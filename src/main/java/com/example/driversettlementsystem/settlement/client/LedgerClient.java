package com.example.driversettlementsystem.settlement.client;

import com.example.driversettlementsystem.exception.ExternalServiceException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 원장 서버 호출을 담당하는 유일한 클래스.
 * <p>
 * <b>정산이 계산하는 모든 금액이 이 클래스를 통과한다.</b> 운임 합계의 출처가 원장이기
 * 때문이다. 정산 서버에서 가장 크게 의존하는 지점이다.
 * <p>
 * 원장의 설계 원칙은 "잔액을 저장하지 않고 분개의 합으로 항상 재계산한다"이다. 정산이
 * {@code LEDGER_ENTRIES}를 직접 SUM하면 원장이 계산 규칙을 바꿀 때 <b>정산만 조용히 틀린
 * 값을 갖게 된다.</b> 반드시 API를 경유한다.
 * <p>
 * ⚠️ <b>미지급금 부호 규약은 이 클래스에만 나타난다.</b> 배치나 대사에 부호 처리가
 * 흩어지면 규약이 바뀔 때 여러 파일을 고쳐야 하고, 하나를 빠뜨리면 조용히 틀린다.
 * <p>
 * 🚧 지급 상쇄 분개({@code POST /api/ledger/entries})는 여기 없다. 원장의 현재 구현이
 * {@code entries}의 첫 항목만 읽고 {@code entryType}·{@code direction}을 무시해서, 그대로
 * 부르면 미지급금이 상쇄되는 게 아니라 늘어날 수 있다. 형태 확정 후 별도 이슈에서 붙인다.
 */
@Component
public class LedgerClient
{

    /** 재시도 횟수. 팀 규약은 5xx·타임아웃만 최대 2회다. */
    static final int MAX_RETRIES = 2;

