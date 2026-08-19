package com.example.driversettlementsystem.settlement.domain;

import java.io.Serializable;

/**
 * {@code SETTLEMENTS} 복합키 {@code (batchId, driverId)}.
 * <p>
 * {@link Settlement}의 {@code @IdClass}로 쓰인다. JPA가 영속성 컨텍스트에서 엔티티를
 * 식별할 때 이 클래스의 {@code equals}·{@code hashCode}를 쓰므로 둘이 반드시 있어야 한다.
 * record가 그 둘을 자동으로 만들어 주므로 손으로 쓰지 않는다.
 * <p>
 * 필드 이름과 타입이 {@link Settlement}의 {@code @Id} 필드와 <b>정확히 같아야</b> 한다.
 * 어긋나면 컴파일은 통과하고 부팅 시점에야 매핑 오류로 드러난다.
 */
public record SettlementId(Long batchId, Long driverId) implements Serializable
{
}
