package com.example.driversettlementsystem.settlement.controller;

import com.example.driversettlementsystem.settlement.dto.BatchRunResponse;
import com.example.driversettlementsystem.settlement.dto.SettlementResponse;
import com.example.driversettlementsystem.settlement.service.SettlementJobRunner;
import com.example.driversettlementsystem.settlement.service.SettlementLifecycleService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배치를 사람이 조작하는 엔드포인트 — 수동 실행과 상태 전이({@code RUNNING → CONFIRMED → PAID}).
 * <p>
 * 세 가지 모두 <b>명시적으로 불러야만</b> 일어난다. 특히 확정과 지급을 자동으로 잇지 않는
 * 이유는, 대사가 불일치를 냈을 때 <b>보류된 배치를 사람이 확인하고 진행시키는 지점</b>이
 * 필요하기 때문이다.
 * <p>
 * {@code @Scheduled}만 있으면 배치가 도는 것을 보여주려고 자정까지 기다려야 한다. 그리고
 * 서버가 그 시각에 죽어 있으면 그날 배치를 <b>아예 건너뛰고 나중에 따라잡지 않는다</b> —
 * Railway가 유휴 인스턴스를 재우므로 실제로 일어날 수 있다.
 * <p>
 * 🔓 <b>이 API는 인증이 없다.</b> 아무나 부르면 정산을 돌릴 수 있다. 이미 확정된 날짜는
 * 중복 검사와 부분 UNIQUE 인덱스가 막지만, 확정 전 날짜로는 얼마든지 실행된다. 데모
 * 범위에서는 이대로 두되 <b>운영으로 갈 때 가장 먼저 잠가야 할 엔드포인트다.</b>
 *
 * @see SettlementJobRunner 스케줄러와 공유하는 실행 경로
 */
@RestController
@RequestMapping("/api/settlements")
public class SettlementBatchController
{

    private final SettlementJobRunner jobRunner;

    private final SettlementLifecycleService lifecycleService;

    public SettlementBatchController(SettlementJobRunner jobRunner,
                                     SettlementLifecycleService lifecycleService)
    {
        this.jobRunner = jobRunner;
        this.lifecycleService = lifecycleService;
    }

    /**
     * 지정한 날짜의 정산 배치를 실행한다.
     * <p>
     * <b>스케줄러와 같은 {@link SettlementJobRunner#run}을 부른다.</b> 여기서 별도 경로를
     * 만들면 수동 실행에만 중복 검사가 빠지는 식으로 규칙이 갈라진다.
     * <p>
     * 실행이 동기라 <b>끝난 뒤에 응답한다.</b> {@code 202 Accepted}가 아니라 {@code 200 OK}인
     * 이유다 — 응답 시점에 이미 결과가 정해져 있는데 "접수했다"고 답하면 사실과 다르다.
     *
     * @param targetDate 정산 대상 일자. {@code yyyy-MM-dd} 형식이며 필수다
     * @return 200 OK + 배치 ID와 실행 결과
     */
    @PostMapping("/batch")
    public BatchRunResponse runBatch(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate)
    {
        return jobRunner.run(targetDate);
    }

    /**
     * 배치를 확정한다. ({@code RUNNING → CONFIRMED}, {@code FR-S-01})
     * <p>
     * <b>이 호출이 원장에 지급 상쇄 분개를 남긴다.</b> 확정과 상쇄를 따로 두면 한쪽만 실행된
     * 배치가 생기고, 상쇄가 빠진 쪽은 <b>다음날 같은 금액이 다시 정산된다.</b>
     * <p>
     * 대사가 불일치를 냈어도 막지 않는다 — <b>보류를 사람이 푸는 지점이 여기다.</b> 대신
     * {@code reconciliationStatus}가 응답에 그대로 나오므로, 무엇을 알고 확정했는지가 남는다.
     *
     * @param batchId 확정할 배치
     * @return 200 OK + 확정 후 상태
     */
    @PostMapping("/{batchId}/confirm")
    public SettlementResponse confirmBatch(@PathVariable Long batchId)
    {
        return lifecycleService.confirm(batchId);
    }

    /**
     * 배치를 지급 완료로 표시한다. ({@code CONFIRMED → PAID}, {@code FR-S-02})
     * <p>
     * <b>실제 송금은 하지 않는다.</b> 데모 범위에서 {@code PAID}는 처리 완료 표식이다.
     *
     * @param batchId 지급 표시할 배치
     * @return 200 OK + 전이 후 상태
     */
    @PostMapping("/{batchId}/pay")
    public SettlementResponse payBatch(@PathVariable Long batchId)
    {
        return lifecycleService.markAsPaid(batchId);
    }

}
