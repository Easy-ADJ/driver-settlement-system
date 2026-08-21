package com.example.driversettlementsystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.driversettlementsystem.auth.AuthDataSourceTestConfiguration;
import com.example.driversettlementsystem.exception.ExternalServiceException;
import com.example.driversettlementsystem.settlement.client.DriverLedger;
import com.example.driversettlementsystem.settlement.client.DriverUnpaid;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import com.example.driversettlementsystem.settlement.client.PaymentDetail;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.repository.SettlementBatchRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 정산 배치 전 구간 통합 테스트 — <b>실행 → 저장 → 확정 → 지급 → 조회</b> 한 바퀴.
 * <p>
 * 클래스별 단위 테스트가 전부 통과해도 <b>"붙였을 때 동작한다"는 뜻이 아니다.</b> 엔티티와
 * 실제 테이블의 불일치, {@code @StepScope} 파라미터 주입, 청크 트랜잭션 경계는 실제
 * PostgreSQL 위에서 Job을 돌려야만 드러난다.
 * <p>
 * <b>원장은 스텁으로 끊는다.</b> 실제 원장 서버를 띄워 테스트하면 남이 자기 서버를 고치는
 * 순간 우리 테스트가 빨개진다. 서버 간 실제 연동은 별도 통합 테스트에서 확인한다.
 * <p>
 * ⚠️ <b>결제 서버는 여기 등장하지 않는다.</b> 배치 입력은 원장 미지급 목록이고, 결제는
 * 대사(아직 미구현)에서만 쓰인다 — 현재 {@code PaymentClient}를 부르는 운영 코드가 없다.
 */
@Import({TestcontainersConfiguration.class, AuthDataSourceTestConfiguration.class})
@SpringBootTest(properties = {
        "settlement.client.payment.base-url=http://payment.test",
        "settlement.client.ledger.base-url=http://ledger.test"})
class SettlementBatchE2ETest
{

    private static final LocalDate FULL_LOOP_DATE = LocalDate.of(2027, 6, 1);

    private static final LocalDate DUPLICATE_DATE = LocalDate.of(2027, 6, 2);

    private static final LocalDate LEDGER_DOWN_DATE = LocalDate.of(2027, 6, 3);

    private static final LocalDate RETRY_DATE = LocalDate.of(2027, 6, 4);

    private static final LocalDate EMPTY_PAYMENTS_DATE = LocalDate.of(2027, 6, 5);

