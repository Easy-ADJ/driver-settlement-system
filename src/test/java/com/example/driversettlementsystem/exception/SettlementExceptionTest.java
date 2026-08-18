package com.example.driversettlementsystem.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 예외 4종이 정의서 §7 에러 코드 표와 어긋나지 않는지 확인한다.
 * <p>
 * {@code code}는 호출자와의 계약이므로 오타 하나가 곧 계약 위반이다.
 * 문서를 고칠 때 이 테스트가 함께 깨져야 한다.
 */
class SettlementExceptionTest
{

    @DisplayName("중복 정산 — SETTLEMENT_ALREADY_CONFIRMED / 409")
    @Test
    void duplicateSettlement()
    {
        LocalDate targetDate = LocalDate.of(2026, 8, 18);
        DuplicateSettlementException e = new DuplicateSettlementException(targetDate);

        assertThat(e.getCode()).isEqualTo("SETTLEMENT_ALREADY_CONFIRMED");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getTargetDate()).isEqualTo(targetDate);
    }

    @DisplayName("대사 불일치 — RECONCILIATION_MISMATCH / 409, 두 합계를 함께 담는다")
    @Test
    void reconciliationMismatch()
    {
        BigDecimal paymentTotal = new BigDecimal("42000");
        BigDecimal ledgerUnpaidTotal = new BigDecimal("41999");
        ReconciliationMismatchException e =
                new ReconciliationMismatchException(paymentTotal, ledgerUnpaidTotal);

        assertThat(e.getCode()).isEqualTo("RECONCILIATION_MISMATCH");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getPaymentTotal()).isEqualByComparingTo(paymentTotal);
        assertThat(e.getLedgerUnpaidTotal()).isEqualByComparingTo(ledgerUnpaidTotal);
    }

    @DisplayName("잘못된 상태 전이 — INVALID_STATE_TRANSITION / 409")
    @Test
    void invalidStateTransition()
    {
        InvalidStateTransitionException e =
                new InvalidStateTransitionException(BatchStatus.PAID, BatchStatus.RUNNING);

        assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @DisplayName("결제 서버 실패 — PAYMENT_SERVICE_UNAVAILABLE / 500")
    @Test
    void paymentServiceUnavailable()
    {
        ExternalServiceException e = ExternalServiceException.payment("연결 타임아웃", null);

        assertThat(e.getCode()).isEqualTo("PAYMENT_SERVICE_UNAVAILABLE");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(e.getServiceName()).isEqualTo("payment");
    }

    @DisplayName("원장 서버 실패 — LEDGER_SERVICE_UNAVAILABLE / 500")
    @Test
    void ledgerServiceUnavailable()
    {
        ExternalServiceException e = ExternalServiceException.ledger("5xx 응답", null);

        assertThat(e.getCode()).isEqualTo("LEDGER_SERVICE_UNAVAILABLE");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(e.getServiceName()).isEqualTo("ledger");
    }

}
