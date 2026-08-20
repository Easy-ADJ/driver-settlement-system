package com.example.driversettlementsystem.settlement.dto;

import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.ReconciliationStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/settlements} 응답 전체. ({@code FR-Q-01})
 * <p>
 * 배치 수준 정보(일자·상태·대사 결과)와 기사별 상세를 함께 담는다.
 * <p>
 * <b>{@code reconciliationStatus}를 노출하는 것이 중요하다.</b> 대사가 {@code MATCHED}가
 * 아니면 배치를 확정하지 않기로 정해져 있지만, 그렇다고 조회에서 감출 이유는 없다 —
 * 관리자는 "왜 아직 확정이 안 됐는지"를 알아야 하고, {@code MISMATCHED}(틀렸다)와
 * {@code SKIPPED}(확인 못 했다)는 <b>후속 조치가 다르다.</b> 전자는 금액을 조사해야 하고,
 * 후자는 원장이 돌아온 뒤 다시 대사하면 된다.
 *
 * @param targetDate           정산 대상 일자 ({@code yyyy-MM-dd})
 * @param batchStatus          배치 상태
 * @param reconciliationStatus 대사 판정 결과. <b>대사 전이면 {@code null}</b>
 * @param settlements          기사별 정산 상세
 */
public record SettlementResponse(
        LocalDate targetDate,
        BatchStatus batchStatus,
        ReconciliationStatus reconciliationStatus,
        List<DriverSettlementResponse> settlements)
{
}