    @MockitoBean
    private LedgerClient ledgerClient;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private SettlementBatchRepository batchRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        when(ledgerClient.findUnpaidDrivers(any())).thenReturn(List.of());
        when(ledgerClient.recordPayoutEntry(any(), any(), any())).thenReturn(991L);
    }

    /**
     * <b>이 테스트가 곧 시연 대본이다.</b> 배치를 돌리고, 금액을 확인하고, 확정하면서 원장에
     * 상쇄를 남기고, 지급으로 표시하고, 근거까지 조회한다.
     * <p>
     * 세 가지를 한 번에 확인한다.
     * <ul>
     *   <li><b>미지급금이 0인 기사는 정산 항목이 생기지 않는다</b> — 기사 3명 중 2건</li>
     *   <li><b>수수료는 버림이다</b> — 3,333 × 20% = 666.6 → 666, 지급 2,667</li>
     *   <li><b>상쇄 금액은 지급액이 아니라 운임 합계다</b> — 20,000과 3,333</li>
     * </ul>
     */
    @DisplayName("배치 실행부터 지급 표시까지 한 바퀴가 돈다")
    @Test
    void runsFullLoopFromBatchToPayout() throws Exception
    {
        when(ledgerClient.findUnpaidDrivers(FULL_LOOP_DATE)).thenReturn(List.of(
                unpaid(1L, "20000"),
                unpaid(2L, "3333"),
                unpaid(3L, "0")));

        when(ledgerClient.findDriverLedger(2L)).thenReturn(new DriverLedger(
                2L, new BigDecimal("3333"),
                List.of(new PaymentDetail(700L, new BigDecimal("3333"),
                        Instant.parse("2027-06-01T09:00:00Z")))));

        // 1. 배치 실행 — 미지급금이 0인 기사 3번은 빠진다
        String runBody = mockMvc.perform(post("/api/settlements/batch")
                        .param("targetDate", "2027-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementCount").value(2))
                .andExpect(jsonPath("$.batchStatus").value("RUNNING"))
                .andReturn().getResponse().getContentAsString();

        long batchId = ((Number) JsonPath.read(runBody, "$.batchId")).longValue();

        // 2. 조회 — 저장된 금액이 계산 규칙대로인지
        mockMvc.perform(get("/api/settlements").param("date", "2027-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchStatus").value("RUNNING"))
                .andExpect(jsonPath("$.settlements.length()").value(2))
                .andExpect(jsonPath("$.settlements[?(@.driverId == 1)].payoutAmount").value("16000.00"))
                .andExpect(jsonPath("$.settlements[?(@.driverId == 2)].feeAmount").value("666.00"))
                .andExpect(jsonPath("$.settlements[?(@.driverId == 2)].payoutAmount").value("2667.00"));

        // 3. 확정 — 원장에 운임 합계로 상쇄 분개를 남긴다
        mockMvc.perform(post("/api/settlements/{batchId}/confirm", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchStatus").value("CONFIRMED"));

        verify(ledgerClient).recordPayoutEntry(eq(batchId), eq(1L), eq(new BigDecimal("20000.00")));
        verify(ledgerClient).recordPayoutEntry(eq(batchId), eq(2L), eq(new BigDecimal("3333.00")));

        // 4. 지급 표시 — 배치와 기사별 항목이 함께 넘어간다
        mockMvc.perform(post("/api/settlements/{batchId}/pay", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchStatus").value("PAID"))
                .andExpect(jsonPath("$.settlements[0].payoutStatus").value("PAID"));

        // 5. 기사 지정 조회 — 결제 건별 근거가 채워진다
        mockMvc.perform(get("/api/settlements")
                        .param("date", "2027-06-01")
                        .param("driverId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlements[0].payoutStatus").value("PAID"))
                .andExpect(jsonPath("$.settlements[0].payments.length()").value(1))
                .andExpect(jsonPath("$.settlements[0].payments[0].paymentId").value(700))
                .andExpect(jsonPath("$.settlements[0].payments[0].amount").value("3333"));
    }

    /**
     * 전체 조회는 원장을 부르지 않는다. 채우려면 기사마다 원장을 한 번씩 불러야 하고,
     * 기사 100명이면 조회 한 번에 원장 호출 100번이 된다.
     */
    @DisplayName("전체 조회의 payments는 비어 있다 — 생략되지 않고 빈 배열이다")
    @Test
    void fullListLeavesPaymentsEmptyWithoutOmittingIt() throws Exception
    {
        when(ledgerClient.findUnpaidDrivers(EMPTY_PAYMENTS_DATE)).thenReturn(List.of(unpaid(6L, "10000")));

        mockMvc.perform(post("/api/settlements/batch").param("targetDate", "2027-06-05"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/settlements").param("date", "2027-06-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlements[0].payments").isArray())
                .andExpect(jsonPath("$.settlements[0].payments.length()").value(0));
    }

    /**
     * {@code FR-B-06}. 거부만으로는 부족하다 — <b>거부됐는데 배치 레코드가 늘어나면</b>
     * 조회 API가 그 빈 배치를 최신으로 잡아 "정산 0건"으로 보여준다.
     */
    @DisplayName("같은 날짜 재실행은 거부되고 새 배치 레코드가 생기지 않는다")
    @Test
    void rejectsSecondRunWithoutCreatingBatch() throws Exception
    {
        when(ledgerClient.findUnpaidDrivers(DUPLICATE_DATE)).thenReturn(List.of(unpaid(4L, "10000")));

        mockMvc.perform(post("/api/settlements/batch").param("targetDate", "2027-06-02"))
                .andExpect(status().isOk());

        long countAfterFirstRun = batchRepository.count();

        mockMvc.perform(post("/api/settlements/batch").param("targetDate", "2027-06-02"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ALREADY_CONFIRMED"));

        assertThat(batchRepository.count()).isEqualTo(countAfterFirstRun);
    }

    /**
     * 원장이 죽으면 정산할 금액 자체를 못 읽는다. <b>0건으로 성공했다고 답하면 안 된다</b> —
     * 그날 정산이 끝났다고 오해하고 아무도 다시 돌리지 않는다.
     */
    @DisplayName("원장이 죽으면 배치가 FAILED로 남고 호출자에게 실패가 전달된다")
    @Test
    void marksBatchFailedWhenLedgerIsDown() throws Exception
    {
        when(ledgerClient.findUnpaidDrivers(LEDGER_DOWN_DATE))
                .thenThrow(ExternalServiceException.ledger("원장이 응답하지 않는다", null));

        mockMvc.perform(post("/api/settlements/batch").param("targetDate", "2027-06-03"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("LEDGER_SERVICE_UNAVAILABLE"));

        assertThat(latestBatchStatus(LEDGER_DOWN_DATE)).isEqualTo(BatchStatus.FAILED);
    }

    /**
     * {@code FR-B-07}이 실무에서 뜻하는 것은 <b>"실패한 날을 다시 돌릴 수 있다"</b>이다.
     * 중복 거부 규칙이 실패한 배치까지 막아버리면 그날은 영영 정산되지 않는다.
     * <p>
     * ⚠️ <b>청크 단위 재개(이미 처리한 청크를 건너뛰는 것)는 여기서 확인하지 않았다.</b>
     * 중간 청크에서만 실패시키는 장치가 필요한데, 그 장치를 위해 운영 코드에 구멍을 내는
     * 것은 얻는 것보다 잃는 것이 크다.
     */
    @DisplayName("실패한 날짜는 같은 날짜로 다시 돌릴 수 있다")
    @Test
    void allowsRerunAfterFailure() throws Exception
    {
        when(ledgerClient.findUnpaidDrivers(RETRY_DATE))
                .thenThrow(ExternalServiceException.ledger("원장이 응답하지 않는다", null));

        mockMvc.perform(post("/api/settlements/batch").param("targetDate", "2027-06-04"))
                .andExpect(status().isInternalServerError());

        // doReturn을 쓴다. when(mock.call())은 스텁을 거는 과정에서 목을 실제로 호출하는데,
        // 지금 이 목은 예외를 던지도록 걸려 있어 스텁을 다시 걸려다 예외가 난다.
        doReturn(List.of(unpaid(5L, "50000"))).when(ledgerClient).findUnpaidDrivers(RETRY_DATE);

        mockMvc.perform(post("/api/settlements/batch").param("targetDate", "2027-06-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementCount").value(1));

        assertThat(latestBatchStatus(RETRY_DATE)).isEqualTo(BatchStatus.RUNNING);

        mockMvc.perform(get("/api/settlements").param("date", "2027-06-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlements[0].payoutAmount").value("40000.00"));
    }

    private BatchStatus latestBatchStatus(LocalDate targetDate)
    {
        return batchRepository.findFirstByTargetDateOrderByExecutedAtDesc(targetDate)
                .orElseThrow()
                .getStatus();
    }

    private static DriverUnpaid unpaid(Long driverId, String amount)
    {
        return new DriverUnpaid(driverId, new BigDecimal(amount),
                Instant.parse("2027-06-01T12:00:00Z"));
    }

}
