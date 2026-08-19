package com.example.driversettlementsystem.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Settlement}의 불변식 — {@code amount}는 언제나 {@code fareTotal - feeAmount}다.
 */
class SettlementTest
{

    private static final Long BATCH_ID = 1L;

    private static final Long DRIVER_ID = 7L;

    @DisplayName("amount를 인자로 받지 않고 운임에서 수수료를 빼서 계산한다")
    @Test
    void computesAmountFromFareAndFee()
    {
        Settlement settlement = Settlement.of(BATCH_ID, DRIVER_ID,
                new BigDecimal("42000"), new BigDecimal("8400"));

        assertThat(settlement.getAmount()).isEqualByComparingTo("33600");
        assertThat(settlement.getFareTotal()).isEqualByComparingTo("42000");
        assertThat(settlement.getFeeAmount()).isEqualByComparingTo("8400");
    }

    @DisplayName("계산 근거를 따로 남긴다 — 수수료율이 바뀌어도 과거 정산을 설명할 수 있다")
    @Test
    void keepsFareAndFeeSeparately()
    {
        Settlement settlement = Settlement.of(BATCH_ID, DRIVER_ID,
                new BigDecimal("42000"), new BigDecimal("8400"));

        assertThat(settlement.getFareTotal().subtract(settlement.getFeeAmount()))
                .isEqualByComparingTo(settlement.getAmount());
    }

    @DisplayName("생성 직후에는 CONFIRMED이고 지급 분개는 아직 없다")
    @Test
    void startsConfirmedWithoutLedgerEntry()
    {
        Settlement settlement = Settlement.of(BATCH_ID, DRIVER_ID,
                new BigDecimal("42000"), new BigDecimal("8400"));

        assertThat(settlement.getPayoutStatus()).isEqualTo(PayoutStatus.CONFIRMED);
        assertThat(settlement.getLedgerId()).isNull();
    }

    @DisplayName("지급 분개를 남기면 원장 ID가 채워진다")
    @Test
    void linksLedgerEntry()
    {
        Settlement settlement = Settlement.of(BATCH_ID, DRIVER_ID,
                new BigDecimal("42000"), new BigDecimal("8400"));

        settlement.linkLedgerEntry(9001L);

        assertThat(settlement.getLedgerId()).isEqualTo(9001L);
    }

    @DisplayName("금액 필드는 전부 BigDecimal이다")
    @Test
    void storesMoneyAsBigDecimal()
    {
        Set<String> moneyFields = Set.of("fareTotal", "feeAmount", "amount");

        assertThat(Arrays.stream(Settlement.class.getDeclaredFields())
                .filter(field -> moneyFields.contains(field.getName()))
                .map(Field::getType))
                .hasSize(3)
                .containsOnly(BigDecimal.class);
    }

    @DisplayName("기본 생성자는 protected다 — JPA만 쓴다")
    @Test
    void defaultConstructorIsProtected() throws NoSuchMethodException
    {
        Constructor<Settlement> constructor = Settlement.class.getDeclaredConstructor();

        assertThat(Modifier.isProtected(constructor.getModifiers())).isTrue();
    }

    @DisplayName("public setter가 없다")
    @Test
    void exposesNoPublicSetters()
    {
        assertThat(Arrays.stream(Settlement.class.getMethods())
                .map(method -> method.getName())
                .filter(name -> name.startsWith("set")))
                .isEmpty();
    }

    /**
     * 복합키는 영속성 컨텍스트의 식별 기준이다. {@code equals}·{@code hashCode}가
     * 값 기준으로 동작하지 않으면 같은 행을 서로 다른 엔티티로 취급한다.
     */
    @DisplayName("복합키는 값이 같으면 같다")
    @Test
    void identifiesByValue()
    {
        assertThat(new SettlementId(BATCH_ID, DRIVER_ID))
                .isEqualTo(new SettlementId(BATCH_ID, DRIVER_ID))
                .hasSameHashCodeAs(new SettlementId(BATCH_ID, DRIVER_ID));
        assertThat(new SettlementId(BATCH_ID, DRIVER_ID))
                .isNotEqualTo(new SettlementId(BATCH_ID, 8L));
    }

}
