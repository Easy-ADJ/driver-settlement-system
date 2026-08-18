package com.example.driversettlementsystem.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.driversettlementsystem.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@code FR-S-01} 수락 기준 — 정의되지 않은 전이는 거부한다.
 * <p>
 * 허용·금지 목록은 FC-04 상태 전이 표와 1:1로 대응한다.
 */
class BatchStatusTest
{

    @DisplayName("허용된 전이는 통과한다")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "RUNNING, CONFIRMED",
            "RUNNING, FAILED",
            "CONFIRMED, PAID",
            "FAILED, RUNNING"
    })
    void allowsDefinedTransitions(BatchStatus from, BatchStatus to)
    {
        assertThatCode(() -> from.validateTransitionTo(to)).doesNotThrowAnyException();
    }

    @DisplayName("정의되지 않은 전이는 거부한다")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "RUNNING, PAID",
            "CONFIRMED, RUNNING",
            "CONFIRMED, FAILED",
            "PAID, RUNNING",
            "PAID, CONFIRMED",
            "PAID, FAILED",
            "FAILED, CONFIRMED",
            "FAILED, PAID"
    })
    void rejectsUndefinedTransitions(BatchStatus from, BatchStatus to)
    {
        assertThatThrownBy(() -> from.validateTransitionTo(to))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @DisplayName("같은 상태로의 전이도 거부한다")
    @ParameterizedTest(name = "{0} -> {0}")
    @EnumSource(BatchStatus.class)
    void rejectsSelfTransition(BatchStatus status)
    {
        assertThatThrownBy(() -> status.validateTransitionTo(status))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @DisplayName("null 로의 전이는 거부한다")
    @ParameterizedTest(name = "{0} -> null")
    @EnumSource(BatchStatus.class)
    void rejectsNullTarget(BatchStatus status)
    {
        assertThatThrownBy(() -> status.validateTransitionTo(null))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @DisplayName("거부된 전이의 예외에 from·to와 에러 코드가 담긴다")
    @Test
    void carriesTransitionDetail()
    {
        assertThatThrownBy(() -> BatchStatus.PAID.validateTransitionTo(BatchStatus.RUNNING))
                .isInstanceOfSatisfying(InvalidStateTransitionException.class, e ->
                {
                    assertThat(e.getFrom()).isEqualTo(BatchStatus.PAID);
                    assertThat(e.getTo()).isEqualTo(BatchStatus.RUNNING);
                    assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION");
                });
    }

}
