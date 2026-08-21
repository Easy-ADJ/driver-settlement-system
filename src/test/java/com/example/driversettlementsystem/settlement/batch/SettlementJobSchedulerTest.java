package com.example.driversettlementsystem.settlement.batch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.driversettlementsystem.exception.DuplicateSettlementException;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.dto.BatchRunResponse;
import com.example.driversettlementsystem.settlement.service.SettlementJobRunner;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 스케줄러가 정하는 것은 <b>어느 날짜를 정산하느냐</b>와 <b>실패했을 때 스케줄이 살아남느냐</b>
 * 둘뿐이다. 나머지(중복 판정·상태 전이)는 {@link SettlementJobRunner}가 하고 그쪽 테스트가 지킨다.
 */
class SettlementJobSchedulerTest
{

    /**
     * UTC로는 8/20이지만 <b>서울로는 이미 8/21 새벽 3시</b>인 시각.
     * <p>
     * 이 시각을 고른 이유가 있다. 시간대를 서울로 고정하지 않고 시스템 기본값이나 UTC로
     * 날짜를 구하면 여기서 <b>하루가 어긋난다</b> — 8/20을 정산해야 하는데 8/19를 정산한다.
     */
    private static final Instant SEOUL_EARLY_MORNING = Instant.parse("2026-08-20T18:00:00Z");

    private static final LocalDate YESTERDAY_IN_SEOUL = LocalDate.of(2026, 8, 20);

    private final SettlementJobRunner jobRunner = mock(SettlementJobRunner.class);

    private final SettlementJobScheduler scheduler = new SettlementJobScheduler(
            jobRunner,
            Clock.fixed(SEOUL_EARLY_MORNING, ZoneId.of(SettlementJobScheduler.SETTLEMENT_ZONE)));

    @DisplayName("오늘이 아니라 전일을, 서울 기준으로 정산한다")
    @Test
    void runsPreviousDayInSeoulTime()
    {
        when(jobRunner.run(any())).thenReturn(
                new BatchRunResponse(7L, YESTERDAY_IN_SEOUL, BatchStatus.RUNNING, 2));

        scheduler.runDailySettlement();

        verify(jobRunner).run(YESTERDAY_IN_SEOUL);
    }

    /**
     * <b>스케줄 메서드에서 예외가 새어 나가면 그 스케줄이 멈출 수 있다.</b> 어제 배치가
     * 중복으로 거부된 것 때문에 내일 배치까지 영영 안 도는 일은 없어야 한다.
     */
    @DisplayName("실행이 실패해도 예외가 밖으로 나가지 않는다")
    @Test
    void swallowsFailureSoTomorrowStillRuns()
    {
        when(jobRunner.run(YESTERDAY_IN_SEOUL))
                .thenThrow(new DuplicateSettlementException(YESTERDAY_IN_SEOUL));

        assertThatCode(scheduler::runDailySettlement).doesNotThrowAnyException();
    }

}
