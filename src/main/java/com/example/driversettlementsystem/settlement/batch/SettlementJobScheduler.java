package com.example.driversettlementsystem.settlement.batch;

import com.example.driversettlementsystem.settlement.dto.BatchRunResponse;
import com.example.driversettlementsystem.settlement.service.SettlementJobRunner;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 정해진 시각에 전일 정산 Job을 실행한다.
 * <p>
 * <b>이 클래스는 시각과 날짜만 정한다.</b> 중복 판정도 상태 전이도 여기서 하지 않는다 —
 * 전부 {@link SettlementJobRunner}가 하고, 수동 실행 API도 같은 메서드를 부른다. 스케줄러가
 * 판단까지 하기 시작하면 두 경로의 규칙이 갈라지고, <b>갈라진 쪽은 시연 당일에야 발견된다.</b>
 * <p>
 * ⚠️ <b>서버가 잠들어 있으면 그 시각의 배치는 아예 돌지 않는다.</b> Railway는 유휴 시
 * 인스턴스를 재우고, {@code @Scheduled}는 놓친 실행을 나중에 따라잡지 않는다. 그래서 날짜를
 * 지정해 메우는 경로를 따로 열어 두었다(#38).
 *
 * @see SettlementJobRunner 수동 실행과 공유하는 실행 경로
 */
@Component
public class SettlementJobScheduler
{

    /**
     * 정산 기준 시간대. {@code @Scheduled}의 {@code zone}과 <b>같은 값이어야 한다</b> —
     * 다르면 "새벽 3시에 깨어나서 그저께를 정산하는" 식으로 하루가 어긋난다.
     */
    static final String SETTLEMENT_ZONE = "Asia/Seoul";

    private static final Logger log = LoggerFactory.getLogger(SettlementJobScheduler.class);

    private final SettlementJobRunner jobRunner;

    private final Clock clock;

    @Autowired
    public SettlementJobScheduler(SettlementJobRunner jobRunner)
    {
        this(jobRunner, Clock.system(ZoneId.of(SETTLEMENT_ZONE)));
    }

    /**
     * 테스트가 "어제"를 고정하기 위한 생성자. 운영에서는 위 생성자만 쓰인다.
     */
    SettlementJobScheduler(SettlementJobRunner jobRunner, Clock clock)
    {
        this.jobRunner = jobRunner;
        this.clock = clock;
    }

    /**
     * 매일 정해진 시각에 <b>전일</b> 정산을 실행한다.
     * <p>
     * 대상이 오늘이 아니라 어제인 이유: 오늘 결제는 아직 발생 중이라 지금 집계하면 하루가
     * 잘린다. 하루가 완전히 닫힌 뒤에 정산한다.
     * <p>
     * <b>예외를 밖으로 던지지 않는다.</b> 스케줄 메서드에서 예외가 새어 나가면 그 스케줄이
     * 더 이상 실행되지 않을 수 있다 — 어제 배치가 중복으로 거부된 것 때문에 <b>내일 배치까지
     * 영영 안 도는 일</b>은 없어야 한다.
     * <p>
     * cron 식은 프로퍼티로 주입한다. 개발 중에는 자주 돌려보고 싶고 운영에서는 새벽에 한 번이면
     * 되는데, 그 차이 때문에 코드를 고칠 이유가 없다.
     */
    @Scheduled(cron = "${settlement.batch.cron}", zone = SETTLEMENT_ZONE)
    public void runDailySettlement()
    {
        LocalDate targetDate = LocalDate.now(clock).minusDays(1);

        log.info("정산 배치 스케줄 시작 — targetDate={}", targetDate);

        try
        {
            BatchRunResponse result = jobRunner.run(targetDate);

            log.info("정산 배치 스케줄 완료 — targetDate={}, batchId={}, 정산 건수={}",
                    targetDate, result.batchId(), result.settlementCount());
        }
        catch (Exception e)
        {
            log.error("정산 배치 스케줄 실패 — targetDate={}. 스케줄 자체는 살아 있으므로 내일도 실행된다",
                    targetDate, e);
        }
    }

}
