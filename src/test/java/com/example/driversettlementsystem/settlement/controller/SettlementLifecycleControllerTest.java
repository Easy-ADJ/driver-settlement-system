package com.example.driversettlementsystem.settlement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.driversettlementsystem.TestcontainersConfiguration;
import com.example.driversettlementsystem.auth.AuthDataSourceTestConfiguration;
import com.example.driversettlementsystem.exception.ExternalServiceException;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.PayoutStatus;
import com.example.driversettlementsystem.settlement.domain.Settlement;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import com.example.driversettlementsystem.settlement.repository.SettlementBatchRepository;
import com.example.driversettlementsystem.settlement.repository.SettlementRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/**
 * 확정·지급 전이를 실제 DB 위에서 확인한다.
 * <p>
 * <b>슬라이스로는 부족하다.</b> 이 이슈에서 제일 위험한 것이 "원장 기록이 실패했는데 배치가
 * 확정된 채로 남는 것"인데, 그건 <b>트랜잭션이 실제로 롤백되는지</b>를 봐야 알 수 있다.
 * 서비스를 목으로 바꾸면 그 동작 자체가 사라진다.
 * <p>
 * 테스트마다 날짜를 따로 잡는다 — 같은 날짜를 공유하면 부분 UNIQUE 인덱스
 * {@code uq_batches_confirmed_date}에 걸려 실행 순서에 따라 결과가 달라진다.
 */
@Import({TestcontainersConfiguration.class, AuthDataSourceTestConfiguration.class})
@SpringBootTest(properties = {
        "settlement.client.payment.base-url=http://payment.test",
        "settlement.client.ledger.base-url=http://ledger.test"})
class SettlementLifecycleControllerTest
{

    private static final LocalDate CONFIRM_DATE = LocalDate.of(2027, 3, 1);

    private static final LocalDate PAY_DATE = LocalDate.of(2027, 3, 2);

    private static final LocalDate LEDGER_FAILURE_DATE = LocalDate.of(2027, 3, 3);

    private static final LocalDate ALREADY_CONFIRMED_DATE = LocalDate.of(2027, 3, 4);

    private static final LocalDate RUNNING_DATE = LocalDate.of(2027, 3, 5);

    private static final LocalDate DOUBLE_PAY_DATE = LocalDate.of(2027, 3, 6);

