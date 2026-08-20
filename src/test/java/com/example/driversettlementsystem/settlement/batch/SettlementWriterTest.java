package com.example.driversettlementsystem.settlement.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.driversettlementsystem.TestcontainersConfiguration;
import com.example.driversettlementsystem.auth.AuthDataSourceTestConfiguration;
import com.example.driversettlementsystem.settlement.client.DriverUnpaid;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import com.example.driversettlementsystem.settlement.domain.PayoutStatus;
import com.example.driversettlementsystem.settlement.domain.Settlement;
import com.example.driversettlementsystem.settlement.repository.SettlementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
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
 * <b>배치가 실제로 정산을 남기는지 확인한다.</b> 여기까지 오면 Reader·Processor·Writer가
 * 전부 실물이라, Job 하나를 돌리면 {@code SETTLEMENTS}에 행이 쌓인다.
 * <p>
 * 저장 자체보다 중요한 것이 두 가지다 — <b>같은 기사가 두 번 오면 거부되는가</b>와
 * <b>실패한 청크만 롤백되는가</b>. 전자가 깨지면 정산 한 건이 조용히 사라지고, 후자가
 * 깨지면 청크 커밋을 쓴 이유가 없어진다.
 */
@Import({TestcontainersConfiguration.class, AuthDataSourceTestConfiguration.class})
@SpringBootTest(properties = {
        "settlement.client.payment.base-url=http://payment.test",
        "settlement.client.ledger.base-url=http://ledger.test"})
class SettlementWriterTest
{

    private static final LocalDate SAVE_DATE = LocalDate.of(2026, 10, 1);

    private static final LocalDate RELOAD_DATE = LocalDate.of(2026, 10, 3);

    private static final LocalDate ROLLBACK_DATE = LocalDate.of(2026, 10, 2);

    /** 청크 크기와 같다. 첫 청크가 정확히 이 수만큼 커밋되는지 보는 데 쓴다. */
    private static final int CHUNK_SIZE = DailySettlementJobConfig.CHUNK_SIZE;

    @MockitoBean
    private LedgerClient ledgerClient;

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job dailySettlementJob;

    @Autowired
    private SettlementRepository settlementRepository;

    @BeforeEach
    void setUp()
    {
        when(ledgerClient.findUnpaidDrivers(any())).thenReturn(List.of());
    }

    @DisplayName("배치가 돌면 기사별 정산 항목이 SETTLEMENTS에 저장된다")
    @Test
    void savesSettlementsForEveryDriver() throws Exception
    {
        when(ledgerClient.findUnpaidDrivers(SAVE_DATE)).thenReturn(List.of(
                driver(1L, "20000"),
                driver(2L, "3333")));

        JobExecution execution = jobOperator.start(dailySettlementJob, parametersFor(SAVE_DATE));
        long batchId = batchIdOf(execution);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<Settlement> saved = settlementRepository.findByBatchId(batchId);
        assertThat(saved).hasSize(2);

        Settlement first = settlementRepository.findByBatchIdAndDriverId(batchId, 1L).orElseThrow();
        assertThat(first.getFareTotal()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(first.getFeeAmount()).isEqualByComparingTo(new BigDecimal("4000"));
        assertThat(first.getAmount()).isEqualByComparingTo(new BigDecimal("16000"));
        assertThat(first.getPayoutStatus()).isEqualTo(PayoutStatus.CONFIRMED);
        assertThat(first.getLedgerId()).isNull();

        Settlement second = settlementRepository.findByBatchIdAndDriverId(batchId, 2L).orElseThrow();
        assertThat(second.getFeeAmount()).isEqualByComparingTo(new BigDecimal("666"));
        assertThat(second.getAmount()).isEqualByComparingTo(new BigDecimal("2667"));
    }

    @DisplayName("저장 후 다시 읽어도 fareTotal - feeAmount == amount 가 성립한다")
    @Test
    void keepsAmountConsistentAfterReload() throws Exception
    {
        when(ledgerClient.findUnpaidDrivers(RELOAD_DATE)).thenReturn(List.of(
                driver(1L, "9999"),
                driver(2L, "1"),
                driver(3L, "123456789")));

        long batchId = batchIdOf(jobOperator.start(dailySettlementJob, parametersFor(RELOAD_DATE)));

        assertThat(settlementRepository.findByBatchId(batchId))
                .hasSize(3)
                .allSatisfy(settlement ->
                assertThat(settlement.getFareTotal().subtract(settlement.getFeeAmount()))
                        .isEqualByComparingTo(settlement.getAmount()));
    }

    /**
     * <b>이 테스트 하나에 청크 커밋을 쓴 이유 전체가 들어 있다.</b>
     * <p>
     * 기사 {@code CHUNK_SIZE + 50}명을 주고 두 번째 청크 안에 같은 기사를 두 번 넣는다.
     * 첫 청크는 이미 커밋됐으므로 남고, 복합 PK를 위반한 두 번째 청크만 롤백된다.
     * <p>
     * Job 전체를 트랜잭션 하나로 묶었다면 <b>{@code CHUNK_SIZE}명분이 전부 날아갔을 것이다.</b>
     * 그리고 {@code save()}로 저장했다면 중복이 merge로 흡수돼 <b>실패조차 나지 않고</b>
     * 정산 한 건이 조용히 덮어써졌을 것이다.
     */
    @DisplayName("중복 기사가 든 청크만 롤백되고 앞 청크는 남는다")
    @Test
    void rollsBackOnlyTheFailedChunk() throws Exception
    {
        List<DriverUnpaid> drivers = new ArrayList<>();

        for (long driverId = 1; driverId <= CHUNK_SIZE + 50; driverId++)
        {
            drivers.add(driver(driverId, "10000"));
        }

        // 두 번째 청크 안에서 같은 기사가 두 번 나온다 — 원장이 중복을 줬을 때의 상황이다.
        // 인덱스 CHUNK_SIZE 가 이미 기사 CHUNK_SIZE + 1 이므로, 그 다음 자리를 같은 기사로 덮는다.
        drivers.set(CHUNK_SIZE + 1, driver(CHUNK_SIZE + 1L, "99999"));

        when(ledgerClient.findUnpaidDrivers(ROLLBACK_DATE)).thenReturn(drivers);

        JobExecution execution = jobOperator.start(dailySettlementJob, parametersFor(ROLLBACK_DATE));
        long batchId = batchIdOf(execution);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(settlementRepository.findByBatchId(batchId)).hasSize(CHUNK_SIZE);
    }

    private static DriverUnpaid driver(Long driverId, String unpaidAmount)
    {
        return new DriverUnpaid(driverId, new BigDecimal(unpaidAmount),
                Instant.parse("2026-10-01T14:30:00Z"));
    }

    private static long batchIdOf(JobExecution execution)
    {
        return execution.getExecutionContext().getLong(SettlementJobListener.BATCH_ID_KEY);
    }

    private static JobParameters parametersFor(LocalDate targetDate)
    {
        return new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters();
    }

}
