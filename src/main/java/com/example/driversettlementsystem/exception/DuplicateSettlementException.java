package com.example.driversettlementsystem.exception;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/**
 * 같은 {@code targetDate}로 이미 확정된 배치가 있는데 다시 실행하려 할 때 던진다.
 * <p>
 * <b>Job이 시작되기 전에</b> 던져야 한다. 시작 후 롤백하면 {@code BATCHES}에
 * 쓸모없는 레코드가 남는다.
 */
public class DuplicateSettlementException extends SettlementException
{

    private static final String CODE = "SETTLEMENT_ALREADY_CONFIRMED";

    private final LocalDate targetDate;

    public DuplicateSettlementException(LocalDate targetDate)
    {
        super(CODE, HttpStatus.CONFLICT, targetDate + " 정산이 이미 확정되었습니다");
        this.targetDate = targetDate;
    }

    public LocalDate getTargetDate()
    {
        return targetDate;
    }

}
