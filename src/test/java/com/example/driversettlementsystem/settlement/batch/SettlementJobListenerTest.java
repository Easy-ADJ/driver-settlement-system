package com.example.driversettlementsystem.settlement.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.driversettlementsystem.TestcontainersConfiguration;
import com.example.driversettlementsystem.auth.AuthDataSourceTestConfiguration;
import com.example.driversettlementsystem.exception.DuplicateSettlementException;
import com.example.driversettlementsystem.exception.ExternalServiceException;
import com.example.driversettlementsystem.settlement.client.DriverUnpaid;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import com.example.driversettlementsystem.settlement.client.PaymentClient;
import com.example.driversettlementsystem.settlement.client.PaymentSummary;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.ReconciliationStatus;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import com.example.driversettlementsystem.settlement.repository.SettlementBatchRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 배치 레코드가 언제 생기고 언제 안 생기는지 확인한다.
 * <p>
 * <b>{@code FR-B-06}이 여기서 처음으로 실제 충족된다.</b> {@code DuplicateBatchGuard}는
 * #18에서 만들었지만 부르는 곳이 없어 "만들어만 둔 코드"였다. 리스너가 배선하면서
 * "이미 확정된 날짜면 새 레코드가 생성되지 않는다"가 Job 실행 경로에서 성립한다.
 * <p>
 * 날짜를 테스트마다 다르게 쓴다 — Spring Batch는 <b>파라미터가 같은 Job 인스턴스를 두 번
 * 실행하지 않기 때문</b>에, 같은 날짜를 쓰면 뒤 테스트가 실행 자체를 거부당한다.
 */
@Import({TestcontainersConfiguration.class, AuthDataSourceTestConfiguration.class})
@SpringBootTest(properties = {
        "settlement.client.payment.base-url=http://payment.test",
        "settlement.client.ledger.base-url=http://ledger.test"})
class SettlementJobListenerTest
{

    private static final LocalDate SUCCESS_DATE = LocalDate.of(2026, 9, 1);

    private static final LocalDate MATCHED_DATE = LocalDate.of(2026, 9, 4);

    private static final LocalDate MISMATCHED_DATE = LocalDate.of(2026, 9, 5);

    private static final LocalDate SKIPPED_DATE = LocalDate.of(2026, 9, 6);

    private static final LocalDate DUPLICATE_DATE = LocalDate.of(2026, 9, 2);

    private static final LocalDate FAILURE_DATE = LocalDate.of(2026, 9, 3);

    @MockitoBean
    private LedgerClient ledgerClient;

    @MockitoBean
    private PaymentClient paymentClient;

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job dailySettlementJob;

    @Autowired
    private SettlementBatchRepository batchRepository;

    @BeforeEach
    void setUp()
    {
        when(ledgerClient.findUnpaidDrivers(any())).thenReturn(List.of());
        when(ledgerClient.recordPayoutEntry(any(), any(), any())).thenReturn(991L);
        when(paymentClient.findPaymentsByDate(any())).thenReturn(List.of());
    }

    @DisplayName("Job이 시작되면 BATCHES 1건이 RUNNING으로 생기고 batchId가 공유된다")
    @Test
    void createsRunningBatchOnStart() throws Exception
    {
        JobExecution execution = jobOperator.start(dailySettlementJob, parametersFor(SUCCESS_DATE));

        assertThat(execution.getExecutionContext().containsKey(SettlementJobListener.BATCH_ID_KEY))
                .isTrue();

        long batchId = execution.getExecutionContext().getLong(SettlementJobListener.BATCH_ID_KEY);
        SettlementBatch batch = batchRepository.findById(batchId).orElseThrow();

        assertThat(batch.getTargetDate()).isEqualTo(SUCCESS_DATE);
        assertThat(batch.getExecutedAt()).isNotNull();
    }

    /**
     * <b>대사가 통과했을 때만 확정한다.</b> 그리고 확정은 수동 확정과 <b>같은 경로</b>를
     * 탄다 — 여기서 따로 전이시키면 자동 확정에만 상쇄 분개가 빠지고, 그쪽으로 확정된 날은
     * 다음날 이중 정산이 된다.
     */
    @DisplayName("대사가 MATCHED면 확정되고 상쇄 분개까지 나간다")
    @Test
    void confirmsWhenReconciliationMatches() throws Exception
    {
        when(ledgerClient.findUnpaidDrivers(MATCHED_DATE)).thenReturn(List.of(unpaid(1L, "20000")));
        when(paymentClient.findPaymentsByDate(MATCHED_DATE)).thenReturn(List.of(payment("20000", "APPROVED")));

        SettlementBatch batch = runAndFind(MATCHED_DATE);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.CONFIRMED);
        assertThat(batch.getReconciliationStatus()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(batch.getTotalPayoutAmount()).isEqualByComparingTo(new BigDecimal("16000"));

        verify(ledgerClient).recordPayoutEntry(eq(batch.getBatchId()), eq(1L), eq(new BigDecimal("20000.00")));
    }

