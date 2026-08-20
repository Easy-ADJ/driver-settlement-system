package com.example.driversettlementsystem.settlement.dto;

import com.example.driversettlementsystem.settlement.client.PaymentDetail;
import com.example.driversettlementsystem.settlement.domain.PayoutStatus;
import com.example.driversettlementsystem.settlement.domain.Settlement;
import java.math.BigDecimal;
import java.util.List;

/**
 * 기사 1명의 정산 상세. ({@code FR-B-08} 추적성이 응답으로 드러나는 지점)
 * <p>
 * <b>{@code fareTotal - feeAmount == payoutAmount}가 항상 성립한다.</b> 계산 근거를 함께
 * 노출해서 "왜 이 금액인가"를 응답만으로 설명한다. {@code payoutAmount}만 주면 기사 문의에
 * 답하려고 매번 원본을 뒤져야 한다.
 * <p>
 * 금액 3종이 모두 {@code String}인 것은 <b>의도된 것이다.</b> {@code BigDecimal}로 두면
 * Jackson이 JSON 숫자로 직렬화하고, 자바스크립트 클라이언트가 {@code Number}로 파싱하면서
 * 큰 금액에서 정밀도가 깨진다.
 * <p>
 * <b>{@code driverId}는 숫자다.</b> 문자열로 보내는 이유는 정밀도인데 ID에는 그 문제가 없고,
 * 규약을 확대 적용하면 클라이언트가 매번 파싱해야 한다.
 *
 * @param driverId     기사 ID
 * @param fareTotal    수수료 차감 전 운임 합계 (문자열)
 * @param feeAmount    차감된 수수료 (문자열)
 * @param payoutAmount 실지급액 (문자열)
 * @param payoutStatus 지급 상태
 * @param payments     결제 건별 근거. <b>비어 있어도 생략하지 않는다</b> — 있으면 있는 대로,
 *                     없으면 빈 배열로 나가야 클라이언트가 분기하지 않는다
 */
public record DriverSettlementResponse(
        Long driverId,
        String fareTotal,
        String feeAmount,
        String payoutAmount,
        PayoutStatus payoutStatus,
        List<PaymentDetailResponse> payments)
{

    /**
     * 엔티티와 원장 근거를 합쳐 응답을 만든다.
     * <p>
     * 금액을 여기서 {@code String}으로 바꾼다. {@link BigDecimal#toPlainString()}을 쓰는 것이
     * 중요하다 — {@code toString()}은 값에 따라 지수 표기({@code 1E+4})를 내고, 그러면
     * 클라이언트가 파싱에 실패한다.
     * <p>
     * {@code paymentDetails}는 <b>정산 DB가 아니라 원장에서 온다.</b> 정산은 기사별 합계만
     * 저장하므로 결제 단위 근거를 만들어낼 방법이 없다.
     *
     * @param settlement     저장된 정산 항목
     * @param paymentDetails 원장이 준 결제 건별 내역. 없으면 빈 목록을 넘긴다
     * @return 기사 1명분 조회 응답
     */
    public static DriverSettlementResponse from(Settlement settlement, List<PaymentDetail> paymentDetails)
    {
        return new DriverSettlementResponse(
                settlement.getDriverId(),
                settlement.getFareTotal().toPlainString(),
                settlement.getFeeAmount().toPlainString(),
                settlement.getAmount().toPlainString(),
                settlement.getPayoutStatus(),
                paymentDetails.stream().map(PaymentDetailResponse::from).toList());
    }

}
