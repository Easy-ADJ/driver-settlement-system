package com.example.driversettlementsystem.exception;

import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import org.springframework.http.HttpStatus;

/**
 * 정의되지 않은 상태 전이를 시도할 때 던진다. (예: {@code PAID → RUNNING})
 * <p>
 * {@code BatchStatus.validateTransitionTo()}가 던진다. 이 예외가 뜬다는 것은 대개
 * <b>호출 코드의 버그</b>이지 사용자 입력 문제가 아니다.
 * <p>
 * {@code from}과 {@code to}를 담아 어떤 전이가 거부됐는지 로그에서 바로 보이게 한다.
 */
public class InvalidStateTransitionException extends SettlementException
{

    private static final String CODE = "INVALID_STATE_TRANSITION";

    private final BatchStatus from;

    private final BatchStatus to;

    public InvalidStateTransitionException(BatchStatus from, BatchStatus to)
    {
        super(CODE, HttpStatus.CONFLICT, from + " 에서 " + to + " 로의 전이는 허용되지 않습니다");
        this.from = from;
        this.to = to;
    }

    public BatchStatus getFrom()
    {
        return from;
    }

    public BatchStatus getTo()
    {
        return to;
    }

}
