package com.example.driversettlementsystem.exception;

import org.springframework.http.HttpStatus;

/**
 * 결제·원장 서버 호출이 재시도까지 소진하고 실패했을 때 던진다.
 * <p>
 * {@code serviceName}에 따라 에러 코드가 갈린다 — 결제면
 * {@code PAYMENT_SERVICE_UNAVAILABLE}, 원장이면 {@code LEDGER_SERVICE_UNAVAILABLE}.
 * 한 클래스로 두 코드를 처리한다.
 * <p>
 * ⚠️ <b>상대 서버의 에러 메시지를 그대로 클라이언트에 노출하지 않는다.</b> 내부 구조가
 * 새어 나가고, 상대가 문구를 바꾸면 우리 응답도 같이 바뀐다. 원문은 {@code message}에
 * 담아 로그로만 남기고, 계약인 {@code code}는 원문과 무관하게 고정된다.
 */
public class ExternalServiceException extends SettlementException
{

    private static final String PAYMENT = "payment";

    private static final String LEDGER = "ledger";

    private final String serviceName;

    private ExternalServiceException(String serviceName, String code, String detail, Throwable cause)
    {
        super(code, HttpStatus.INTERNAL_SERVER_ERROR, serviceName + " 서버 호출 실패 — " + detail, cause);
        this.serviceName = serviceName;
    }

    /**
     * 결제 서버 호출 실패.
     *
     * @param detail 로그에 남길 실패 사유
     * @param cause  원인 예외. 없으면 {@code null}
     */
    public static ExternalServiceException payment(String detail, Throwable cause)
    {
        return new ExternalServiceException(PAYMENT, "PAYMENT_SERVICE_UNAVAILABLE", detail, cause);
    }

    /**
     * 원장 서버 호출 실패.
     *
     * @param detail 로그에 남길 실패 사유
     * @param cause  원인 예외. 없으면 {@code null}
     */
    public static ExternalServiceException ledger(String detail, Throwable cause)
    {
        return new ExternalServiceException(LEDGER, "LEDGER_SERVICE_UNAVAILABLE", detail, cause);
    }

    public String getServiceName()
    {
        return serviceName;
    }

}
