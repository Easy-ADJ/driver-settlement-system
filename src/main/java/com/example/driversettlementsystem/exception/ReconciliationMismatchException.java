package com.example.driversettlementsystem.exception;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

/**
 * 결제 서버의 전일 결제 합계와 원장 미지급금 합계가 어긋날 때 던진다.
 * <p>
 * <b>두 금액을 함께 담는다.</b> 1원 단위 차이인지 실제 불일치인지 구별해야 하므로
 * 메시지 문자열만으로는 부족하다.
 * <p>
 * 불일치 시 배치를 {@code CONFIRMED}로 올리지 않고 보류한다. 틀린 금액이 지급
 * 단계로 넘어가지 않게 막는 것이 목적이다.
 */
public class ReconciliationMismatchException extends SettlementException
{

    private static final String CODE = "RECONCILIATION_MISMATCH";

    private final BigDecimal paymentTotal;

    private final BigDecimal ledgerUnpaidTotal;

    public ReconciliationMismatchException(BigDecimal paymentTotal, BigDecimal ledgerUnpaidTotal)
    {
        super(CODE, HttpStatus.CONFLICT,
                "대사 불일치 — 결제 합계 " + paymentTotal + ", 원장 미지급 합계 " + ledgerUnpaidTotal);
        this.paymentTotal = paymentTotal;
        this.ledgerUnpaidTotal = ledgerUnpaidTotal;
    }

    public BigDecimal getPaymentTotal()
    {
        return paymentTotal;
    }

    public BigDecimal getLedgerUnpaidTotal()
    {
        return ledgerUnpaidTotal;
    }

}
