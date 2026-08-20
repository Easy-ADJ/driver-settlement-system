package com.example.driversettlementsystem.settlement.client;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 미지급금을 구성하는 결제 1건.
 * <p>
 * 기사가 "이 금액이 왜 이렇게 나왔냐"고 물었을 때 답이 되는 단위다.
 *
 * @param paymentId  결제 ID — 문의에 답할 때 이 값으로 되짚는다
 * @param amount     그 결제의 금액
 * @param approvedAt 결제 승인 시각. 대사에서 결제 응답과 대조한다
 */
public record PaymentDetail(Long paymentId, BigDecimal amount, Instant approvedAt)
{
}
