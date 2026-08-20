package com.example.driversettlementsystem.settlement.controller;

import com.example.driversettlementsystem.settlement.dto.BatchRunResponse;
import com.example.driversettlementsystem.settlement.service.SettlementJobRunner;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배치 수동 실행. 시연과 "놓친 날 메우기"용이다.
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

    public SettlementBatchController(SettlementJobRunner jobRunner)
    {
        this.jobRunner = jobRunner;
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

}
