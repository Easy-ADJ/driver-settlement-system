package com.example.driversettlementsystem.settlement.batch;

import com.example.driversettlementsystem.exception.ExternalServiceException;
import com.example.driversettlementsystem.settlement.client.DriverUnpaid;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import java.time.LocalDate;
import java.util.Iterator;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 원장에서 미지급 기사를 선별해 <b>기사 1명분씩</b> 공급하는 Reader.
 * <p>
 * 첫 호출 때 {@code GET /api/ledger/unpaid?date=}로 대상 기사 목록을 통째로 받아 두고,
 * 이후 호출마다 그 목록에서 하나씩 꺼낸다. <b>원장 호출은 배치 전체에서 한 번뿐이다</b> —
 * 목록 응답이 금액까지 담고 있어 기사마다 되물을 것이 없다.
 * <p>
 * <b>왜 기사 1명씩인가</b> — 청크가 어디서 끊기든 기사 하나는 항상 온전해야 한다. 결제
 * 1건씩 읽던 이전 구조에서는 같은 기사의 결제가 청크 경계에서 갈려 한 기사에 정산이 두 줄
 * 생기고 절사가 두 번 적용됐다. 원장이 기사 단위로 답하는 지금은 그 문제가 구조적으로
 * 발생하지 않는다.
 * <p>
 * ⚠️ {@code LEDGER_ENTRIES}를 직접 읽지 않는다. 이 클래스에 {@code @Query}나 JDBC가
 * 등장하면 서비스 경계 위반이다 — DB가 나뉘어 애초에 불가능하지만, 그만큼 <b>API 계약이
 * 유일한 접점</b>이다.
 *
 * @see LedgerClient 유일한 데이터 출처
 */
@Component
@StepScope
public class UnpaidDriverReader implements ItemReader<DriverUnpaid>
{

    private final LedgerClient ledgerClient;

    /**
     * 정산 대상 일자.
     * <p>
     * {@code @StepScope} + SpEL로 Job 파라미터에서 주입받는다. 이 조합이 있어야 "실행할
     * 때마다 다른 날짜"를 받을 수 있다 — 빈 생성 시점이 아니라 Step 시작 시점에 값이
     * 정해지기 때문이다.
     */
    private final LocalDate targetDate;

    /** 아직 내보내지 않은 대상 기사. 첫 {@link #read()} 때 채워진다. */
    private Iterator<DriverUnpaid> pendingDrivers;

    public UnpaidDriverReader(LedgerClient ledgerClient,
                              @Value("#{jobParameters['targetDate']}") LocalDate targetDate)
    {
        this.ledgerClient = ledgerClient;
        this.targetDate = targetDate;
    }

    /**
     * 다음 기사 1명분을 반환한다.
     * <p>
     * 원장 조회는 첫 호출에서 한 번만 한다. 미지급 기사가 0명이면 첫 호출이 곧바로
     * {@code null}이 되어 Step은 아무것도 처리하지 않고 정상 종료한다 — <b>정산할 것이
     * 없는 날에 Job이 실패하면 안 된다.</b>
     *
     * @return 기사 1명의 미지급금. <b>더 읽을 것이 없으면 {@code null}</b> — Spring Batch는
     *         null을 "입력 끝"으로 해석해 Step을 정상 종료한다. 빈 리스트를 반환하면 무한
     *         루프가 된다
     * @throws ExternalServiceException 원장 호출이 재시도 후에도 실패했을 때. 잡지 않고
     *                                  그대로 올려 Job을 실패시킨다 — 대상을 못 읽은 채
     *                                  "성공"으로 끝나면 그날 정산이 통째로 비는데도
     *                                  아무도 모른다
     */
    @Override
    public DriverUnpaid read()
    {
        if (pendingDrivers == null)
        {
            pendingDrivers = ledgerClient.findUnpaidDrivers(targetDate).iterator();
        }

        if (!pendingDrivers.hasNext())
        {
            return null;
        }

        return pendingDrivers.next();
    }

}
