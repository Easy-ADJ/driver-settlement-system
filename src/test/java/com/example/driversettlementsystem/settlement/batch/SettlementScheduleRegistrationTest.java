package com.example.driversettlementsystem.settlement.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.driversettlementsystem.TestcontainersConfiguration;
import com.example.driversettlementsystem.auth.AuthDataSourceTestConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/**
 * <b>{@code @EnableScheduling}을 빠뜨리면 {@code @Scheduled}는 조용히 무시된다.</b> 예외도
 * 경고도 없이 그냥 아무 일도 일어나지 않고, 그 사실은 <b>정산이 안 된 다음날에야</b> 드러난다.
 * <p>
 * 그래서 "등록되었는가"를 켜져 있는 컨텍스트에 직접 묻는다. cron 식을 프로퍼티로 덮어쓴 뒤
 * 그 값이 등록되는지 보므로 <b>시각이 하드코딩되지 않았다는 것도 같이 확인된다</b> —
 * 하드코딩이라면 덮어쓴 값이 나오지 않는다.
 */
@Import({TestcontainersConfiguration.class, AuthDataSourceTestConfiguration.class})
@SpringBootTest(properties = {
        "settlement.client.payment.base-url=http://payment.test",
        "settlement.client.ledger.base-url=http://ledger.test",
        "settlement.batch.cron=" + SettlementScheduleRegistrationTest.OVERRIDDEN_CRON})
class SettlementScheduleRegistrationTest
{

    /** 기본값(새벽 3시)과 다른 값이어야 의미가 있다. 같으면 덮어쓰기가 됐는지 알 수 없다. */
    static final String OVERRIDDEN_CRON = "0 15 4 * * *";

    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;

    @DisplayName("정산 배치가 프로퍼티의 cron 식으로 실제 등록된다")
    @Test
    void registersDailySettlementWithInjectedCron()
    {
        assertThat(scheduledTaskHolder.getScheduledTasks())
                .map(ScheduledTask::getTask)
                .filteredOn(CronTask.class::isInstance)
                .map(task -> ((CronTask) task).getExpression())
                .contains(OVERRIDDEN_CRON);
    }

}
