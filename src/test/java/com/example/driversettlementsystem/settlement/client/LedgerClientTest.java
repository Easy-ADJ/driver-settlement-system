package com.example.driversettlementsystem.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
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
import org.springframework.http.HttpMethod;
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

    private static final String ENTRIES_URI = "http://ledger.test/api/ledger/entries";

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

    /**
     * <b>이 요청 형태가 이 이슈의 전부다.</b> 원장은 차변합 = 대변합을 검증하므로 한쪽만
     * 보내면 거절되고, {@code entryType}이 {@code PAYOUT}이 아니면 결제 분개로 쌓여
     * <b>미지급금이 줄기는커녕 늘어난다.</b>
     */
    @DisplayName("상쇄 분개는 차변·대변을 함께, PAYOUT 유형으로 보낸다")
    @Test
    void sendsBalancedPayoutEntry()
    {
        ledgerServer.expect(once(), requestTo(ENTRIES_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "settlement-7-1"))
                .andExpect(jsonPath("$.idempotencyKey").value("settlement-7-1"))
                .andExpect(jsonPath("$.driverId").value(1))
                .andExpect(jsonPath("$.entryType").value("PAYOUT"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].amount").value("42000"))
                .andExpect(jsonPath("$.entries[0].paymentId").doesNotExist())
                .andExpect(jsonPath("$.entries[1].direction").value("CREDIT"))
                .andExpect(jsonPath("$.entries[1].amount").value("42000"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{ \"ledgerId\": 991 }"));

        Long ledgerId = ledgerClient.recordPayoutEntry(7L, 1L, new BigDecimal("42000"));

        ledgerServer.verify();
        assertThat(ledgerId).isEqualTo(991L);
    }

    /**
     * 금액을 숫자로 보내면 JSON 파서마다 부동소수점으로 읽어 <b>1원이 조용히 사라질 수
     * 있다.</b> 팀 규약이 금액을 문자열로 정한 이유이고, 규약은 지켜야 규약이다.
     */
    @DisplayName("금액은 JSON에서 문자열로 나간다")
    @Test
    void sendsAmountAsString()
    {
        ledgerServer.expect(once(), requestTo(ENTRIES_URI))
                .andExpect(content().string(containsString("\"amount\":\"33600\"")))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{ \"ledgerId\": 1 }"));

        ledgerClient.recordPayoutEntry(7L, 1L, new BigDecimal("33600"));

        ledgerServer.verify();
    }

    /**
     * <b>재시도마다 키가 달라지면 멱등성이 무의미해진다.</b> 5xx로 두 번 튕긴 뒤 성공하는
     * 동안 세 번의 요청이 모두 같은 키를 들고 가야, 원장이 첫 결과를 돌려줄 수 있다.
     * {@code UUID.randomUUID()}를 썼다면 여기서 분개가 세 세트 쌓인다.
     */
    @DisplayName("재시도해도 멱등 키가 같아 분개가 한 세트만 쌓인다")
    @Test
    void keepsSameIdempotencyKeyAcrossRetries()
    {
        ledgerServer.expect(times(2), requestTo(ENTRIES_URI))
                .andExpect(header("Idempotency-Key", "settlement-7-1"))
                .andRespond(withServerError());
        ledgerServer.expect(once(), requestTo(ENTRIES_URI))
                .andExpect(header("Idempotency-Key", "settlement-7-1"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{ \"ledgerId\": 991 }"));

        Long ledgerId = ledgerClient.recordPayoutEntry(7L, 1L, new BigDecimal("42000"));

        ledgerServer.verify();
        assertThat(ledgerId).isEqualTo(991L);
    }

    @DisplayName("멱등 키는 배치와 기사만으로 만들어져 재계산할 수 있다")
    @Test
    void buildsRecomputableIdempotencyKey()
    {
        assertThat(LedgerClient.payoutIdempotencyKey(7L, 1L)).isEqualTo("settlement-7-1");
        assertThat(LedgerClient.payoutIdempotencyKey(7L, 1L))
                .isEqualTo(LedgerClient.payoutIdempotencyKey(7L, 1L));
        assertThat(LedgerClient.payoutIdempotencyKey(8L, 1L)).isNotEqualTo("settlement-7-1");
    }

    /**
     * 재시도를 소진해도 상쇄가 기록되지 않았다는 사실은 <b>반드시 위로 올라가야 한다.</b>
     * 여기서 삼키면 배치가 확정되고, 다음날 같은 금액이 다시 정산된다.
     */
    @DisplayName("상쇄 기록이 끝내 실패하면 예외로 올라간다 — 조용히 넘어가지 않는다")
    @Test
    void failsLoudlyWhenPayoutCannotBeRecorded()
    {
        ledgerServer.expect(times(3), requestTo(ENTRIES_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> ledgerClient.recordPayoutEntry(7L, 1L, new BigDecimal("42000")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("지급 상쇄 분개 기록");

        ledgerServer.verify();
    }

    @DisplayName("응답에 원장 ID가 없으면 성공으로 치지 않는다")
    @Test
    void rejectsResponseWithoutLedgerId()
    {
        ledgerServer.expect(once(), requestTo(ENTRIES_URI))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{ }"));

        assertThatThrownBy(() -> ledgerClient.recordPayoutEntry(7L, 1L, new BigDecimal("42000")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("원장 ID가 없다");
    }

    /**
     * <b>이 필드가 빠지면 아무 에러 없이 미지급금이 그대로 남는다.</b> 원장은 두 leg에 같은
     * 요청 값을 받으므로 {@code ownerType}으로만 어느 쪽이 기사 잔액인지 안다. 구분하지
     * 않으면 <b>양쪽 다 기사에 달려 서로 상쇄되고 잔액이 0으로 고정된다</b> —
     * 실제로 있던 버그다 (ledger#7).
     * <p>
     * 요청은 201로 성공하고 분개도 쌓인다. <b>잔액만 안 움직인다.</b> 그래서 응답으로는
     * 절대 알 수 없고, 다음날 같은 기사가 또 정산돼야 드러난다.
     */
    @DisplayName("차변만 DRIVER다 — 양쪽 다 DRIVER면 잔액이 움직이지 않는다")
    @Test
    void marksOnlyDriverLegAsDriverOwned()
    {
        ledgerServer.expect(once(), requestTo(ENTRIES_URI))
                .andExpect(jsonPath("$.entries[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].ownerType").value("DRIVER"))
                .andExpect(jsonPath("$.entries[1].direction").value("CREDIT"))
                .andExpect(jsonPath("$.entries[1].ownerType").value("PLATFORM"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{ \"ledgerId\": 991 }"));

        ledgerClient.recordPayoutEntry(7L, 1L, new BigDecimal("42000"));

        ledgerServer.verify();
    }

}