    @MockitoBean
    private LedgerClient ledgerClient;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private SettlementBatchRepository batchRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        when(ledgerClient.recordPayoutEntry(any(), any(), any())).thenReturn(991L);
    }

    /**
     * <b>상쇄 분개가 이 전이의 핵심이다.</b> 확정만 하고 원장에 남기지 않으면 미지급금이 줄지
     * 않아 다음날 배치가 같은 금액을 또 정산한다.
     * <p>
     * 상쇄 금액은 지급액(33,600)이 아니라 <b>운임 합계(42,000)</b>여야 한다 — 수수료가
     * 미지급금으로 남으면 이 기사는 영원히 정산 대상에서 빠지지 않는다.
     */
    @DisplayName("확정하면 운임 합계로 상쇄 분개를 남기고 원장 ID를 항목에 붙인다")
    @Test
    void confirmRecordsPayoutEntryWithFareTotal() throws Exception
    {
        Long batchId = givenRunningBatch(CONFIRM_DATE, 1L, "42000", "8400");

        mockMvc.perform(post("/api/settlements/{batchId}/confirm", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetDate").value("2027-03-01"))
                .andExpect(jsonPath("$.batchStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.settlements[0].fareTotal").value("42000.00"))
                .andExpect(jsonPath("$.settlements[0].payoutAmount").value("33600.00"));

        verify(ledgerClient).recordPayoutEntry(eq(batchId), eq(1L), eq(new BigDecimal("42000.00")));

        assertThat(batchRepository.findById(batchId).orElseThrow().getStatus())
                .isEqualTo(BatchStatus.CONFIRMED);
        assertThat(settlementRepository.findByBatchIdAndDriverId(batchId, 1L).orElseThrow().getLedgerId())
                .isEqualTo(991L);
    }

    /**
     * <b>이 테스트가 이 이슈에서 가장 중요하다.</b> 원장 기록이 실패했는데 배치가 확정되면
     * 미지급금이 남은 채로 확정된 배치가 생기고, <b>다음날 이중 정산이 쌓인다.</b>
     */
    @DisplayName("원장 기록이 실패하면 확정되지 않고 원장 ID도 남지 않는다")
    @Test
    void doesNotConfirmWhenLedgerFails() throws Exception
    {
        Long batchId = givenRunningBatch(LEDGER_FAILURE_DATE, 2L, "42000", "8400");

        when(ledgerClient.recordPayoutEntry(any(), any(), any()))
                .thenThrow(ExternalServiceException.ledger("원장이 응답하지 않는다", null));

        mockMvc.perform(post("/api/settlements/{batchId}/confirm", batchId))
                .andExpect(status().is5xxServerError());

        assertThat(batchRepository.findById(batchId).orElseThrow().getStatus())
                .isEqualTo(BatchStatus.RUNNING);
        assertThat(settlementRepository.findByBatchIdAndDriverId(batchId, 2L).orElseThrow().getLedgerId())
                .isNull();
    }

    @DisplayName("이미 확정된 배치를 다시 확정하면 409 / INVALID_STATE_TRANSITION")
    @Test
    void rejectsConfirmingTwice() throws Exception
    {
        Long batchId = givenRunningBatch(ALREADY_CONFIRMED_DATE, 3L, "10000", "2000");

        mockMvc.perform(post("/api/settlements/{batchId}/confirm", batchId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/settlements/{batchId}/confirm", batchId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @DisplayName("확정 전 배치에 지급을 부르면 409로 거부된다")
    @Test
    void rejectsPayingBeforeConfirm() throws Exception
    {
        Long batchId = givenRunningBatch(RUNNING_DATE, 4L, "10000", "2000");

        mockMvc.perform(post("/api/settlements/{batchId}/pay", batchId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        verify(ledgerClient, never()).recordPayoutEntry(any(), any(), any());
        assertThat(batchRepository.findById(batchId).orElseThrow().getStatus())
                .isEqualTo(BatchStatus.RUNNING);
    }

    /**
     * 배치만 {@code PAID}로 바꾸고 항목을 두면, 조회 응답에서 <b>배치는 지급 완료인데 기사별
     * 항목은 확정 상태</b>로 보인다. 보는 사람이 어느 쪽을 믿어야 할지 알 수 없다.
     */
    @DisplayName("지급 표시는 배치와 기사별 항목을 함께 PAID로 바꾼다")
    @Test
    void payMarksBatchAndItems() throws Exception
    {
        Long batchId = givenRunningBatch(PAY_DATE, 5L, "20000", "4000");

        mockMvc.perform(post("/api/settlements/{batchId}/confirm", batchId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/settlements/{batchId}/pay", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchStatus").value("PAID"))
                .andExpect(jsonPath("$.settlements[0].payoutStatus").value("PAID"));

        assertThat(settlementRepository.findByBatchIdAndDriverId(batchId, 5L).orElseThrow().getPayoutStatus())
                .isEqualTo(PayoutStatus.PAID);
    }

    @DisplayName("이미 지급된 배치를 다시 부르면 409 — 두 번 눌렀다는 사실이 호출자에게 간다")
    @Test
    void rejectsPayingTwice() throws Exception
    {
        Long batchId = givenRunningBatch(DOUBLE_PAY_DATE, 6L, "20000", "4000");

        mockMvc.perform(post("/api/settlements/{batchId}/confirm", batchId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/settlements/{batchId}/pay", batchId)).andExpect(status().isOk());

        mockMvc.perform(post("/api/settlements/{batchId}/pay", batchId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @DisplayName("없는 배치는 404 / SETTLEMENT_NOT_FOUND")
    @Test
    void rejectsUnknownBatch() throws Exception
    {
        mockMvc.perform(post("/api/settlements/{batchId}/confirm", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));

        mockMvc.perform(post("/api/settlements/{batchId}/pay", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    /**
     * {@code RUNNING} 배치와 기사 1명분 정산 항목을 만든다.
     * <p>
     * {@code Settlement}는 배정된 복합키를 써서 {@code save()}가 merge로 돌아간다. 배치
     * Writer가 {@code persist()}를 쓰는 것과 같은 이유로 여기서도 직접 넣는다.
     *
     * @param targetDate 정산 대상 일자
     * @param driverId   기사 ID
     * @param fareTotal  운임 합계
     * @param feeAmount  수수료
     * @return 만들어진 배치 ID
     */
    private Long givenRunningBatch(LocalDate targetDate, Long driverId, String fareTotal, String feeAmount)
    {
        return transactionTemplate.execute(status ->
        {
            SettlementBatch batch = batchRepository.saveAndFlush(SettlementBatch.start(targetDate));

            entityManager.persist(Settlement.of(batch.getBatchId(), driverId,
                    new BigDecimal(fareTotal), new BigDecimal(feeAmount)));

            return batch.getBatchId();
        });
    }

}
