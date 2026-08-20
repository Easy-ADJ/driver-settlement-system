package com.example.driversettlementsystem.settlement.client;

import java.math.BigDecimal;
import java.util.List;

/**
 * 기사 1명의 미지급금과 <b>그 근거</b>. ({@code GET /api/ledger?driver_id=} 응답)
 * <p>
 * {@code paymentDetails}가 비어 있으면 금액은 계산되지만 "이 금액이 어떤 건들에서
 * 나왔는지"를 답할 수 없다 — {@code trips}가 사라진 뒤로 그 정보는 여기에만 있다.
 * <b>정산 내역서가 이 응답 하나에 걸려 있다.</b>
 * <p>
 * 목록 조회 결과인 {@link DriverUnpaid}와 혼동하지 않는다. 저쪽은 배치가 대상을 고를 때
 * 쓰고, 이쪽은 내역서를 조립할 때 쓴다.
 *
 * @param driverId          기사 ID
 * @param totalUnpaidAmount 미지급금 합계
 * @param paymentDetails    결제 건별 내역. 없으면 빈 목록
 */
public record DriverLedger(
        Long driverId,
        BigDecimal totalUnpaidAmount,
        List<PaymentDetail> paymentDetails)
{
}
