package com.example.driversettlementsystem.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.driversettlementsystem.exception.ExternalServiceException;
import com.example.driversettlementsystem.settlement.client.DriverUnpaid;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import com.example.driversettlementsystem.settlement.client.PaymentClient;
import com.example.driversettlementsystem.settlement.client.PaymentSummary;
import com.example.driversettlementsystem.settlement.domain.ReconciliationStatus;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 대사가 무엇을 잡아내고 무엇을 잡아내지 못하는지 고정한다.
 * <p>
 * 여기서 확인하는 실패는 전부 <b>"코드는 정상인데 결과가 틀린"</b> 종류다 — 자릿수 비교,
 * 취소 건 미제외, 상대 서버 장애. 대사가 계속 {@code MISMATCHED}를 뱉으면 금액 로직보다
 * 이 테스트들이 다루는 것부터 확인한다.
 */
class ReconciliationServiceTest
{

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 19);

    private final PaymentClient paymentClient = mock(PaymentClient.class);

    private final LedgerClient ledgerClient = mock(LedgerClient.class);

    private final ReconciliationService reconciliationService =
            new ReconciliationService(paymentClient, ledgerClient);

    @DisplayName("두 출처의 합계가 같으면 MATCHED다")
    @Test
    void judgesMatchedWhenTotalsAgree()
    {
        givenPayments(payment(100L, "15000", "APPROVED"), payment(101L, "32000", "APPROVED"));
        givenUnpaid(unpaid(1L, "15000"), unpaid(2L, "32000"));

        SettlementBatch batch = SettlementBatch.start(TARGET_DATE);

        assertThat(reconciliationService.reconcile(batch)).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(batch.getReconciliationStatus()).isEqualTo(ReconciliationStatus.MATCHED);
    }

    /**
     * <b>{@code BigDecimal.equals}를 썼다면 이 테스트가 실패한다.</b> {@code equals}는
     * 자릿수까지 비교해 {@code 15000}과 {@code 15000.00}을 다르다고 본다. DB에서 읽은 값과
     * API로 받은 값은 scale이 다르기 마련이라, 금액이 같은데도 <b>항상 불일치</b>가 난다.
     */
    @DisplayName("자릿수가 달라도 값이 같으면 MATCHED다 — equals가 아니라 compareTo")
    @Test
    void ignoresScaleDifference()
    {
        givenPayments(payment(100L, "15000", "APPROVED"));
        givenUnpaid(unpaid(1L, "15000.00"));

        assertThat(reconciliationService.reconcile(SettlementBatch.start(TARGET_DATE)))
                .isEqualTo(ReconciliationStatus.MATCHED);
    }

    @DisplayName("합계가 어긋나면 MISMATCHED다")
    @Test
    void judgesMismatchedWhenTotalsDiffer()
    {
        givenPayments(payment(100L, "15000", "APPROVED"));
        givenUnpaid(unpaid(1L, "99999"));

        SettlementBatch batch = SettlementBatch.start(TARGET_DATE);

        assertThat(reconciliationService.reconcile(batch)).isEqualTo(ReconciliationStatus.MISMATCHED);
        assertThat(batch.getReconciliationStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
    }

    /**
     * 원장은 취소 시 상쇄 분개로 미지급금이 이미 줄어 있다. 결제 쪽에서 취소 건을 빼지 않으면
     * <b>그 금액만큼 항상 초과</b>로 나와 매일 불일치가 뜬다.
     */
    @DisplayName("취소·처리중 건은 결제 합계에서 빠진다")
    @Test
    void excludesNonApprovedPayments()
    {
        givenPayments(
                payment(100L, "15000", "APPROVED"),
                payment(101L, "5000", "CANCELLED"),
                payment(102L, "7000", "APPROVING"),
                payment(103L, "3000", "APPROVE_UNKNOWN"));
        givenUnpaid(unpaid(1L, "15000"));

        assertThat(reconciliationService.reconcile(SettlementBatch.start(TARGET_DATE)))
                .isEqualTo(ReconciliationStatus.MATCHED);
    }

    /**
     * <b>검증이 집계를 죽이면 안 된다.</b> 대사는 이미 끝난 집계를 확인하는 일이라, 상대
     * 서버가 잠깐 죽은 것 때문에 정산 결과까지 날리면 손해가 더 크다.
     */
    @DisplayName("결제 서버가 죽으면 SKIPPED — 예외가 밖으로 나가지 않는다")
    @Test
    void judgesSkippedWhenPaymentIsDown()
    {
        when(paymentClient.findPaymentsByDate(any()))
                .thenThrow(ExternalServiceException.payment("결제 서버가 응답하지 않는다", null));

        SettlementBatch batch = SettlementBatch.start(TARGET_DATE);

        assertThatCode(() -> reconciliationService.reconcile(batch)).doesNotThrowAnyException();
        assertThat(batch.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SKIPPED);
    }

    @DisplayName("원장이 죽어도 SKIPPED다")
    @Test
    void judgesSkippedWhenLedgerIsDown()
    {
        givenPayments(payment(100L, "15000", "APPROVED"));
        when(ledgerClient.findUnpaidDrivers(any()))
                .thenThrow(ExternalServiceException.ledger("원장이 응답하지 않는다", null));

        assertThat(reconciliationService.reconcile(SettlementBatch.start(TARGET_DATE)))
                .isEqualTo(ReconciliationStatus.SKIPPED);
    }

    /**
     * 결제도 원장도 비어 있으면 0 = 0이다. <b>{@code SKIPPED}가 아니라 {@code MATCHED}여야
     * 한다</b> — 확인을 못 한 것이 아니라 확인했더니 둘 다 없었던 것이다.
     */
    @DisplayName("양쪽 다 비어 있으면 MATCHED다 — SKIPPED가 아니다")
    @Test
    void judgesMatchedWhenBothSidesAreEmpty()
    {
        givenPayments();
        givenUnpaid();

        assertThat(reconciliationService.reconcile(SettlementBatch.start(TARGET_DATE)))
                .isEqualTo(ReconciliationStatus.MATCHED);
    }

    /**
     * 결제는 있는데 원장에 분개가 안 남은 경우 — <b>대사가 존재하는 이유 그 자체다.</b>
     * DB가 나뉘어 FK로 잡을 수 없는 어긋남이 여기서 드러난다.
     */
    @DisplayName("결제만 있고 원장이 비면 MISMATCHED다")
    @Test
    void catchesPaymentWithoutLedgerEntry()
    {
        givenPayments(payment(100L, "15000", "APPROVED"));
        givenUnpaid();

        assertThat(reconciliationService.reconcile(SettlementBatch.start(TARGET_DATE)))
                .isEqualTo(ReconciliationStatus.MISMATCHED);
    }

    private void givenPayments(PaymentSummary... payments)
    {
        when(paymentClient.findPaymentsByDate(TARGET_DATE)).thenReturn(List.of(payments));
    }

    private void givenUnpaid(DriverUnpaid... drivers)
    {
        when(ledgerClient.findUnpaidDrivers(TARGET_DATE)).thenReturn(List.of(drivers));
    }

    private static PaymentSummary payment(Long paymentId, String amount, String status)
    {
        return new PaymentSummary(paymentId, 1L, new BigDecimal(amount), status);
    }

    private static DriverUnpaid unpaid(Long driverId, String amount)
    {
        return new DriverUnpaid(driverId, new BigDecimal(amount),
                Instant.parse("2026-08-19T14:30:00Z"));
    }

}
