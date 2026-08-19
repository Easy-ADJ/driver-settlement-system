package com.example.driversettlementsystem.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.driversettlementsystem.exception.InvalidStateTransitionException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SettlementBatch}의 불변식 — 상태는 {@code transitionTo}로만 바뀐다.
 */
class SettlementBatchTest
{

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 18);

    @DisplayName("start()는 RUNNING 상태로 시작하고 실행 시각을 남긴다")
    @Test
    void startsAsRunning()
    {
        SettlementBatch batch = SettlementBatch.start(TARGET_DATE);

        assertThat(batch.getTargetDate()).isEqualTo(TARGET_DATE);
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.RUNNING);
        assertThat(batch.getExecutedAt()).isNotNull();
    }

    @DisplayName("집계 전이라 금액·대사 결과·확정 시각은 비어 있다")
    @Test
    void startsWithoutAggregatedValues()
    {
        SettlementBatch batch = SettlementBatch.start(TARGET_DATE);

        assertThat(batch.getTotalPayoutAmount()).isNull();
        assertThat(batch.getReconciliationStatus()).isNull();
        assertThat(batch.getConfirmedAt()).isNull();
    }

    @DisplayName("CONFIRMED로 전이하면 확정 시각이 채워진다")
    @Test
    void recordsConfirmedAtOnConfirm()
    {
        SettlementBatch batch = SettlementBatch.start(TARGET_DATE);

        batch.transitionTo(BatchStatus.CONFIRMED);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.CONFIRMED);
        assertThat(batch.getConfirmedAt()).isNotNull();
    }

    @DisplayName("CONFIRMED가 아닌 전이는 확정 시각을 건드리지 않는다")
    @Test
    void doesNotRecordConfirmedAtOnOtherTransitions()
    {
        SettlementBatch batch = SettlementBatch.start(TARGET_DATE);

        batch.transitionTo(BatchStatus.FAILED);

        assertThat(batch.getConfirmedAt()).isNull();
    }

    @DisplayName("정의되지 않은 전이는 거부하고 상태를 바꾸지 않는다")
    @Test
    void rejectsUndefinedTransition()
    {
        SettlementBatch batch = SettlementBatch.start(TARGET_DATE);

        assertThatThrownBy(() -> batch.transitionTo(BatchStatus.PAID))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.RUNNING);
    }

    @DisplayName("기본 생성자는 protected다 — JPA만 쓴다")
    @Test
    void defaultConstructorIsProtected() throws NoSuchMethodException
    {
        Constructor<SettlementBatch> constructor = SettlementBatch.class.getDeclaredConstructor();

        assertThat(Modifier.isProtected(constructor.getModifiers())).isTrue();
    }

    /**
     * setter가 하나라도 열리면 {@code transitionTo}의 전이 검증을 우회할 수 있다.
     * 검증을 뚫는 경로가 생기는 순간 "중복 없이 한 번만"이 무너진다.
     */
    @DisplayName("public setter가 없다")
    @Test
    void exposesNoPublicSetters()
    {
        assertThat(Arrays.stream(SettlementBatch.class.getMethods())
                .map(method -> method.getName())
                .filter(name -> name.startsWith("set")))
                .isEmpty();
    }

}
