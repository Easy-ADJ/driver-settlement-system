package com.example.driversettlementsystem.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 외부 서버 호출용 {@link RestClient} 빈 설정.
 * <p>
 * 타임아웃과 서버 주소를 이 클래스 한 곳에서만 정한다. 클라이언트({@code PaymentClient}·
 * {@code LedgerClient})는 완성된 {@code RestClient}를 주입받기만 하므로, 타임아웃 정책이
 * 바뀌어도 클라이언트 코드는 바뀌지 않는다.
 * <p>
 * <b>타임아웃이 없으면 상대 서버가 느려질 때 정산 배치 스레드가 무한정 매달린다.</b>
 * 원장이 느려지는 것만으로 정산 배치가 영영 안 끝나는 상황이 된다.
 * <p>
 * ⚠️ <b>재시도는 여기에 넣지 않는다.</b> 실패 후 무엇을 할지가 호출마다 다르기 때문이다 —
 * 원장 조회는 Job 실패, 지급 분개는 확정 보류, 결제 대사는 SKIPPED다. 공통 인터셉터로
 * 묶으면 셋이 같아진다. 여기서는 타임아웃만 걸고, 재시도는 각 호출부가 붙인다.
 */
@Configuration
public class RestClientConfig
{

    /**
     * 연결 수립 제한 시간.
     * <p>
     * Railway가 유휴 인스턴스를 재우므로 3초로는 콜드스타트를 못 넘긴다. 짧게 잡으면
     * 데모의 첫 시도가 타임아웃으로 죽는다.
     */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * 응답 대기 제한 시간.
     * <p>
     * 넉넉하게 잡는 대가는 장애를 늦게 알아차리는 것이다. 상대가 죽었을 때 10초를 기다린
     * 뒤에야 실패하고, 기사 수만큼 곱해진다. 그래도 데모가 첫 호출에서 죽는 것보다 낫다.
     */
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 결제 서버 호출용 클라이언트. 대사에서만 쓴다.
     *
     * @param baseUrl {@code PAYMENT_API_BASE_URL} 환경변수로 주입된다. 기본값을 두지 않는다 —
     *                주소가 빈 채로 떠서 호출 시점에 실패하는 것보다, 뜨지 않는 편이 원인을
     *                훨씬 빨리 알려준다
     * @return 결제 서버 전용 {@code RestClient}
     */
    @Bean
    public RestClient paymentRestClient(@Value("${settlement.client.payment.base-url}") String baseUrl)
    {
        return restClient(baseUrl, CONNECT_TIMEOUT, READ_TIMEOUT);
    }

    /**
     * 원장 서버 호출용 클라이언트.
     * <p>
     * <b>정산이 가장 많이 부르는 쪽이다</b> — 대상 선별·금액 조회·지급 분개 세 곳에서 쓴다.
     *
     * @param baseUrl {@code LEDGER_API_BASE_URL} 환경변수로 주입된다
     * @return 원장 서버 전용 {@code RestClient}
     */
    @Bean
    public RestClient ledgerRestClient(@Value("${settlement.client.ledger.base-url}") String baseUrl)
    {
        return restClient(baseUrl, CONNECT_TIMEOUT, READ_TIMEOUT);
    }

    /**
     * 두 클라이언트가 같은 정책을 쓰므로 한 곳으로 뽑아 둔다.
     * <p>
     * 타임아웃을 상수가 아니라 인자로 받는 것은 <b>테스트 때문이다.</b> 완료 조건인
     * "응답하지 않는 서버를 호출하면 10초 안에 예외로 끝난다"를 실제로 검증하려면 테스트가
     * 10초를 기다려야 한다. 짧은 값을 넣어 같은 경로를 태울 수 있게 열어 둔다. 빈 2개는
     * 여전히 상수만 넘기므로 운영 동작은 바뀌지 않는다.
     *
     * @param baseUrl        서버 주소
     * @param connectTimeout 연결 수립 제한 시간
     * @param readTimeout    응답 대기 제한 시간
     * @return 타임아웃이 걸린 {@code RestClient}
     */
    static RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout)
    {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .build();
    }

    /**
     * 연결 타임아웃은 JDK {@link HttpClient}가, 응답 타임아웃은 팩토리가 들고 있다.
     *
     * @param connectTimeout 연결 수립 제한 시간
     * @param readTimeout    응답 대기 제한 시간
     * @return 두 타임아웃이 모두 설정된 요청 팩토리
     */
    private static ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout)
    {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);

        return factory;
    }

}
