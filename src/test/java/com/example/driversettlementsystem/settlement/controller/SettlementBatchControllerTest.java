package com.example.driversettlementsystem.settlement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.driversettlementsystem.TestcontainersConfiguration;
import com.example.driversettlementsystem.auth.AuthDataSourceTestConfiguration;
import com.example.driversettlementsystem.settlement.client.DriverUnpaid;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import com.example.driversettlementsystem.settlement.repository.SettlementBatchRepository;
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
 * 배치를 HTTP로 돌리는 경로 전체를 실제 DB 위에서 확인한다.
 * <p>
 * <b>슬라이스로는 확인할 수 없는 것이 이 이슈의 핵심이다.</b> Spring Batch가 리스너 예외를
 * 삼키기 때문에, 중복 실행이 409로 나가는지는 Job을 진짜로 돌려봐야 알 수 있다. 서비스를
 * 목으로 바꾸면 그 동작 자체가 사라진다.
 */
@Import({TestcontainersConfiguration.class, AuthDataSourceTestConfiguration.class})
@SpringBootTest(properties = {
        "settlement.client.payment.base-url=http://payment.test",
        "settlement.client.ledger.base-url=http://ledger.test"})
class SettlementBatchControllerTest
{

    private static final LocalDate RUN_DATE = LocalDate.of(2027, 1, 5);

    private static final LocalDate CONFIRMED_DATE = LocalDate.of(2027, 1, 6);

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
    }

    @DisplayName("자정을 기다리지 않고 배치가 돌고 결과가 바로 나온다")
    @Test
    void runsBatchOnDemand() throws Exception
    {
        when(ledgerClient.findUnpaidDrivers(RUN_DATE)).thenReturn(List.of(
                driver(1L, "20000"),
                driver(2L, "3333")));

        mockMvc.perform(post("/api/settlements/batch").param("targetDate", "2027-01-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").isNumber())
                .andExpect(jsonPath("$.targetDate").value("2027-01-05"))
                .andExpect(jsonPath("$.settlementCount").value(2))
                .andExpect(jsonPath("$.batchStatus").value("RUNNING"));
    }

    /**
     * <b>이 테스트가 이 이슈에서 가장 중요하다.</b> Spring Batch는 {@code beforeJob}에서 던진
     * 예외를 삼키고 Job을 {@code FAILED}로 기록할 뿐이다. 실행 결과에서 꺼내 다시 던지지
     * 않으면 <b>중복 실행이 200 OK로 응답한다</b> — 거부됐는데 성공처럼 보인다.
     */
    @DisplayName("이미 확정된 날짜는 409로 거부되고 새 배치가 생기지 않는다")
    @Test
    void rejectsAlreadyConfirmedDate() throws Exception
    {
        SettlementBatch confirmed = SettlementBatch.start(CONFIRMED_DATE);
        confirmed.transitionTo(BatchStatus.CONFIRMED);
        batchRepository.saveAndFlush(confirmed);

        long countBefore = batchRepository.count();

        mockMvc.perform(post("/api/settlements/batch").param("targetDate", "2027-01-06"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ALREADY_CONFIRMED"));

        assertThat(batchRepository.count()).isEqualTo(countBefore);
    }

    /**
     * 같은 날짜로 두 번 부르면 Spring Batch가 {@code JobInstanceAlreadyCompleteException}을
     * 던진다. 호출자에게는 "이미 확정됨"과 다른 사건이 아니라 <b>둘 다 "그날은 이미 돌았다"</b>다.
     * <p>
     * 이 테스트만 쓰는 날짜를 따로 잡는다 — 다른 테스트와 공유하면 실행 순서에 따라 첫 번째
     * 요청이 이미 409를 받는다.
     */
    @DisplayName("같은 날짜로 두 번 부르면 409로 막힌다")
    @Test
    void rejectsSecondRunOfSameDate() throws Exception
    {
        mockMvc.perform(post("/api/settlements/batch").param("targetDate", "2027-01-07"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/settlements/batch").param("targetDate", "2027-01-07"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ALREADY_CONFIRMED"));
    }

    @DisplayName("targetDate를 빼면 400 / MISSING_REQUIRED_PARAMETER")
    @Test
    void rejectsMissingTargetDate() throws Exception
    {
        mockMvc.perform(post("/api/settlements/batch"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_PARAMETER"));
    }

    private static DriverUnpaid driver(Long driverId, String unpaidAmount)
    {
        return new DriverUnpaid(driverId, new BigDecimal(unpaidAmount),
                Instant.parse("2027-01-05T14:30:00Z"));
    }

}
