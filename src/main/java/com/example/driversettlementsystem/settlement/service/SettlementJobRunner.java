package com.example.driversettlementsystem.settlement.service;

import com.example.driversettlementsystem.exception.DuplicateSettlementException;
import com.example.driversettlementsystem.exception.SettlementException;
import com.example.driversettlementsystem.settlement.batch.SettlementJobListener;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.dto.BatchRunResponse;
import com.example.driversettlementsystem.settlement.repository.SettlementBatchRepository;
import com.example.driversettlementsystem.settlement.repository.SettlementRepository;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Service;

/**
 * 정산 배치를 실행하는 단 하나의 진입점.
 * <p>
 * <b>수동 실행(API)과 자동 실행(스케줄러)이 같은 메서드를 부른다.</b> 각자 Job을 띄우게 두면
 * 한쪽에만 중복 검사가 빠지는 식으로 규칙이 갈라지고, <b>갈라진 쪽은 시연 당일에야
 * 발견된다.</b>
 * <p>
 * ⚠️ <b>Spring Batch는 리스너가 던진 예외를 삼킨다.</b> {@code beforeJob}의 중복 검사에서
 * 예외가 나도 호출자에게 올라오지 않고 Job이 {@code FAILED}로 기록될 뿐이다. 그대로 두면
 * 중복 실행이 <b>200 OK로 응답한다</b> — 실패했는데 성공처럼 보인다. 그래서 실행 결과에서
 * 실패 예외를 꺼내 다시 던진다.
 */
@Service
public class SettlementJobRunner
{

    private static final Logger log = LoggerFactory.getLogger(SettlementJobRunner.class);

    private final JobOperator jobOperator;

    private final Job dailySettlementJob;

    private final SettlementBatchRepository batchRepository;

    private final SettlementRepository settlementRepository;

    public SettlementJobRunner(JobOperator jobOperator,
                               Job dailySettlementJob,
                               SettlementBatchRepository batchRepository,
                               SettlementRepository settlementRepository)
    {
        this.jobOperator = jobOperator;
        this.dailySettlementJob = dailySettlementJob;
        this.batchRepository = batchRepository;
        this.settlementRepository = settlementRepository;
    }

    /**
     * 지정한 날짜의 정산 배치를 실행한다.
     * <p>
     * 동기로 돈다. 데모 규모에서는 끝날 때까지 기다렸다 결과를 주는 편이 진행률 조회를
     * 따로 만드는 것보다 낫다.
     *
     * @param targetDate 정산 대상 일자
     * @return 배치 ID와 실행 결과
     * @throws DuplicateSettlementException 이미 확정된 날짜이거나, 같은 날짜로 이미 완료된
     *                                      Job 인스턴스가 있을 때
     * @throws SettlementException          그 밖에 배치가 의도적으로 던진 실패 (원장 장애 등)
     */
    public BatchRunResponse run(LocalDate targetDate)
    {
        JobExecution execution = startJob(targetDate);

        rethrowIfFailed(execution);

        long batchId = execution.getExecutionContext().getLong(SettlementJobListener.BATCH_ID_KEY);

        return new BatchRunResponse(
                batchId,
                targetDate,
                batchRepository.findById(batchId).map(batch -> batch.getStatus()).orElse(BatchStatus.FAILED),
                settlementRepository.findByBatchId(batchId).size());
    }

    /**
     * Job을 띄운다.
     * <p>
     * Spring Batch는 <b>파라미터가 같은 Job 인스턴스를 두 번 완료시키지 않는다.</b> 같은
     * 날짜로 다시 부르면 {@code JobInstanceAlreadyCompleteException}이 나는데, 호출자 입장에서
     * 이건 "이미 확정됨"과 다른 사건이 아니다 — <b>둘 다 "그날은 이미 돌았다"</b>이고 할 일도
     * 같다. 코드를 나누면 호출자가 분기해야 하는데 그 분기로 할 일이 다르지 않다.
     *
     * @param targetDate 정산 대상 일자
     * @return 끝난 Job 실행
     */
    private JobExecution startJob(LocalDate targetDate)
    {
        JobParameters parameters = new JobParametersBuilder()
                .addLocalDate(SettlementJobListener.TARGET_DATE_KEY, targetDate)
                .toJobParameters();

        try
        {
            return jobOperator.start(dailySettlementJob, parameters);
        }
        catch (JobInstanceAlreadyCompleteException e)
        {
            throw new DuplicateSettlementException(targetDate);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(targetDate + " 배치를 시작하지 못했습니다", e);
        }
    }

    /**
     * 실패한 실행에서 원래 예외를 꺼내 다시 던진다.
     * <p>
     * <b>이게 없으면 중복 실행이 200 OK로 응답한다.</b> 리스너가 던진
     * {@code DuplicateSettlementException}은 Spring Batch가 삼켜 실행 기록에만 남기 때문이다.
     * <p>
     * {@link SettlementException}이 아닌 실패(예상 못 한 오류)는 그대로 두고 상태만 넘긴다 —
     * 그런 것까지 여기서 감싸면 원인이 한 겹 가려진다.
     *
     * @param execution 끝난 Job 실행
     */
    private void rethrowIfFailed(JobExecution execution)
    {
        if (!execution.getStatus().isUnsuccessful())
        {
            return;
        }

        log.warn("정산 배치 실패 — jobExecutionId={}", execution.getId());

        execution.getAllFailureExceptions().stream()
                .filter(SettlementException.class::isInstance)
                .map(SettlementException.class::cast)
                .findFirst()
                .ifPresent(failure ->
                {
                    throw failure;
                });
    }

}
