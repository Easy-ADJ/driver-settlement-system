package com.example.driversettlementsystem.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * {@code RestClientConfig}의 완료 조건 4개를 확인한다.
 * <p>
 * 특히 <b>타임아웃이 실제로 걸렸는지</b>는 설정값을 읽어서는 알 수 없다. 응답하지 않는
 * 서버를 실제로 열어 호출해야 무한 대기하지 않는다는 것이 증명된다.
 */
class RestClientConfigTest
{

    /**
     * {@code PropertyPlaceholderAutoConfiguration}을 함께 올린다. 이게 없으면
     * {@code ${...}}가 해석되지 않아 <b>주소가 없어도 컨텍스트가 떠 버려서</b> 실제 앱과
     * 다른 것을 검증하게 된다.
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(RestClientConfig.class);

    @DisplayName("서버 주소 환경변수가 없으면 부팅 시점에 실패한다 — 호출 시점 NPE가 아니라")
    @Test
    void failsToStartWithoutBaseUrl()
    {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @DisplayName("주소가 주입되면 결제·원장 클라이언트 빈이 이름으로 구분돼 뜬다")
    @Test
    void createsTwoNamedClients()
    {
        contextRunner
                .withPropertyValues(
                        "settlement.client.payment.base-url=http://payment.test",
                        "settlement.client.ledger.base-url=http://ledger.test")
                .run(context ->
                {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("paymentRestClient");
                    assertThat(context).hasBean("ledgerRestClient");
                    assertThat(context.getBeansOfType(RestClient.class)).hasSize(2);
                });
    }

    @DisplayName("타임아웃 값은 팀 규약과 같다 — 연결 5초 / 응답 10초")
    @Test
    void timeoutsMatchTeamContract()
    {
        assertThat(RestClientConfig.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(5));
        assertThat(RestClientConfig.READ_TIMEOUT).isEqualTo(Duration.ofSeconds(10));
    }

    /**
     * 연결은 받아주되 응답을 주지 않는 서버를 열어, 호출이 무한정 매달리지 않는지 본다.
     * <p>
     * 상수 10초를 그대로 쓰면 이 테스트 하나가 10초를 잡아먹으므로 같은 경로에 짧은 값을
     * 넣는다. 검증하려는 것은 "값이 10초냐"가 아니라 <b>"타임아웃이 실제로 동작하느냐"</b>이고,
     * 값 자체는 위 테스트가 따로 지킨다.
     */
    @DisplayName("응답하지 않는 서버를 부르면 무한 대기하지 않고 예외로 끝난다")
    @Test
    void readTimeoutEndsTheCall() throws IOException
    {
        try (ServerSocket silentServer = new ServerSocket(0))
        {
            RestClient client = RestClientConfig.restClient(
                    "http://localhost:" + silentServer.getLocalPort(),
                    Duration.ofMillis(300),
                    Duration.ofMillis(300));

            long startedAt = System.nanoTime();

            assertThatThrownBy(() -> client.get().uri("/api/payments").retrieve().body(String.class))
                    .isInstanceOf(ResourceAccessException.class);

            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofSeconds(5));
        }
    }

}
