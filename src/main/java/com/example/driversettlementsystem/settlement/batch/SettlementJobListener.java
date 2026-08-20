package com.example.driversettlementsystem.settlement.batch;

import com.example.driversettlementsystem.exception.DuplicateSettlementException;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import com.example.driversettlementsystem.settlement.repository.SettlementBatchRepository;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.stereotype.Component;

/**
 * 정산 Job의 시작·종료 시점 처리.
 * <p>
 * Reader·Processor·Writer는 "데이터 한 건"만 알기 때문에, <b>"배치가 시작됐다/끝났다"는
 * Job 단위 사건은 이 리스너가 맡는다.</b> 배치 레코드 생성을 청크마다 도는 Writer에 넣으면
 * 청크 수만큼 배치가 생긴다.
 * <p>
 * 상태 전이는 반드시 {@link SettlementBatch#transitionTo}를 거친다 — {@code status}를 직접
 * 바꾸면 전이 검증이 무의미해진다.
 * <p>
 * 🚧 <b>대사 트리거는 아직 없다.</b> Job이 성공해도 배치는 {@code RUNNING}으로 남는다.
 * 확정은 대사가 {@code MATCHED}를 낼 때만 하기로 정해져 있고, 대사가 아직 없기 때문이다.
 * 검증되지 않은 금액을 확정으로 올리면 그대로 지급 단계로 넘어간다.
 *
 * @see DuplicateBatchGuard 시작 전 중복 검사
 */
@Component
public class SettlementJobListener implements JobExecutionListener
{

    /**
     * {@code ExecutionContext}에 배치 ID를 담는 키.
     * <p>
     * Processor와 실행 진입점이 같은 키로 꺼낸다. <b>문자열을 여러 곳에 적으면 오타 하나로
     * 조용히 null이 되므로</b> 여기 하나만 두고 참조한다.
     */
    public static final String BATCH_ID_KEY = "batchId";

    /** Job 파라미터에서 정산 대상 일자를 꺼내는 키. 실행 진입점이 같은 키로 넣는다. */
    public static final String TARGET_DATE_KEY = "targetDate";

    private static final Logger log = LoggerFactory.getLogger(SettlementJobListener.class);

    private final DuplicateBatchGuard duplicateBatchGuard;

    private final SettlementBatchRepository batchRepository;

    public SettlementJobListener(DuplicateBatchGuard duplicateBatchGuard,
                                 SettlementBatchRepository batchRepository)
    {
        this.duplicateBatchGuard = duplicateBatchGuard;
        this.batchRepository = batchRepository;
    }

    /**
     * Job 시작 전 — 중복 검사, 배치 생성, {@code batchId} 공유.
     * <p>
     * <b>검사가 저장보다 먼저다.</b> 이미 확정된 날짜면 여기서 예외가 나고 {@code BATCHES}에는
     * 아무것도 남지 않는다 — {@code FR-B-06}의 수락 기준이 "새 레코드가 생성되지 않는다"인
     * 이유다. 시작한 뒤 롤백하는 방식이면 쓸모없는 이력이 쌓인다.
     *
     * @param jobExecution 시작하려는 Job 실행
     * @throws DuplicateSettlementException 이미 확정된 배치가 있을 때
     */
    @Override
    public void beforeJob(JobExecution jobExecution)
    {
        LocalDate targetDate = jobExecution.getJobParameters().getLocalDate(TARGET_DATE_KEY);

        duplicateBatchGuard.verifyNotConfirmed(targetDate);

        SettlementBatch batch = batchRepository.save(SettlementBatch.start(targetDate));
        jobExecution.getExecutionContext().putLong(BATCH_ID_KEY, batch.getBatchId());

        log.info("정산 배치 시작 — targetDate={}, batchId={}", targetDate, batch.getBatchId());
    }

    /**
     * Job 종료 후 — 실패했으면 {@code FAILED}로 전이한다.
     * <p>
     * <b>성공했을 때는 아무것도 하지 않는다.</b> 배치는 {@code RUNNING}으로 남고, 확정은
     * 대사가 {@code MATCHED}를 낼 때만 한다. {@code RUNNING → CONFIRMED} 전이는 열려 있어
     * 대사가 그대로 이어받는다.
     * <p>
     * 중복 거부로 {@link #beforeJob}이 실패한 경우에도 Spring Batch는 이 메서드를 부른다.
     * 그때는 {@code ExecutionContext}에 {@code batchId}가 없다 — <b>만들지도 않은 배치를
     * 실패로 만들려 들면 안 된다.</b>
     *
     * @param jobExecution 끝난 Job 실행
     */
    @Override
    public void afterJob(JobExecution jobExecution)
    {
        if (!jobExecution.getStatus().isUnsuccessful())
        {
            return;
        }

        ExecutionContext executionContext = jobExecution.getExecutionContext();

        if (!executionContext.containsKey(BATCH_ID_KEY))
        {
            log.warn("배치가 생성되기 전에 Job이 실패했다 — 남길 상태가 없다");
            return;
        }

        long batchId = executionContext.getLong(BATCH_ID_KEY);

        batchRepository.findById(batchId).ifPresent(batch ->
        {
            batch.transitionTo(BatchStatus.FAILED);
            batchRepository.save(batch);
            log.error("정산 배치 실패 — batchId={}", batchId);
        });
    }

}
