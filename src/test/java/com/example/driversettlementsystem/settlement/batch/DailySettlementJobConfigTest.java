package com.example.driversettlementsystem.settlement.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.driversettlementsystem.TestcontainersConfiguration;
import com.example.driversettlementsystem.auth.AuthDataSourceTestConfiguration;
import com.example.driversettlementsystem.settlement.client.DriverUnpaid;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.batch.autoconfigure.JobLauncherApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Job이 실제로 조립돼 돌고, 읽은 만큼 청크에 흘러가는지 확인한다.
 * <p>
 * <b>원장은 가짜로 세운다.</b> 이 테스트가 보려는 것은 원장 응답 파싱이 아니라 Job 구조다 —
 * 파싱은 {@code LedgerClientTest}가, Reader의 종료 조건은 {@code UnpaidDriverReaderTest}가
 * 각각 맡는다. 여기서만 확인할 수 있는 것은 <b>{@code @StepScope}로 주입된
 * {@code targetDate}가 실제 Job 파라미터에서 오는가</b>이다.
 * <p>
 * Processor는 아직 no-op이라 쓰기 건수는 0이다. 읽기 건수까지가 지금 의미 있는 지표다.
 */
@Import({TestcontainersConfiguration.class, AuthDataSourceTestConfiguration.class})
@SpringBootTest(properties = {
        "settlement.client.payment.base-url=http://payment.test",
        "settlement.client.ledger.base-url=http://ledger.test"})
class DailySettlementJobConfigTest
{

    private static final LocalDate EMPTY_DATE = LocalDate.of(2026, 8, 19);

    private static final LocalDate BUSY_DATE = LocalDate.of(2026, 8, 20);

    @MockitoBean
    private LedgerClient ledgerClient;

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job dailySettlementJob;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void setUp()
    {
        when(ledgerClient.findUnpaidDrivers(any())).thenReturn(List.of());
    }

    /**
     * 부팅만으로 Job이 돌면 아무도 지시하지 않은 정산이 확정된다.
     * <p>
     * "실행 이력이 없다"로 확인하지 않는다 — 같은 컨텍스트를 쓰는 다른 테스트가 먼저
     * Job을 돌리면 그 이력이 잡혀 <b>테스트 순서에 따라 결과가 달라진다.</b> 대신
     * 자동 실행을 담당하는 빈이 <b>아예 등록되지 않았는지</b>를 본다. 이쪽이
     * {@code spring.batch.job.enabled=false}가 실제로 하는 일이다.
     */
    @DisplayName("앱이 떠도 Job이 저절로 실행되지는 않는다")
    @Test
    void doesNotRunOnStartup()
    {
        assertThat(applicationContext.getBeanNamesForType(JobLauncherApplicationRunner.class))
                .isEmpty();
    }

    @DisplayName("정산할 기사가 없는 날도 성공으로 끝난다")
    @Test
    void completesWithEmptyInput() throws Exception
    {
        JobExecution execution = jobOperator.start(dailySettlementJob, parametersFor(EMPTY_DATE));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getJobParameters().getLocalDate("targetDate")).isEqualTo(EMPTY_DATE);
        assertThat(execution.getStepExecutions()).hasSize(1);
        assertThat(stepOf(execution).getStepName()).isEqualTo("settlementStep");
        assertThat(stepOf(execution).getReadCount()).isZero();
    }

    /**
     * <b>{@code @StepScope} 주입은 여기서만 확인된다.</b> Reader를 직접 생성하는 테스트는
     * 날짜를 손으로 넣으므로, Job 파라미터의 날짜가 실제로 원장 호출까지 흘러가는지는
     * 알 수 없다. 날짜가 안 흘러가면 <b>매번 같은 날을 정산하면서 성공으로 끝난다.</b>
     */
    @DisplayName("읽은 기사 수만큼 청크로 흘러가고, Job 파라미터의 날짜로 원장을 부른다")
    @Test
    void readsEveryUnpaidDriver() throws Exception
    {
        when(ledgerClient.findUnpaidDrivers(BUSY_DATE)).thenReturn(List.of(
                new DriverUnpaid(1L, new BigDecimal("15000"), Instant.parse("2026-08-20T14:30:00Z")),
                new DriverUnpaid(2L, new BigDecimal("32000"), Instant.parse("2026-08-20T23:15:00Z"))));

        JobExecution execution = jobOperator.start(dailySettlementJob, parametersFor(BUSY_DATE));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(stepOf(execution).getReadCount()).isEqualTo(2);
        verify(ledgerClient).findUnpaidDrivers(BUSY_DATE);
    }

    /**
     * 없으면 어느 날짜를 정산하는지 모르는 채로 Job이 돌고, 그게 <b>성공으로 끝난다.</b>
     * 그 침묵이 가장 나쁘다.
     */
    @DisplayName("targetDate 없이 실행하면 Job이 시작되지 않는다")
    @Test
    void rejectsMissingTargetDate()
    {
        assertThatThrownBy(() -> jobOperator.start(dailySettlementJob, new JobParameters()))
                .isInstanceOf(InvalidJobParametersException.class);
    }

    @DisplayName("청크 크기가 상수로 명시돼 있다 — 매직 넘버가 아니다")
    @Test
    void chunkSizeIsNamedConstant()
    {
        assertThat(DailySettlementJobConfig.CHUNK_SIZE).isEqualTo(100);
    }

    private static JobParameters parametersFor(LocalDate targetDate)
    {
        return new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters();
    }

    private static StepExecution stepOf(JobExecution execution)
    {
        return execution.getStepExecutions().iterator().next();
    }

}
