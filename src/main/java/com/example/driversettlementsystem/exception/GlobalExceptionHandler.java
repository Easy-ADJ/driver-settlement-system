package com.example.driversettlementsystem.exception;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 정산 서버의 모든 예외를 팀 공통 에러 포맷으로 변환하는 단일 지점. ({@code FR-Q-02})
 * <p>
 * 예외 타입마다 핸들러를 만들지 않고 {@link SettlementException} 부모 하나만 잡는다.
 * 예외가 스스로 {@code code}와 HTTP 상태를 들고 오기 때문에, 새 예외를 추가해도
 * 이 클래스는 고치지 않아도 된다.
 * <p>
 * 나머지 핸들러 두 개는 Spring이 던지는 예외(파라미터 누락·타입 변환 실패)를
 * 우리 에러 코드로 옮기는 역할이다.
 *
 * @see ErrorResponse 응답 포맷
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 예상하지 못한 예외의 응답 문구.
     * <p>
     * 예외 메시지를 그대로 내보내면 내부 구조가 새어 나간다. 고정 문구를 쓰고
     * 원인은 {@code transactionId}로 로그에서 찾는다.
     */
    private static final String INTERNAL_ERROR_MESSAGE = "서버 오류가 발생했습니다";

    /**
     * 정산 서버가 의도적으로 던진 예외를 변환한다.
     * <p>
     * 의도된 예외이므로 스택트레이스까지 남기지 않고 {@code warn} 한 줄로 끝낸다.
     * 잘못된 요청 하나하나에 스택트레이스를 찍으면 정작 봐야 할 로그가 묻힌다.
     *
     * @param e 정산 서버가 던진 예외
     * @return 예외가 들고 온 {@code code}와 HTTP 상태를 그대로 쓴 응답
     */
    @ExceptionHandler(SettlementException.class)
    public ResponseEntity<ErrorResponse> handleSettlementException(SettlementException e)
    {
        String transactionId = currentTransactionId();
        log.warn("[{}] {}: {}", transactionId, e.getCode(), e.getMessage());

        return respond(e.getStatus(), e.getCode(), e.getMessage(), transactionId);
    }

    /**
     * 필수 쿼리 파라미터가 빠졌을 때. (예: {@code date} 없이 조회)
     *
     * @param e Spring이 던진 파라미터 누락 예외
     * @return 400 / {@code MISSING_REQUIRED_PARAMETER}
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e)
    {
        String transactionId = currentTransactionId();
        String message = "필수 파라미터가 누락되었습니다: " + e.getParameterName();
        log.warn("[{}] MISSING_REQUIRED_PARAMETER: {}", transactionId, message);

        return respond(HttpStatus.BAD_REQUEST, "MISSING_REQUIRED_PARAMETER", message, transactionId);
    }

    /**
     * 파라미터 타입 변환에 실패했을 때. (예: {@code date=2026-13-99})
     * <p>
     * Spring이 {@code String → LocalDate} 변환에 실패하면 이 예외가 온다.
     * <p>
     * ⚠️ 타입 변환 실패를 전부 이 코드로 묶는다. 조회 API의 변환 대상 파라미터가
     * {@code date}뿐이라 지금은 맞지만, 다른 타입의 파라미터가 생기면
     * 에러 코드 목록에 코드를 추가하고 여기서 나눠야 한다.
     *
     * @param e Spring이 던진 타입 불일치 예외
     * @return 400 / {@code INVALID_DATE_FORMAT}
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e)
    {
        String transactionId = currentTransactionId();
        String message = "파라미터 형식이 올바르지 않습니다: " + e.getName();
        log.warn("[{}] INVALID_DATE_FORMAT: {}", transactionId, message);

        return respond(HttpStatus.BAD_REQUEST, "INVALID_DATE_FORMAT", message, transactionId);
    }

    /**
     * 위에서 걸리지 않은 모든 예외.
     * <p>
     * ⚠️ 여기서만 {@code log.error}로 <b>스택트레이스를 남긴다</b>. 예상하지 못한
     * 예외이므로 원인을 추적할 수 있어야 한다. 반대로 <b>응답 본문에는</b> 내부
     * 정보를 절대 담지 않는다 — {@code transactionId}로 로그를 찾게 한다.
     *
     * @param e 처리되지 않은 예외
     * @return 500 / {@code INTERNAL_ERROR}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e)
    {
        String transactionId = currentTransactionId();
        log.error("[{}] INTERNAL_ERROR", transactionId, e);

        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                INTERNAL_ERROR_MESSAGE, transactionId);
    }

    /**
     * 이 요청의 추적 ID를 얻는다.
     * <p>
     * 🚧 팀 공통 추적 ID 전파 규약이 확정되면 그 값을 쓴다. 잠정적으로는 요청마다
     * UUID를 새로 만든다 — 없는 것보다는 낫고, 규약이 정해지면 이 메서드 하나만
     * 고치면 된다.
     *
     * @return 요청 추적 ID
     */
    private String currentTransactionId()
    {
        return UUID.randomUUID().toString();
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String code,
                                                         String message, String transactionId)
    {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, transactionId));
    }

}
