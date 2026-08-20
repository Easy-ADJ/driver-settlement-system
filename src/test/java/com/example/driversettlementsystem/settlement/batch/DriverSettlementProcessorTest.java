package com.example.driversettlementsystem.settlement.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.driversettlementsystem.settlement.client.DriverUnpaid;
import com.example.driversettlementsystem.settlement.domain.PayoutStatus;
import com.example.driversettlementsystem.settlement.domain.Settlement;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 정산 서버가 하는 <b>계산의 전부</b>를 검증한다.
 * <p>
 * 절사 규칙은 팀 공통 규약이다 — 원 단위 <b>버림</b>, 기사 단위 합계에 <b>한 번만</b>.
 * ({@code service-contracts.md} §2) 세 서버가 같은 규칙을 쓰지 않으면 계산이 전부 정상인데도
 * 대사가 1원 단위로 실패하고, 기사가 1,000명이면 1,000원이 어긋난다.
 */
class DriverSettlementProcessorTest
{

    private static final BigDecimal FEE_RATE = new BigDecimal("0.20");

    private static final Long BATCH_ID = 42L;

    private final DriverSettlementProcessor processor =
            new DriverSettlementProcessor(FEE_RATE, BATCH_ID);

    /**
     * 이슈의 검증용 계산 예시를 그대로 옮겼다. 두 번째 행이 절사 규칙을 실제로 태우는
     * 유일한 행이다 — 20,000원은 나누어떨어져서 어떤 규칙을 써도 같은 답이 나온다.
     */
    @DisplayName("수수료 20%를 버림으로 떼고 나머지를 지급액으로 삼는다")
    @ParameterizedTest(name = "미지급금 {0} → 수수료 {1} / 지급액 {2}")
    @CsvSource({
            "20000, 4000, 16000",
            "3333,   666,  2667",
            "1,        0,     1",
            "5,        1,     4"
    })
    void deductsFeeWithFloorRounding(String unpaidAmount, String expectedFee, String expectedPayout)
    {
        Settlement settlement = processor.process(driverWith(unpaidAmount));

        assertThat(settlement.getFeeAmount()).isEqualByComparingTo(new BigDecimal(expectedFee));
        assertThat(settlement.getAmount()).isEqualByComparingTo(new BigDecimal(expectedPayout));
    }

    /**
     * <b>이 불변식이 깨지면 조회 응답이 거짓말을 시작한다.</b> 수수료를 먼저 절사하고
     * 지급액을 뺄셈으로 구하기 때문에 성립한다 — {@code payout = floor(fare × 0.8)}로
     * 계산하면 1원이 공중에 뜬다.
     */
    @DisplayName("fareTotal - feeAmount == amount 가 항상 성립한다")
    @ParameterizedTest(name = "미지급금 {0}")
    @CsvSource({"20000", "3333", "9999", "1", "7", "123456789"})
    void keepsAmountConsistent(String unpaidAmount)
    {
        Settlement settlement = processor.process(driverWith(unpaidAmount));

        assertThat(settlement.getFareTotal().subtract(settlement.getFeeAmount()))
                .isEqualByComparingTo(settlement.getAmount());
    }

    @DisplayName("만들어진 항목이 현재 배치와 기사에 연결된다")
    @Test
    void linksBatchAndDriver()
    {
        Settlement settlement = processor.process(driverWith("20000"));

        assertThat(settlement.getBatchId()).isEqualTo(BATCH_ID);
        assertThat(settlement.getDriverId()).isEqualTo(1L);
        assertThat(settlement.getPayoutStatus()).isEqualTo(PayoutStatus.CONFIRMED);
        assertThat(settlement.getLedgerId()).isNull();
    }

    /**
     * 0원짜리 빈 항목을 만드는 것보다 낫다. Spring Batch는 Processor가 {@code null}을 주면
     * 그 항목을 Writer에 넘기지 않는다.
     */
    @DisplayName("미지급금이 0 이하인 기사는 항목을 만들지 않는다")
    @ParameterizedTest(name = "미지급금 {0}")
    @CsvSource({"0", "-1", "-15000"})
    void skipsDriversWithNothingToSettle(String unpaidAmount)
    {
        assertThat(processor.process(driverWith(unpaidAmount))).isNull();
    }

    /**
     * 수수료율을 프로퍼티로 뺐다는 것은 <b>언젠가 바뀐다는 뜻</b>이다. 바뀐 값이 실제로
     * 계산에 쓰이는지 확인한다 — 상수를 그대로 두고 프로퍼티만 읽는 실수가 흔하다.
     */
    @DisplayName("수수료율은 주입된 값을 쓴다 — 하드코딩이 아니다")
    @Test
    void usesInjectedFeeRate()
    {
        DriverSettlementProcessor tenPercent =
                new DriverSettlementProcessor(new BigDecimal("0.10"), BATCH_ID);

        Settlement settlement = tenPercent.process(driverWith("20000"));

        assertThat(settlement.getFeeAmount()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(settlement.getAmount()).isEqualByComparingTo(new BigDecimal("18000"));
    }

    private static DriverUnpaid driverWith(String unpaidAmount)
    {
        return new DriverUnpaid(1L, new BigDecimal(unpaidAmount), Instant.parse("2026-08-19T14:30:00Z"));
    }

}