    /** 첫 재시도까지 기다리는 시간. 이후 {@link #BACKOFF_MULTIPLIER}배씩 늘어난다. */
    static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);

    /** 지수 백오프 배수. */
    static final double BACKOFF_MULTIPLIER = 2.0;

    private final RestClient restClient;

    private final RetryTemplate retryTemplate;

    public LedgerClient(@Qualifier("ledgerRestClient") RestClient restClient)
    {
        this.restClient = restClient;
        this.retryTemplate = new RetryTemplate(retryPolicy());
    }

    /**
     * 재시도 정책. <b>무엇을 재시도하는지가 선언 하나로 남는다.</b>
     * <p>
     * 5xx({@link HttpServerErrorException})와 타임아웃({@link ResourceAccessException})만
     * 재시도한다. <b>4xx는 목록에 없어 재시도되지 않는다</b> — 잘못된 요청을 두 번 더
     * 보내도 답은 같고, 그동안 배치만 늦어진다.
     *
     * @return 5xx·타임아웃만 2회 지수 백오프하는 정책
     */
    static RetryPolicy retryPolicy()
    {
        return RetryPolicy.builder()
                .maxRetries(MAX_RETRIES)
                .delay(INITIAL_BACKOFF)
                .multiplier(BACKOFF_MULTIPLIER)
                .includes(HttpServerErrorException.class, ResourceAccessException.class)
                .build();
    }

    /**
     * 정산 대상 기사를 한 번에 선별한다. ({@code GET /api/ledger/unpaid?date=})
     * <p>
     * <b>기사마다 부르지 않는다.</b> 원장이 목록에 금액까지 담아 주므로 이 호출 하나로
     * 배치 입력이 완성된다. ID만 받아 되물으면 기사 100명에 호출이 101번이 된다.
     * <p>
     * {@code date}는 <b>그 날짜 기준 누적 미지급 잔액</b>이며 결제 승인 시각을 기준으로 한다.
     * 결제 {@code GET /api/payments?date=}와 같은 기준이라 대사에서 짝이 맞는다.
     *
     * @param date 정산 대상 일자
     * @return 미지급금이 있는 기사 목록. 없으면 빈 목록 ({@code null}이 아니다)
     * @throws ExternalServiceException 재시도 후에도 실패했거나 부호 규약이 어긋났을 때
     */
    public List<DriverUnpaid> findUnpaidDrivers(LocalDate date)
    {
        UnpaidListResponse response = call(
                () -> restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/ledger/unpaid")
                                .queryParam("date", date)
                                .build())
                        .retrieve()
                        .body(UnpaidListResponse.class),
                date + " 미지급 기사 목록 조회");

        if (response == null || response.data() == null)
        {
            return List.of();
        }

        response.data().forEach(driver -> verifyNotNegative(driver.driverId(), driver.totalUnpaidAmount()));

        return response.data();
    }

    /**
     * 기사 1명의 미지급금과 결제 건별 근거를 가져온다. ({@code GET /api/ledger?driver_id=})
     * <p>
     * <b>배치는 이걸 부르지 않는다.</b> 대상 선별에 필요한 금액은 목록 응답에 이미 있다.
     * 이 메서드는 정산 내역서를 조립할 때 결제 건별 근거가 필요해서 쓴다.
     *
     * @param driverId 기사 ID
     * @return 미지급금 합계와 결제 건별 내역
     * @throws ExternalServiceException 재시도 후에도 실패했거나 부호 규약이 어긋났을 때
     */
    public DriverLedger findDriverLedger(Long driverId)
    {
        DriverLedger ledger = call(
                () -> restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/ledger")
                                .queryParam("driver_id", driverId)
                                .build())
                        .retrieve()
                        .body(DriverLedger.class),
                "driverId=" + driverId + " 미지급 내역 조회");

        if (ledger == null)
        {
            throw ExternalServiceException.ledger("driverId=" + driverId + " 응답 본문이 비어 있다", null);
        }

        verifyNotNegative(driverId, ledger.totalUnpaidAmount());

        if (ledger.paymentDetails() == null)
        {
            return new DriverLedger(ledger.driverId(), ledger.totalUnpaidAmount(), List.of());
        }

        return ledger;
    }

    /**
     * 재시도를 태우고, 소진되면 공통 에러 포맷으로 바꾼다.
     *
     * @param action      실제 호출
     * @param description 실패 로그에 남길 설명
     * @param <T>         응답 타입
     * @return 호출 결과
     * @throws ExternalServiceException 재시도를 소진했거나 재시도 대상이 아닌 실패일 때
     */
    private <T> T call(Retryable<T> action, String description)
    {
        try
        {
            return retryTemplate.execute(action);
        }
        catch (RetryException e)
        {
            throw ExternalServiceException.ledger(description + " 실패", e.getLastException());
        }
    }

    /**
     * 미지급금이 양수인지 확인한다. <b>부호 규약이 나타나는 유일한 곳이다.</b>
     * <p>
     * 원장이 응답 DTO에서 절댓값으로 변환하기로 확정했으므로 음수는 오지 않아야 한다.
     * 그래도 막아 두는 이유는 <b>이것이 가장 조용히 틀리는 종류</b>이기 때문이다 — 부호가
     * 뒤집히면 계산은 전부 정상인데 대사만 항상 불일치로 뜨고, 버그처럼 보이지 않아 원인을
     * 찾는 데 오래 걸린다. 틀린 값으로 정산을 확정하느니 여기서 멈추는 편이 싸다.
     *
     * @param driverId 기사 ID. 어느 기사에서 어긋났는지 로그에 남긴다
     * @param amount   원장이 준 미지급금
     * @throws ExternalServiceException 값이 없거나 음수일 때
     */
    private static void verifyNotNegative(Long driverId, BigDecimal amount)
    {
        if (amount == null)
        {
            throw ExternalServiceException.ledger("driverId=" + driverId + " 미지급금이 비어 있다", null);
        }

        if (amount.signum() < 0)
        {
            throw ExternalServiceException.ledger(
                    "driverId=" + driverId + " 미지급금이 " + amount + " — 규약은 양수다", null);
        }
    }

    /**
     * {@code /unpaid} 응답의 바깥 껍데기.
     *
     * @param targetDate 조회 기준 일자. 정산은 요청한 날짜를 이미 알고 있어 쓰지 않는다
     * @param data       미지급 기사 목록
     */
    private record UnpaidListResponse(String targetDate, List<DriverUnpaid> data)
    {
    }

}
