package com.example.driversettlementsystem.settlement.batch;

import com.example.driversettlementsystem.exception.DuplicateSettlementException;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.ReconciliationStatus;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import com.example.driversettlementsystem.settlement.repository.SettlementBatchRepository;
import com.example.driversettlementsystem.settlement.repository.SettlementRepository;
import com.example.driversettlementsystem.settlement.service.ReconciliationService;
import com.example.driversettlementsystem.settlement.service.SettlementLifecycleService;
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
 * <b>확정은 대사가 {@code MATCHED}를 낼 때만 한다.</b> 검증되지 않은 금액을 확정으로 올리면
 * 그대로 지급 단계로 넘어간다. {@code MISMATCHED}(틀렸다)와 {@code SKIPPED}(확인 못 했다)는
 * 다른 정보지만 <b>둘 다 확정을 막는다</b> — 후속 조치만 다르다.
 *
 * @see DuplicateBatchGuard        시작 전 중복 검사
 * @see ReconciliationService      종료 후 대사
 * @see SettlementLifecycleService 확정 — 수동 확정과 공유하는 단 하나의 경로
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

    private final SettlementRepository settlementRepository;

    private final ReconciliationService reconciliationService;

    private final SettlementLifecycleService lifecycleService;

    public SettlementJobListener(DuplicateBatchGuard duplicateBatchGuard,
                                 SettlementBatchRepository batchRepository,
                                 SettlementRepository settlementRepository,
                                 ReconciliationService reconciliationService,
                                 SettlementLifecycleService lifecycleService)
    {
        this.duplicateBatchGuard = duplicateBatchGuard;
        this.batchRepository = batchRepository;
        this.settlementRepository = settlementRepository;
        this.reconciliationService = reconciliationService;
        this.lifecycleService = lifecycleService;
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
     * Job 종료 후 — 실패면 {@code FAILED}, 성공이면 합계 집계 → 대사 → 판정에 따른 분기.
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
        ExecutionContext executionContext = jobExecution.getExecutionContext();

        if (!executionContext.containsKey(BATCH_ID_KEY))
        {
            log.warn("배치가 생성되기 전에 Job이 끝났다 — 남길 상태가 없다");
            return;
        }

        long batchId = executionContext.getLong(BATCH_ID_KEY);

        if (jobExecution.getStatus().isUnsuccessful())
        {
            markFailed(batchId);
            return;
        }

        settleUp(batchId);
    }

    /**
     * @param batchId 실패한 배치
     */
    private void markFailed(long batchId)
    {
        batchRepository.findById(batchId).ifPresent(batch ->
        {
            batch.transitionTo(BatchStatus.FAILED);
            batchRepository.save(batch);
            log.error("정산 배치 실패 — batchId={}", batchId);
        });
    }

    /**
     * 집계가 끝난 배치의 합계를 채우고 대사한 뒤, {@code MATCHED}일 때만 확정한다.
     * <p>
     * <b>판정 결과를 먼저 저장하고 확정한다.</b> 순서를 뒤집으면 확정 중 오류가 났을 때
     * "대사를 했는지조차" 알 수 없는 배치가 남는다.
     *
     * @param batchId 성공한 배치
     */
    private void settleUp(long batchId)
    {
        SettlementBatch batch = batchRepository.findById(batchId).orElse(null);

        if (batch == null)
        {
            log.error("배치를 찾을 수 없다 — batchId={}", batchId);
            return;
        }

        batch.recordTotalPayout(settlementRepository.sumAmountByBatchId(batchId));

        ReconciliationStatus reconciliation = reconciliationService.reconcile(batch);

        batchRepository.save(batch);

        if (reconciliation != ReconciliationStatus.MATCHED)
        {
            log.warn("대사가 {}이라 확정하지 않는다 — batchId={}", reconciliation, batchId);
            return;
        }

        confirmQuietly(batchId);
    }

    /**
     * 확정한다. <b>여기서 실패해도 Job까지 실패로 만들지 않는다.</b>
     * <p>
     * 집계는 이미 끝났고 대사도 통과했다. 원장이 잠깐 죽어 상쇄 분개를 못 남긴 것 때문에
     * 그 결과까지 날릴 이유가 없다 — 배치는 {@code RUNNING}으로 남고, <b>사람이
     * {@code POST /api/settlements/&#123;batchId&#125;/confirm}으로 이어서 진행하면 된다.</b>
     * 그 엔드포인트가 존재하는 이유이기도 하다.
     * <p>
     * ⚠️ 반대로 여기서 예외를 밖으로 던지면 Spring Batch가 이미 성공으로 기록한 Job의
     * 상태와 어긋나고, 그 어긋남은 실행 기록에만 남아 <b>아무도 보지 않는다.</b>
     *
     * @param batchId 확정할 배치
     */
    private void confirmQuietly(long batchId)
    {
        try
        {
            lifecycleService.confirm(batchId);
            log.info("대사 일치 — 배치를 확정했다. batchId={}", batchId);
        }
        catch (RuntimeException e)
        {
            log.error("대사는 일치했으나 확정에 실패했다 — batchId={}. "
                    + "POST /api/settlements/{}/confirm 으로 이어서 진행할 수 있다", batchId, batchId, e);
        }
    }

}
