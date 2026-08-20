package com.example.driversettlementsystem.settlement.client;

import java.math.BigDecimal;

/**
 * 결제 서버가 돌려주는 결제 1건 — <b>대사에 필요한 필드만.</b>
 * <p>
 * <b>정산 서버가 소유한 타입이지 결제 서버의 엔티티가 아니다.</b> 상대 응답에서 우리가
 * 실제로 쓰는 것만 담는다. 상대가 필드를 추가해도 여기를 고칠 필요가 없고, 반대로 여기
 * 있는 필드가 사라지면 그때는 반드시 깨져야 한다.
 * <p>
 * 결제가 카카오페이 연동으로 구체화되며 {@code tid}·{@code partnerOrderId} 등이 생겼지만
 * <b>담지 않는다.</b> 대사에 쓰지 않는 필드를 들고 오면 상대 스키마 변경에 불필요하게 묶인다.
 * <p>
 * ⚠️ {@code driverId}를 {@code Long}으로 둔 근거는 {@code erd.md} §2가
 * {@code PAYMENT.driver_id}·{@code MONEY.driver_id}·{@code DRIVER_ACCOUNTS.driver_id}를
 * 전부 BIGINT로 그리고 있다는 것이다. 결제 구현체는 현재 {@code String}이라 어긋나 있고,
 * 결제 레포 이슈에서 타입을 확정하는 중이다.
 *
 * @param paymentId 결제 ID. 불일치 시 어느 건이 문제인지 좁히는 데 쓴다
 * @param driverId  기사 ID. 기사별 대조에 쓴다
 * @param amount    결제 금액. <b>{@code BigDecimal}</b>이다 — {@code double}을 쓰면 원 단위가
 *                  어긋나고, 그게 대사 실패로 나타난다. JSON에서는 문자열로 온다
 * @param status    결제 상태. 취소·실패 건을 합계에서 빼는 데 쓴다
 */
public record PaymentSummary(
        Long paymentId,
        Long driverId,
        BigDecimal amount,
        String status)
{
}
