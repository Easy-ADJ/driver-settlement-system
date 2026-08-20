package com.example.driversettlementsystem.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.driversettlementsystem.exception.ExternalServiceException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 원장 응답을 어떻게 읽고, 실패를 어떻게 다루는지 확인한다.
 * <p>
 * 여기서 쓰는 JSON은 <b>원장이 실제로 구현한 DTO</b>를 그대로 옮긴 것이다 —
 * {@code UnpaidLedgerResponse}·{@code DriverLedgerResponse}. 추측한 형태가 아니다.
 */
class LedgerClientTest
{

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 19);

    private static final String UNPAID_URI = "http://ledger.test/api/ledger/unpaid?date=2026-08-19";

    private static final String DRIVER_URI = "http://ledger.test/api/ledger?driver_id=1";

    private MockRestServiceServer ledgerServer;

    private LedgerClient ledgerClient;

    @BeforeEach
    void setUp()
    {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ledger.test");
        ledgerServer = MockRestServiceServer.bindTo(builder).build();
        ledgerClient = new LedgerClient(builder.build());
    }

    @DisplayName("미지급 기사 목록을 금액까지 한 번에 읽는다")
    @Test
    void parsesUnpaidDrivers()
    {
        ledgerServer.expect(requestTo(UNPAID_URI))
                .andRespond(withSuccess("""
                        {
                          "targetDate": "2026-08-19",
                          "data": [
                            { "driverId": 1, "totalUnpaidAmount": "15000",
                              "lastApprovedAt": "2026-08-19T14:30:00Z" },
                            { "driverId": 2, "totalUnpaidAmount": "32000",
                              "lastApprovedAt": "2026-08-19T23:15:00Z" }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<DriverUnpaid> drivers = ledgerClient.findUnpaidDrivers(TARGET_DATE);

        ledgerServer.verify();
        assertThat(drivers).hasSize(2);
        assertThat(drivers.get(0).driverId()).isEqualTo(1L);
        assertThat(drivers.get(0).totalUnpaidAmount()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(drivers.get(0).lastApprovedAt()).isEqualTo(Instant.parse("2026-08-19T14:30:00Z"));
        assertThat(drivers.get(1).totalUnpaidAmount()).isEqualByComparingTo(new BigDecimal("32000"));
    }

    @DisplayName("미지급 기사가 없는 날은 빈 목록이다 — null이 아니다")
    @Test
    void returnsEmptyListWhenNoUnpaidDrivers()
    {
        ledgerServer.expect(requestTo(UNPAID_URI))
                .andRespond(withSuccess("""
                        { "targetDate": "2026-08-19", "data": [] }
                        """, MediaType.APPLICATION_JSON));

        assertThat(ledgerClient.findUnpaidDrivers(TARGET_DATE)).isEmpty();
    }

    /**
     * <b>정산 내역서가 이 응답 하나에 걸려 있다.</b> {@code trips}가 사라진 뒤로 결제 단위
     * 근거는 원장에만 있다.
     */
    @DisplayName("기사별 조회는 결제 건별 근거까지 읽는다")
    @Test
    void parsesDriverLedgerWithPaymentDetails()
    {
        ledgerServer.expect(requestTo(DRIVER_URI))
                .andRespond(withSuccess("""
                        {
                          "driverId": 1,
                          "totalUnpaidAmount": "15000",
                          "paymentDetails": [
                            { "paymentId": 100, "amount": "10000", "approvedAt": "2026-08-19T14:30:00Z" },
                            { "paymentId": 101, "amount": "5000",  "approvedAt": "2026-08-19T18:00:00Z" }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        DriverLedger ledger = ledgerClient.findDriverLedger(1L);

        ledgerServer.verify();
        assertThat(ledger.totalUnpaidAmount()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(ledger.paymentDetails()).hasSize(2);
        assertThat(ledger.paymentDetails().get(0).paymentId()).isEqualTo(100L);
        assertThat(ledger.paymentDetails().get(0).amount()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(ledger.paymentDetails().get(0).approvedAt())
                .isEqualTo(Instant.parse("2026-08-19T14:30:00Z"));
    }

    @DisplayName("결제 건별 내역이 없어도 빈 목록으로 온다 — null이 아니다")
    @Test
    void normalizesMissingPaymentDetails()
    {
        ledgerServer.expect(requestTo(DRIVER_URI))
                .andRespond(withSuccess("""
                        { "driverId": 1, "totalUnpaidAmount": "15000" }
                        """, MediaType.APPLICATION_JSON));

        assertThat(ledgerClient.findDriverLedger(1L).paymentDetails()).isEmpty();
    }

    /**
     * 최초 1회 + 재시도 2회 = <b>총 3회</b>다. 호출 횟수로 확인하지 않으면 재시도가 실제로
     * 도는지 알 수 없다.
     */
    @DisplayName("5xx는 2회 재시도한 뒤 LEDGER_SERVICE_UNAVAILABLE로 끝난다")
    @Test
    void retriesServerErrorTwice()
    {
        ledgerServer.expect(times(3), requestTo(UNPAID_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> ledgerClient.findUnpaidDrivers(TARGET_DATE))
                .isInstanceOf(ExternalServiceException.class)
                .satisfies(thrown ->
                {
                    ExternalServiceException e = (ExternalServiceException) thrown;
                    assertThat(e.getCode()).isEqualTo("LEDGER_SERVICE_UNAVAILABLE");
                    assertThat(e.getServiceName()).isEqualTo("ledger");
                });

        ledgerServer.verify();
    }

    /**
     * 잘못된 요청을 두 번 더 보내도 답은 같고, 그동안 배치만 늦어진다.
     */
    @DisplayName("4xx는 재시도하지 않는다 — 딱 한 번 부르고 실패한다")
    @Test
    void doesNotRetryClientError()
    {
        ledgerServer.expect(once(), requestTo(UNPAID_URI)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> ledgerClient.findUnpaidDrivers(TARGET_DATE))
                .isInstanceOf(ExternalServiceException.class);

        ledgerServer.verify();
    }

    /**
     * 부호가 뒤집히면 계산은 전부 정상인데 대사만 항상 불일치로 뜬다. 버그처럼 보이지
     * 않아 원인을 찾는 데 오래 걸린다 — 틀린 값으로 정산을 확정하느니 여기서 멈춘다.
     */
    @DisplayName("미지급금이 음수로 오면 계산하지 않고 멈춘다")
    @Test
    void rejectsNegativeUnpaidAmount()
    {
        ledgerServer.expect(requestTo(UNPAID_URI))
                .andRespond(withSuccess("""
                        {
                          "targetDate": "2026-08-19",
                          "data": [
                            { "driverId": 1, "totalUnpaidAmount": "-15000",
                              "lastApprovedAt": "2026-08-19T14:30:00Z" }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ledgerClient.findUnpaidDrivers(TARGET_DATE))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("규약은 양수다");
    }

    @DisplayName("재시도 정책은 팀 규약과 같다 — 5xx·타임아웃만 2회 지수 백오프")
    @Test
    void retryPolicyMatchesTeamContract()
    {
        assertThat(LedgerClient.MAX_RETRIES).isEqualTo(2);
        assertThat(LedgerClient.BACKOFF_MULTIPLIER).isEqualTo(2.0);
    }

}
