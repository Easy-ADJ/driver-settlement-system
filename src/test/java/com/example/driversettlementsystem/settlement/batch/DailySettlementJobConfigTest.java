package com.example.driversettlementsystem.settlement.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.driversettlementsystem.TestcontainersConfiguration;
import com.example.driversettlementsystem.auth.AuthDataSourceTestConfiguration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.batch.autoconfigure.JobLauncherApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * Job 골격이 실제로 뜨고 도는지 확인한다.
 * <p>
 * <b>비즈니스 로직은 아직 없다.</b> Reader가 no-op이라 처리 건수는 0이며, 여기서 보는
 * 것은 "Job이 조립됐고, 파라미터를 요구하고, 빈 입력으로도 정상 종료한다"까지다.
 * 무엇을 읽고 계산하는지는 Reader·Processor·Writer가 들어올 때 각자 검증한다.
 */
@Import({TestcontainersConfiguration.class, AuthDataSourceTestConfiguration.class})
@SpringBootTest(properties = {
        "settlement.client.payment.base-url=http://payment.test",
        "settlement.client.ledger.base-url=http://ledger.test"})
class DailySettlementJobConfigTest
{

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 19);

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job dailySettlementJob;

    @Autowired
    private ApplicationContext applicationContext;

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

    @DisplayName("targetDate를 주고 실행하면 입력이 비어 있어도 성공으로 끝난다")
    @Test
    void completesWithEmptyInput() throws Exception
    {
        JobParameters parameters = new JobParametersBuilder()
                .addLocalDate("targetDate", TARGET_DATE)
                .toJobParameters();

        JobExecution execution = jobOperator.start(dailySettlementJob, parameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getJobParameters().getLocalDate("targetDate")).isEqualTo(TARGET_DATE);
        assertThat(execution.getStepExecutions()).hasSize(1);
        assertThat(execution.getStepExecutions().iterator().next().getStepName())
                .isEqualTo("settlementStep");
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

}
