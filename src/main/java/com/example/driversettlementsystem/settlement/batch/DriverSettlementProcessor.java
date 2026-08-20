package com.example.driversettlementsystem.settlement.batch;

import com.example.driversettlementsystem.settlement.client.DriverUnpaid;
import com.example.driversettlementsystem.settlement.domain.Settlement;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 기사 1명의 미지급금을 정산 항목으로 변환한다.
 * <p>
 * <b>정산 서버의 계산 로직은 전부 여기 있다.</b> 원장이 준 미지급금에서 수수료를 떼는 것이
 * 전부이고, 나머지 클래스는 이걸 실어 나르는 배관이다.
 * <p>
 * 이전 설계에서는 결제 목록을 받아 상태 필터링·합산까지 했지만, 운임 합계의 출처가 원장으로
 * 바뀌면서 그 두 단계가 사라졌다. <b>대신 원장 값을 그대로 신뢰한다</b> — 그 신뢰가 맞는지
 * 확인하는 것이 대사(`ReconciliationService`)다.
 */
@Component
@StepScope
public class DriverSettlementProcessor implements ItemProcessor<DriverUnpaid, Settlement>
{

    /** 금액을 원 단위로 맞출 때 쓰는 소수 자릿수. 원화는 소수점이 없다. */
    private static final int WON_SCALE = 0;

    /**
     * 절사 방식. <b>버림</b>이며 팀 공통 규약이다.
     * <p>
     * 회사가 떼는 금액이 줄어드는 방향이라 기사와의 분쟁이 적다. 세 서버가 같은 규칙을
     * 쓰지 않으면 계산이 전부 정상인데도 대사가 1원 단위로 실패한다.
     */
    private static final RoundingMode ROUNDING = RoundingMode.FLOOR;

    /**
     * 수수료율. ({@code settlement.fee-rate})
     * <p>
     * <b>하드코딩하지 않는다.</b> 그리고 이 값이 바뀌어도 과거 정산을 설명할 수 있어야
     * 하므로, {@link Settlement}에 {@code feeAmount}를 <b>계산식이 아니라 값으로</b>
     * 저장한다. 응답 시점에 재계산하면 요율이 바뀐 뒤 과거 정산이 조용히 다른 금액으로
     * 보이기 시작한다.
     */
    private final BigDecimal feeRate;

    /**
     * 이 Step이 처리 중인 배치의 ID.
     * <p>
     * {@link SettlementJobListener}가 Job 시작 시 만들어 {@code ExecutionContext}에 넣어 둔
     * 값이다. {@code Settlement.of()}가 생성 시점에 {@code batchId}를 요구하므로 <b>그 값을
     * 아는 쪽이 이 클래스여야 한다</b> — 엔티티에 setter가 없는 것은 반쯤 만들어진 정산
     * 레코드를 막기 위한 의도된 설계다.
     */
    private final Long batchId;

    public DriverSettlementProcessor(
            @Value("${settlement.fee-rate}") BigDecimal feeRate,
            @Value("#{jobExecutionContext['" + SettlementJobListener.BATCH_ID_KEY + "']}") Long batchId)
    {
        this.feeRate = feeRate;
        this.batchId = batchId;
    }

    /**
     * 기사 1명분을 정산 항목으로 변환한다.
     *
     * @param unpaid 원장이 준 기사 1명의 미지급금
     * @return 정산 항목. <b>미지급금이 0 이하면 {@code null}</b> — Spring Batch는 Processor가
     *         null을 주면 그 항목을 Writer에 넘기지 않는다. 0원짜리 빈 항목을 만드는 것보다
     *         낫다
     */
    @Override
    public Settlement process(DriverUnpaid unpaid)
    {
        BigDecimal fareTotal = unpaid.totalUnpaidAmount();

        if (fareTotal.signum() <= 0)
        {
            return null;
        }

        return Settlement.of(batchId, unpaid.driverId(), fareTotal, calculateFee(fareTotal));
    }

    /**
     * 미지급금에서 수수료를 계산한다. 원 단위 <b>버림</b>이다.
     * <p>
     * <b>수수료를 먼저 절사하고 지급액을 뺄셈으로 구하는 순서가 중요하다.</b>
     * {@code payout = floor(fare × 0.8)}로 계산하면 {@code fee + payout != fare}가 되어 1원이
     * 공중에 뜬다. 지금 방식이면 합이 항상 원본과 맞는다 — 지급액은
     * {@code Settlement.of()}가 {@code fareTotal - feeAmount}로 채운다.
     * <p>
     * <b>절사는 여기 한 번뿐이다.</b> 원장이 기사 단위 합계를 주므로 결제 건마다 절사할 일이
     * 없다. 건마다 절사하면 건수만큼 오차가 쌓인다.
     *
     * @param fareTotal 수수료 차감 전 운임 합계
     * @return 원 단위로 버림한 수수료
     */
    private BigDecimal calculateFee(BigDecimal fareTotal)
    {
        return fareTotal.multiply(feeRate).setScale(WON_SCALE, ROUNDING);
    }

}
