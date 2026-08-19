package com.example.driversettlementsystem.exception;

import org.springframework.http.HttpStatus;

/**
 * 정산 서버가 의도적으로 던지는 예외의 공통 부모.
 * <p>
 * 모든 하위 예외는 에러 코드를 하나씩 들고 다닌다. 이 코드가 호출자와의 계약이고,
 * {@code message}는 계약이 아니라 로그용이다. 호출자는 {@code code}로만 분기하고
 * {@code message}는 로그에만 쓴다.
 * <p>
 * 코드와 상태를 부모가 들고 있으므로 {@code GlobalExceptionHandler}는 이 타입
 * 하나만 잡아 변환할 수 있다. 예외를 추가해도 핸들러를 고치지 않아도 된다.
 */
public abstract class SettlementException extends RuntimeException
{

    private final String code;

    private final HttpStatus status;

    protected SettlementException(String code, HttpStatus status, String message)
    {
        this(code, status, message, null);
    }

    protected SettlementException(String code, HttpStatus status, String message, Throwable cause)
    {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String getCode()
    {
        return code;
    }

    public HttpStatus getStatus()
    {
        return status;
    }

}
