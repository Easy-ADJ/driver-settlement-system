package com.example.driversettlementsystem.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * 기사 1명의 하루치 정산 결과. ({@code SETTLEMENTS})
 * <p>
 * <b>PK가 {@code (batchId, driverId)} 복합키다.</b> 이것 자체가 제약이 된다 —
 * 한 배치에서 같은 기사에 정산이 두 줄 생기는 사고를 DB가 막는다. 대리키를 쓰면
 * 그 사고가 저장까지 성공한 뒤 조회에서야 드러난다.
 * <p>
 * {@code fareTotal}과 {@code feeAmount}를 따로 저장하는 이유는, 수수료율이 나중에
 * 바뀌면 {@code amount}만으로는 과거 정산을 재구성할 수 없기 때문이다.
 * <p>
 * ⚠️ <b>결제 단위 근거는 여기 없다.</b> 정산 DB에는 "어떤 건들에서 나온 금액인지"가
 * 담기지 않는다. 그 답은 {@code GET /api/ledger?driver_id=} 응답이 준다.
 *
 * @see SettlementBatch 이 정산이 속한 배치
 */
@Entity
@Table(name = "SETTLEMENTS")
@IdClass(SettlementId.class)
public class Settlement
{

    @Id
    @Column(name = "batch_id")
    private Long batchId;

    /** 기사 ID. 로그인 DB 소유라 FK를 걸 수 없다 — 값만 보관한다. */
    @Id
    @Column(name = "driver_id")
    private Long driverId;

    /**
     * 지급 상쇄 분개의 원장 ID.
     * <p>
     * 지급 분개를 남기기 전에는 {@code null}이다. 값이 있으면 원장에 상쇄 분개가
     * 기록됐다는 뜻이고, 이 컬럼 하나로 "이 정산이 원장에 반영됐는가"를 확인한다 —
     * 지급 분개 누락은 다음날 이중 정산으로 이어진다. 원장 DB 소유라 FK 불가.
     */
    @Column(name = "ledger_id")
    private Long ledgerId;

    /** 수수료 차감 전 운임 합계. 원장 미지급금에서 온 값이다. */
    @Column(name = "fare_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal fareTotal;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal feeAmount;

    /** 실지급액. 항상 {@code fareTotal - feeAmount} 와 같다. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_status", nullable = false)
    private PayoutStatus payoutStatus;

    /** JPA 전용. 외부에서 빈 엔티티를 만들지 못하게 protected로 둔다. */
    protected Settlement()
    {
    }

    /**
     * 정산 항목을 생성한다.
     * <p>
     * {@code amount}를 인자로 받지 않고 {@code fareTotal - feeAmount}로 <b>계산해서</b>
     * 채운다. 셋을 따로 받으면 서로 안 맞는 레코드가 만들어질 수 있고,
     * 그 순간 "왜 이 금액인가"에 대한 답이 거짓말이 된다.
     *
     * @param batchId   소속 배치 ID
     * @param driverId  기사 ID
     * @param fareTotal 수수료 차감 전 운임 합계
     * @param feeAmount 차감할 수수료
     * @return {@link PayoutStatus#CONFIRMED} 상태의 새 정산 항목
     */
    public static Settlement of(Long batchId, Long driverId,
                                BigDecimal fareTotal, BigDecimal feeAmount)
    {
        Settlement settlement = new Settlement();
        settlement.batchId = Objects.requireNonNull(batchId, "batchId는 null일 수 없습니다");
        settlement.driverId = Objects.requireNonNull(driverId, "driverId는 null일 수 없습니다");
        settlement.fareTotal = Objects.requireNonNull(fareTotal, "fareTotal은 null일 수 없습니다");
        settlement.feeAmount = Objects.requireNonNull(feeAmount, "feeAmount는 null일 수 없습니다");
        settlement.amount = fareTotal.subtract(feeAmount);
        settlement.payoutStatus = PayoutStatus.CONFIRMED;
        return settlement;
    }

    /**
     * 지급 분개 기록 후 원장 ID를 채운다.
     *
     * @param ledgerId 원장이 돌려준 지급 상쇄 분개 ID
     */
    public void linkLedgerEntry(Long ledgerId)
    {
        this.ledgerId = Objects.requireNonNull(ledgerId, "ledgerId는 null일 수 없습니다");
    }

    public Long getBatchId()
    {
        return batchId;
    }

    public Long getDriverId()
    {
        return driverId;
    }

    public Long getLedgerId()
    {
        return ledgerId;
    }

    public BigDecimal getFareTotal()
    {
        return fareTotal;
    }

    public BigDecimal getFeeAmount()
    {
        return feeAmount;
    }

    public BigDecimal getAmount()
    {
        return amount;
    }

    public PayoutStatus getPayoutStatus()
    {
        return payoutStatus;
    }

}
