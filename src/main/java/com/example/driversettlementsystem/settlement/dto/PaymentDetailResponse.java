package com.example.driversettlementsystem.settlement.dto;

import com.example.driversettlementsystem.settlement.client.PaymentDetail;
import java.time.Instant;

/**
 * 정산 금액을 구성하는 결제 1건. ({@code FR-B-08} 추적성의 최소 단위)
 * <p>
 * 기사가 "이 금액이 왜 이렇게 나왔냐"고 물었을 때 답이 되는 단위다.
 * <p>
 * <b>정산 DB에 없는 값이다.</b> {@code trips}가 ERD에서 사라지면서 정산은 기사별 합계만
 * 들고 있고, 결제 단위 근거는 원장에만 있다. 조회 시점에 원장에서 받아 채운다.
 *
 * @param paymentId  결제 ID
 * @param amount     그 결제의 금액. <b>문자열</b>이다
 * @param approvedAt 결제 승인 시각 (ISO-8601 UTC)
 */
public record PaymentDetailResponse(Long paymentId, String amount, Instant approvedAt)
{

    /**
     * 원장 응답을 조회 응답으로 옮긴다.
     *
     * @param detail 원장이 준 결제 건별 내역
     * @return 조회 응답용 결제 1건
     */
    public static PaymentDetailResponse from(PaymentDetail detail)
    {
        return new PaymentDetailResponse(
                detail.paymentId(),
                detail.amount().toPlainString(),
                detail.approvedAt());
    }

}
