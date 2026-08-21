package com.example.driversettlementsystem.settlement.service;

import com.example.driversettlementsystem.exception.ExternalServiceException;
import com.example.driversettlementsystem.settlement.client.DriverUnpaid;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import com.example.driversettlementsystem.settlement.client.PaymentClient;
import com.example.driversettlementsystem.settlement.client.PaymentSummary;
import com.example.driversettlementsystem.settlement.domain.ReconciliationStatus;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 결제 합계와 원장 미지급 합계를 대조해 일치 여부를 판정한다.
 * <p>
 * <b>이 서비스가 "기사 지급액을 원장만 보고 설명할 수 있게" 만드는 장치다.</b> DB가 나뉘어
 * FK로 참조 무결성을 보장할 수 없으므로, 결제와 원장이 어긋났을 때 그것을 발견하는 유일한
 * 수단이 대사다. 결제는 승인됐는데 원장에 분개가 안 남은 경우가 여기서 잡힌다.
 * <p>
 * ⚠️ <b>정산 합계를 원장과 비교하지 않는다.</b> 정산 금액은 원장에서 받아온 값이므로, 그
 * 둘을 비교하면 같은 값을 자기 자신과 대조하는 것이 되어 <b>항상 일치하고 아무것도 검증하지
 * 못한다.</b> 결제 서버가 독립된 두 번째 출처이기 때문에 검증이 성립한다.
 *
 * @see PaymentClient 두 번째 출처
 * @see LedgerClient  첫 번째 출처 (정산 금액의 출처이기도 하다)
 */
@Service
public class ReconciliationService
{

    /**
     * 결제 합계에 넣을 상태.
     * <p>
     * 결제 서버가 승인 성공 시 넣는 값이 {@code "APPROVED"}다 — 그쪽 {@code PaymentService}가
     * 쓰는 나머지 값은 {@code CANCELLED}·{@code APPROVING}·{@code APPROVE_UNKNOWN}이고,
     * 셋 다 합계에 넣으면 안 된다. <b>취소 건을 안 빼면 그만큼 항상 초과로 나온다</b> —
     * 원장은 취소 시 상쇄 분개로 미지급금이 이미 줄어 있기 때문이다.
     * <p>
     * 🚧 이 값은 결제 서버 코드를 읽어 정한 것이고 계약으로 합의된 것은 아니다
     * (<a href="https://github.com/Easy-ADJ/driver-payment-system/issues/2">payment#2</a>).
     * 틀렸다면 대사가 <b>계속 {@code MISMATCHED}로 시끄럽게 울린다</b> — 조용히 틀리지는
     * 않으므로 여기서는 그 편을 택했다.
     */
    static final String COMPLETED_PAYMENT_STATUS = "APPROVED";

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final PaymentClient paymentClient;

    private final LedgerClient ledgerClient;

    public ReconciliationService(PaymentClient paymentClient, LedgerClient ledgerClient)
    {
        this.paymentClient = paymentClient;
        this.ledgerClient = ledgerClient;
    }

    /**
     * 배치 하나를 대사하고 판정 결과를 배치에 기록한다.
     * <p>
     * 어느 한쪽 호출이 실패하면 {@link ReconciliationStatus#SKIPPED}를 기록하고 <b>예외를
     * 밖으로 던지지 않는다.</b> 상대 서버가 잠깐 죽었다는 이유로 이미 끝난 정산 집계까지
     * 실패로 만들 이유가 없다 — <b>대사는 검증이지 집계가 아니다.</b>
     * <p>
     * 다만 {@code SKIPPED}도 <b>확정을 막는다.</b> "확인 못 했다"와 "맞다"는 다른 정보이므로,
     * 확인하지 못한 금액을 지급 단계로 넘기지 않는다.
     * <p>
     * ⚠️ <b>이 비교는 "전일 배치가 정상 확정돼 상쇄까지 끝났다"를 전제한다.</b> 원장이 주는
     * 것은 그 날짜 기준 <b>누적</b> 미지급 잔액이라, 어제 정산을 건너뛰었다면 어제치가 함께
     * 잡혀 {@code MISMATCHED}가 난다. 그건 오탐이 아니라 <b>실제로 확인이 필요한 상태</b>지만,
     * 원인이 오늘 금액이 아니라 어제 누락이라는 것을 로그의 차이 금액으로 판단해야 한다.
     *
     * @param batch 대사할 배치
     * @return 판정 결과. 배치에도 같은 값이 기록된다
     */
    public ReconciliationStatus reconcile(SettlementBatch batch)
    {
        ReconciliationStatus status = judge(batch.getBatchId(), batch.getTargetDate());

        batch.recordReconciliation(status);

        return status;
    }