    /**
     * <b>틀린 금액이 지급 단계로 넘어가지 않게 막는 것</b>이 이 분기의 존재 이유다.
     * 판정 결과는 남는다 — 사람이 확인한 뒤 {@code /confirm}으로 진행시킬 수 있다.
     */
    @DisplayName("대사가 MISMATCHED면 확정하지 않고 판정만 남는다")
    @Test
    void staysRunningWhenReconciliationMismatches() throws Exception
    {
        when(ledgerClient.findUnpaidDrivers(MISMATCHED_DATE)).thenReturn(List.of(unpaid(2L, "20000")));
        when(paymentClient.findPaymentsByDate(MISMATCHED_DATE)).thenReturn(List.of(payment("99999", "APPROVED")));

        SettlementBatch batch = runAndFind(MISMATCHED_DATE);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.RUNNING);
        assertThat(batch.getReconciliationStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
    }

    /**
     * {@code SKIPPED}는 "틀렸다"가 아니라 "확인 못 했다"지만 <b>확정을 막는 것은 같다.</b>
     * 확인하지 못한 금액을 지급 단계로 넘기지 않는다.
     * <p>
     * 그러면서도 <b>배치 자체는 죽지 않는다</b> — 대사는 검증이지 집계가 아니다.
     */
    @DisplayName("대사가 SKIPPED여도 배치는 죽지 않고, 다만 확정되지 않는다")
    @Test
    void staysRunningWhenReconciliationSkipped() throws Exception
    {
        when(ledgerClient.findUnpaidDrivers(SKIPPED_DATE)).thenReturn(List.of(unpaid(3L, "20000")));
        when(paymentClient.findPaymentsByDate(SKIPPED_DATE))
                .thenThrow(ExternalServiceException.payment("결제 서버가 응답하지 않는다", null));

        SettlementBatch batch = runAndFind(SKIPPED_DATE);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.RUNNING);
        assertThat(batch.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SKIPPED);
        assertThat(batch.getTotalPayoutAmount()).isEqualByComparingTo(new BigDecimal("16000"));
    }

    /**
     * {@code FR-B-06}의 수락 기준이 그대로 여기다 — <b>거부는 흔적을 남기지 않아야 한다.</b>
     * 검사가 저장보다 먼저라서 성립한다.
     */
    @DisplayName("이미 확정된 날짜면 거부되고 BATCHES에 새 레코드가 생기지 않는다")
    @Test
    void rejectsAlreadyConfirmedDateWithoutCreatingBatch() throws Exception
    {
        SettlementBatch confirmed = SettlementBatch.start(DUPLICATE_DATE);
        confirmed.transitionTo(BatchStatus.CONFIRMED);
        batchRepository.saveAndFlush(confirmed);

        long countBefore = batchRepository.count();

        JobExecution execution = jobOperator.start(dailySettlementJob, parametersFor(DUPLICATE_DATE));

        assertThat(execution.getAllFailureExceptions())
                .anySatisfy(thrown -> assertThat(thrown).isInstanceOf(DuplicateSettlementException.class));
        assertThat(batchRepository.count()).isEqualTo(countBefore);
    }

    @DisplayName("Job이 실패하면 배치가 FAILED로 전이한다")
    @Test
    void marksBatchFailedWhenJobFails() throws Exception
    {
        when(ledgerClient.findUnpaidDrivers(FAILURE_DATE))
                .thenThrow(ExternalServiceException.ledger("원장이 응답하지 않는다", null));

        JobExecution execution = jobOperator.start(dailySettlementJob, parametersFor(FAILURE_DATE));

        long batchId = execution.getExecutionContext().getLong(SettlementJobListener.BATCH_ID_KEY);

        assertThat(batchRepository.findById(batchId).orElseThrow().getStatus())
                .isEqualTo(BatchStatus.FAILED);
    }

    private SettlementBatch runAndFind(LocalDate targetDate) throws Exception
    {
        JobExecution execution = jobOperator.start(dailySettlementJob, parametersFor(targetDate));
        long batchId = execution.getExecutionContext().getLong(SettlementJobListener.BATCH_ID_KEY);

        return batchRepository.findById(batchId).orElseThrow();
    }

    private static DriverUnpaid unpaid(Long driverId, String amount)
    {
        return new DriverUnpaid(driverId, new BigDecimal(amount), Instant.parse("2026-09-04T12:00:00Z"));
    }

    private static PaymentSummary payment(String amount, String status)
    {
        return new PaymentSummary(100L, 1L, new BigDecimal(amount), status);
    }

    private static JobParameters parametersFor(LocalDate targetDate)
    {
        return new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters();
    }

}
