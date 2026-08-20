package com.example.driversettlementsystem.settlement.dto;

import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import java.time.LocalDate;

/**
 * 배치 실행 결과. ({@code POST /api/settlements/batch} 응답)
 * <p>
 * 실행이 <b>끝난 뒤</b> 나가는 응답이다. Job이 동기로 돌기 때문에 응답 시점에는 이미
 * 결과가 정해져 있고, 그래서 {@code 202 Accepted}가 아니라 {@code 200 OK}다.
 * <p>
 * <b>{@code batchStatus}가 {@code RUNNING}으로 나오는 것은 정상이다.</b> 배치는 성공해도
 * 확정되지 않는다 — 확정은 대사가 {@code MATCHED}를 낼 때만 한다.
 *
 * @param batchId         만들어진 배치 ID. 조회 API로 바로 확인할 수 있다
 * @param targetDate      정산 대상 일자
 * @param batchStatus     실행 후 배치 상태
 * @param settlementCount 저장된 정산 항목 수
 */
public record BatchRunResponse(
        Long batchId,
        LocalDate targetDate,
        BatchStatus batchStatus,
        int settlementCount)
{
}