    /**
     * 두 출처의 합계를 구해 비교한다.
     *
     * @param batchId    로그에 남길 배치 ID
     * @param targetDate 대사 대상 일자
     * @return 판정 결과
     */
    private ReconciliationStatus judge(Long batchId, LocalDate targetDate)
    {
        BigDecimal paymentTotal;
        BigDecimal ledgerTotal;

        try
        {
            paymentTotal = fetchPaymentTotal(targetDate);
            ledgerTotal = fetchLedgerTotal(targetDate);
        }
        catch (ExternalServiceException e)
        {
            log.warn("대사를 수행하지 못했다 — batchId={}, targetDate={}, 사유={}",
                    batchId, targetDate, e.getMessage());
            return ReconciliationStatus.SKIPPED;
        }

        if (amountsMatch(paymentTotal, ledgerTotal))
        {
            log.info("대사 일치 — batchId={}, 합계={}", batchId, paymentTotal.toPlainString());
            return ReconciliationStatus.MATCHED;
        }

        // 차이 금액이 없으면 원인을 좁힐 수 없다. "안 맞는다"만으로는 어디를 볼지 알 수 없다.
        log.error("대사 불일치 — batchId={}, targetDate={}, 결제={}, 원장={}, 차이={}",
                batchId, targetDate,
                paymentTotal.toPlainString(),
                ledgerTotal.toPlainString(),
                paymentTotal.subtract(ledgerTotal).toPlainString());

        return ReconciliationStatus.MISMATCHED;
    }

    /**
     * 결제 서버의 해당 일자 결제 합계를 구한다.
     * <p>
     * <b>취소·실패 건을 제외한다.</b> 원장에는 취소 시 상쇄 분개가 들어가 미지급금이 이미
     * 줄어 있으므로, 결제 쪽에서 취소 건을 안 빼면 그만큼 항상 초과로 나온다.
     *
     * @param date 대사 대상 일자
     * @return 승인 완료된 결제의 금액 합계. 없으면 0
     * @throws ExternalServiceException 결제 서버 호출이 실패했을 때
     */
    private BigDecimal fetchPaymentTotal(LocalDate date)
    {
        return paymentClient.findPaymentsByDate(date).stream()
                .filter(payment -> COMPLETED_PAYMENT_STATUS.equals(payment.status()))
                .map(PaymentSummary::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 원장의 해당 일자 미지급금 합계를 구한다.
     * <p>
     * ⚠️ 부호 검증은 {@link LedgerClient}가 끝낸 상태로 온다. 여기서 부호를 다시 만지면
     * 처리가 두 곳에 생기고, 규약이 바뀔 때 하나를 빠뜨린다.
     *
     * @param date 대사 대상 일자
     * @return 미지급금 합계. 대상이 없으면 0
     * @throws ExternalServiceException 원장 호출이 실패했을 때
     */
    private BigDecimal fetchLedgerTotal(LocalDate date)
    {
        return ledgerClient.findUnpaidDrivers(date).stream()
                .map(DriverUnpaid::totalUnpaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 두 금액이 같은지 비교한다.
     * <p>
     * ⚠️ <b>{@link BigDecimal#equals}를 쓰면 안 된다.</b> {@code equals}는 소수점 자릿수까지
     * 비교해서 {@code 16000}과 {@code 16000.00}을 다르다고 판정한다. DB에서 읽은 값과 API로
     * 받은 값은 scale이 다르기 마련이라, {@code equals}로 비교하면 <b>금액이 같은데도 항상
     * 불일치가 난다.</b>
     *
     * @param paymentTotal 결제 합계
     * @param ledgerTotal  원장 미지급 합계
     * @return 값이 같으면 {@code true}
     */
    private static boolean amountsMatch(BigDecimal paymentTotal, BigDecimal ledgerTotal)
    {
        return paymentTotal.compareTo(ledgerTotal) == 0;
    }

}
