package com.example.driversettlementsystem.settlement.client;

import com.example.driversettlementsystem.exception.ExternalServiceException;
import com.fasterxml.jackson.annotation.JsonFormat;
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
 * ⚠️ <b>{@link #recordPayoutEntry}를 빠뜨리면 다음날 배치가 같은 금액을 또 정산한다.</b>
 * 그리고 당일 정산 결과만 보면 금액이 완벽하게 맞아서 눈에 띄지 않는다.
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

    /** 지급 상쇄 분개의 유형. 원장이 결제 분개({@code PAYMENT})와 구분하는 값이다. */
    static final String PAYOUT_ENTRY_TYPE = "PAYOUT";

    /** 미지급금을 줄이는 방향. 결제가 대변으로 쌓았으니 상쇄는 차변이다. */
    static final String DEBIT = "DEBIT";

    /** 상쇄의 반대편(현금 유출). 차변합과 대변합을 맞추기 위해 반드시 함께 보낸다. */
    static final String CREDIT = "CREDIT";

    /**
     * 기사 미지급금 계정에 걸리는 leg. <b>이 값이 붙은 leg만 원장이 기사 잔액에 반영한다.</b>
     * <p>
     * ⚠️ 빠뜨리면 원장이 두 leg 모두 {@code driver_id = null}로 저장해 <b>미지급금이 전혀
     * 움직이지 않는다.</b> 요청은 201로 성공하고 분개도 쌓이는데 잔액만 그대로다.
     */
    static final String DRIVER_OWNER = "DRIVER";

    /** 상대편(현금·수수료) leg. 기사 잔액에 잡히면 안 되므로 기사 쪽과 반드시 구분한다. */
    static final String PLATFORM_OWNER = "PLATFORM";

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
     * 정산 확정에 따른 지급 상쇄 분개를 원장에 기록한다. ({@code POST /api/ledger/entries})
     * <p>
     * <b>이 호출이 빠지면 미지급금이 줄지 않아 다음날 배치가 같은 금액을 다시 정산한다.</b>
     * "중복 없이 한 번만"이 이 프로젝트 목표의 절반인데, 여기가 그 절반이 깨지는 자리다.
     * 게다가 조용히 깨진다 — 당일 정산 결과만 보면 금액이 정확하다.
     * <p>
     * 상쇄 금액은 <b>지급액이 아니라 운임 합계</b>다. 42,000원 중 33,600원을 지급했다고
     * 33,600원만 상쇄하면 수수료 8,400원이 미지급금으로 남아 <b>다음날 배치가 이 기사를 또
     * 선별한다.</b> 그러면 정산 대상 선별이 영원히 끝나지 않는다.
     * <p>
     * ⚠️ 재시도가 실제로 일어나므로 <b>멱등 키가 재계산 가능한 값이어야 한다.</b>
     * {@link #payoutIdempotencyKey}가 {@code batchId}와 {@code driverId}만으로 키를 만드는
     * 이유다 — {@code UUID.randomUUID()}를 쓰면 재시도마다 키가 달라져 멱등성이 무의미해진다.
     *
     * @param batchId   배치 ID. 멱등 키 구성에 쓴다
     * @param driverId  기사 ID
     * @param fareTotal 상쇄할 미지급금. <b>지급액이 아니라 운임 합계다</b>
     * @return 기록된 분개의 원장 ID
     * @throws ExternalServiceException 재시도 후에도 실패했거나 응답에 원장 ID가 없을 때
     */
    public Long recordPayoutEntry(Long batchId, Long driverId, BigDecimal fareTotal)
    {
        String idempotencyKey = payoutIdempotencyKey(batchId, driverId);

        PayoutEntryResponse response = call(
                () -> restClient.post()
                        .uri("/api/ledger/entries")
                        .header("Idempotency-Key", idempotencyKey)
                        .body(payoutRequest(idempotencyKey, driverId, fareTotal))
                        .retrieve()
                        .body(PayoutEntryResponse.class),
                "batchId=" + batchId + " driverId=" + driverId + " 지급 상쇄 분개 기록");

        if (response == null || response.ledgerId() == null)
        {
            throw ExternalServiceException.ledger(
                    "batchId=" + batchId + " driverId=" + driverId + " 응답에 원장 ID가 없다", null);
        }

        return response.ledgerId();
    }

    /**
     * 지급 상쇄의 멱등 키를 만든다.
     * <p>
     * <b>같은 배치의 같은 기사에 대한 재시도는 반드시 같은 키를 만들어야 한다.</b> 배치가
     * 다르면 다른 키가 되므로 다음날 정산은 막히지 않는다.
     * <p>
     * 기사 100명 중 40명까지 기록하고 실패해도, 재실행하면 <b>앞의 40명은 원장이 멱등 키로
     * 걸러내고 나머지 60명만 기록된다.</b> 키를 이렇게 잡는 두 번째 이유다.
     *
     * @param batchId  배치 ID
     * @param driverId 기사 ID
     * @return {@code settlement-{batchId}-{driverId}}
     */
    static String payoutIdempotencyKey(Long batchId, Long driverId)
    {
        return "settlement-" + batchId + "-" + driverId;
    }

    /**
     * 상쇄 분개 요청 본문을 만든다.
     * <p>
     * <b>차변과 대변을 함께 보낸다.</b> 원장이 차변합 = 대변합을 검증하므로 한쪽만 보내면
     * 거절된다. 미지급금을 줄이는 쪽이 차변이고, 반대편(현금 유출)이 대변이다.
     * <p>
     * ⚠️ <b>{@code ownerType}이 어느 leg을 기사 잔액에 반영할지 정한다.</b> 원장은 두 leg에
     * 같은 요청 값을 받으므로 이걸로 구분하지 않으면 <b>양쪽 다 기사에 달려 서로 상쇄되고
     * 잔액이 0으로 고정된다.</b> 실제로 그 버그가 있었다
     * (<a href="https://github.com/Easy-ADJ/driver-ledger-system/issues/7">ledger#7</a>).
     * <p>
     * {@code paymentId}는 {@code null}이다 — 상쇄는 특정 결제 한 건이 아니라 <b>그날까지
     * 쌓인 미지급금 전체</b>를 대상으로 하기 때문이다.
     *
     * @param idempotencyKey 멱등 키. 헤더와 본문에 같은 값이 들어간다
     * @param driverId       기사 ID
     * @param fareTotal      상쇄할 운임 합계
     * @return 요청 본문
     */
    private static PayoutEntryRequest payoutRequest(String idempotencyKey, Long driverId, BigDecimal fareTotal)
    {
        return new PayoutEntryRequest(idempotencyKey, driverId, PAYOUT_ENTRY_TYPE, List.of(
                new EntryDetail(DEBIT, fareTotal, null, DRIVER_OWNER),
                new EntryDetail(CREDIT, fareTotal, null, PLATFORM_OWNER)));
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

    /**
     * {@code POST /api/ledger/entries} 요청 본문. 원장의 {@code LedgerEntryRequest}와 같은 형태다.
     *
     * @param idempotencyKey 멱등 키. {@code Idempotency-Key} 헤더와 같은 값이다
     * @param driverId       기사 ID
     * @param entryType      분개 유형. 상쇄는 {@code PAYOUT}이다
     * @param entries        차변·대변 양쪽. 합이 같아야 원장이 받는다
     */
    private record PayoutEntryRequest(String idempotencyKey, Long driverId, String entryType,
                                      List<EntryDetail> entries)
    {
    }

    /**
     * 분개 한 줄.
     *
     * @param direction {@code DEBIT} 또는 {@code CREDIT}
     * @param amount    금액. <b>JSON에서는 문자열로 나간다</b> — 팀 규약이 부동소수점 손실을
     *                  막기 위해 금액을 문자열로 주고받기로 했다
     * @param paymentId 결제 ID. 상쇄 분개에서는 {@code null}이다
     * @param ownerType {@code DRIVER}면 원장이 이 leg을 기사 잔액에 반영하고, 그 밖에는
     *                  {@code driver_id}를 비운다. <b>둘 다 {@code DRIVER}면 서로 상쇄돼
     *                  잔액이 움직이지 않는다</b>
     */
    private record EntryDetail(String direction,
                               @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
                               Long paymentId,
                               String ownerType)
    {
    }

    /**
     * {@code POST /api/ledger/entries} 응답.
     *
     * @param ledgerId 기록된 분개의 원장 ID. {@code SETTLEMENTS.ledger_id}에 저장한다
     */
    private record PayoutEntryResponse(Long ledgerId)
    {
    }

}
