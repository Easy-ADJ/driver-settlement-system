package com.example.driversettlementsystem.settlement.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.driversettlementsystem.TestcontainersConfiguration;
import com.example.driversettlementsystem.auth.AuthDataSourceTestConfiguration;
import com.example.driversettlementsystem.exception.DuplicateSettlementException;
import com.example.driversettlementsystem.exception.ExternalServiceException;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import com.example.driversettlementsystem.settlement.repository.SettlementBatchRepository;
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

    private static final LocalDate PENDING_DATE = LocalDate.of(2026, 9, 4);

    private static final LocalDate DUPLICATE_DATE = LocalDate.of(2026, 9, 2);

    private static final LocalDate FAILURE_DATE = LocalDate.of(2026, 9, 3);

    @MockitoBean
    private LedgerClient ledgerClient;

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
     * <b>Job이 성공해도 확정하지 않는다.</b> 확정은 대사가 {@code MATCHED}를 낼 때만 하기로
     * 정해져 있고(#6), 대사는 아직 없다. 검증되지 않은 금액을 확정으로 올리면 그대로
     * 지급 단계로 넘어간다.
     */
    @DisplayName("Job이 성공해도 대사 전이므로 RUNNING에 머문다")
    @Test
    void staysRunningUntilReconciled() throws Exception
    {
        JobExecution execution = jobOperator.start(dailySettlementJob, parametersFor(PENDING_DATE));

        long batchId = execution.getExecutionContext().getLong(SettlementJobListener.BATCH_ID_KEY);

        assertThat(batchRepository.findById(batchId).orElseThrow().getStatus())
                .isEqualTo(BatchStatus.RUNNING);
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

    private static JobParameters parametersFor(LocalDate targetDate)
    {
        return new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters();
    }

}
