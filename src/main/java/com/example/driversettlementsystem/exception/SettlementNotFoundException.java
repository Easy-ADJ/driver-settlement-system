package com.example.driversettlementsystem.exception;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/**
 * 조회한 일자·기사의 정산 내역이 없을 때 던진다. ({@code FR-Q-01})
 * <p>
 * <b>"배치가 없다"와 "그 기사 항목이 없다"를 구분하지 않는다.</b> 호출자 입장에서는 둘 다
 * "찾는 내역이 없다"이고, 어느 쪽인지는 {@code message}로 로그에 남는다. 코드를 둘로 나누면
 * 호출자가 분기해야 하는데 그 분기로 할 일이 다르지 않다.
 * <p>
 * 설계 문서의 예외 4종에는 조회 실패가 빠져 있었지만, 에러 코드 문서에는
 * {@code SETTLEMENT_NOT_FOUND} / 404가 정의돼 있다 — 문서에는 있고 클래스만 없던 것을 채운다.
 */
public class SettlementNotFoundException extends SettlementException
{

    private static final String CODE = "SETTLEMENT_NOT_FOUND";

    private final LocalDate targetDate;

    private SettlementNotFoundException(LocalDate targetDate, String detail)
    {
        super(CODE, HttpStatus.NOT_FOUND, detail);
        this.targetDate = targetDate;
    }

    /**
     * 해당 일자에 배치 자체가 없을 때.
     *
     * @param targetDate 조회한 일자
     * @return 조회 실패 예외
     */
    public static SettlementNotFoundException batchNotFound(LocalDate targetDate)
    {
        return new SettlementNotFoundException(targetDate, targetDate + " 정산 배치가 없습니다");
    }

    /**
     * 배치 ID로 찾았는데 그런 배치가 없을 때.
     * <p>
     * 이때 {@link #getTargetDate()}는 {@code null}이다 — 배치를 찾지 못했으니 그 배치가
     * 어느 날짜였는지 알 방법이 없다.
     *
     * @param batchId 조회한 배치 ID
     * @return 조회 실패 예외
     */
    public static SettlementNotFoundException batchIdNotFound(Long batchId)
    {
        return new SettlementNotFoundException(null, "배치 " + batchId + "를 찾을 수 없습니다");
    }

    /**
     * 배치는 있지만 그 기사의 정산 항목이 없을 때.
     * <p>
     * 미지급금이 0이어서 항목이 만들어지지 않은 기사가 여기 해당한다.
     *
     * @param targetDate 조회한 일자
     * @param driverId   조회한 기사 ID
     * @return 조회 실패 예외
     */
    public static SettlementNotFoundException driverNotFound(LocalDate targetDate, Long driverId)
    {
        return new SettlementNotFoundException(
                targetDate, targetDate + " 기사 " + driverId + "의 정산 항목이 없습니다");
    }

    public LocalDate getTargetDate()
    {
        return targetDate;
    }

}
