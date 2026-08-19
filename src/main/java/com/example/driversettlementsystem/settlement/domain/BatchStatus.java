package com.example.driversettlementsystem.settlement.domain;

import com.example.driversettlementsystem.exception.InvalidStateTransitionException;
import java.util.Set;

/**
 * 정산 배치의 생명주기 상태.
 * <p>
 * 상태 전이 규칙을 이 Enum 안에 모아 둔다. 전이 검증을 서비스 코드에 흩어 놓으면
 * 한 곳을 빠뜨렸을 때 잘못된 전이가 조용히 통과한다. 상태를 바꾸는 모든 코드는
 * 반드시 {@link #validateTransitionTo(BatchStatus)}를 거친다.
 *
 * <pre>
 *   [*] ──▶ RUNNING ──▶ CONFIRMED ──▶ PAID
 *              │  ▲
 *              ▼  │
 *            FAILED
 * </pre>
 */
public enum BatchStatus
{

    /** Job이 실행 중이다. 배치 시작 시점의 최초 상태. */
    RUNNING,

    /** 집계가 끝나 금액이 확정됐다. 아직 지급 표시 전이다. */
    CONFIRMED,

    /**
     * 지급 완료로 표시됐다.
     * <p>
     * ⚠️ <b>실제 송금을 뜻하지 않는다.</b> 송금 연동은 이 프로젝트 범위 밖이다.
     * 되돌아가는 전이가 없는 유일한 종료 상태다.
     */
    PAID,

    /**
     * Job이 실패했다.
     * <p>
     * 종료 상태가 아니다 — {@link #RUNNING}으로 재시작할 수 있다. Spring Batch
     * 메타 테이블이 실패 지점을 기억하므로 처음부터 다시 돌지 않는다.
     */
    FAILED;

    /**
     * 현재 상태에서 {@code next}로 전이할 수 있는지 검증한다.
     * <p>
     * 같은 상태로의 전이와 {@code null}도 정의되지 않은 전이로 보고 거부한다.
     *
     * @param next 전이하려는 상태
     * @throws InvalidStateTransitionException 정의되지 않은 전이일 때
     */
    public void validateTransitionTo(BatchStatus next)
    {
        if (next == null || !allowedNextStates().contains(next))
        {
            throw new InvalidStateTransitionException(this, next);
        }
    }

    /**
     * 이 상태에서 갈 수 있는 다음 상태들.
     * <p>
     * FC-04 상태 전이 표가 이 메서드 하나에 대응한다. 규칙을 고칠 곳은 여기뿐이다.
     */
    private Set<BatchStatus> allowedNextStates()
    {
        return switch (this)
        {
            case RUNNING -> Set.of(CONFIRMED, FAILED);
            case CONFIRMED -> Set.of(PAID);
            case FAILED -> Set.of(RUNNING);
            case PAID -> Set.of();
        };
    }

}
