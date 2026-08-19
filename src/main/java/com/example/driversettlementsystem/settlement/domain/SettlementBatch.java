package com.example.driversettlementsystem.settlement.domain;

import com.example.driversettlementsystem.exception.InvalidStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 하루치 정산 배치 1회 실행. ({@code BATCHES})
 * <p>
 * {@code targetDate} 하루당 확정된 배치는 최대 1건이어야 한다. 이 불변식이
 * 프로젝트의 핵심 목표인 "중복 없이 한 번만"을 테이블 수준에서 지탱한다.
 * 애플리케이션 검증({@code DuplicateBatchGuard})만으로는 두 프로세스가 동시에
 * 검사를 통과하는 경합을 막지 못하므로, DB 부분 UNIQUE 인덱스를 함께 건다.
 *
 * @see Settlement 이 배치에 속한 기사별 상세
 */
@Entity
@Table(name = "BATCHES")
public class SettlementBatch
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_id")
    private Long batchId;

    /** 정산 대상 일자. 중복 실행 판정의 키가 된다. */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    /** 이 배치 전체의 지급액 합계. 대사에서 바로 쓰려고 저장한다. */
    @Column(name = "total_payout_amount", precision = 19, scale = 2)
    private BigDecimal totalPayoutAmount;

    /**
     * 대사 판정 결과.
     * <p>
     * ⚠️ <b>{@code MISMATCHED}면 {@code CONFIRMED}로 올리지 않는다.</b>
     * 이 필드는 기록용이 아니라 상태 전이를 좌우하는 값이다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status")
    private ReconciliationStatus reconciliationStatus;

    /** {@code CONFIRMED}로 전이한 시각. 확정 전이면 null이다. */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** JPA 전용. 외부에서 빈 엔티티를 만들지 못하게 protected로 둔다. */
    protected SettlementBatch()
    {
    }

    /**
     * 배치 실행을 시작한다.
     * <p>
     * 시작 시점에 정해지는 것은 대상 일자와 실행 시각뿐이다. 금액과 대사 결과는
     * 집계가 끝나야 나오므로 null로 둔다.
     *
     * @param targetDate 정산 대상 일자
     * @return {@link BatchStatus#RUNNING} 상태의 새 배치
     */
    public static SettlementBatch start(LocalDate targetDate)
    {
        SettlementBatch batch = new SettlementBatch();
        batch.targetDate = Objects.requireNonNull(targetDate, "targetDate는 null일 수 없습니다");
        batch.status = BatchStatus.RUNNING;
        batch.executedAt = Instant.now();
        return batch;
    }

    /**
     * 배치 상태를 전이시킨다.
     * <p>
     * 전이 가능 여부는 {@link BatchStatus#validateTransitionTo(BatchStatus)}가 판단한다.
     * 이 메서드를 거치지 않고 {@code status}를 직접 바꾸는 코드가 생기면
     * {@code FR-S-01}이 깨지므로 setter를 열지 않는다.
     * <p>
     * {@link BatchStatus#CONFIRMED}로 가는 전이에서만 {@code confirmedAt}을 채운다.
     * 확정 시각을 호출자가 따로 넣게 두면 전이와 시각이 어긋날 수 있다.
     *
     * @param next 전이하려는 상태
     * @throws InvalidStateTransitionException 정의되지 않은 전이일 때
     */
    public void transitionTo(BatchStatus next)
    {
        status.validateTransitionTo(next);
        status = next;

        if (next == BatchStatus.CONFIRMED)
        {
            confirmedAt = Instant.now();
        }
    }

    public Long getBatchId()
    {
        return batchId;
    }

    public LocalDate getTargetDate()
    {
        return targetDate;
    }

    public BatchStatus getStatus()
    {
        return status;
    }

    public Instant getExecutedAt()
    {
        return executedAt;
    }

    public BigDecimal getTotalPayoutAmount()
    {
        return totalPayoutAmount;
    }

    public ReconciliationStatus getReconciliationStatus()
    {
        return reconciliationStatus;
    }

    public Instant getConfirmedAt()
    {
        return confirmedAt;
    }

}
