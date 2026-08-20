package com.example.driversettlementsystem.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.driversettlementsystem.exception.ExternalServiceException;
import java.math.BigDecimal;
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
 * {@code PaymentClient}가 결제 응답을 어떻게 읽고, 실패를 어떻게 바꾸는지 확인한다.
 * <p>
 * ⚠️ <b>결제 서버에 이 API가 아직 없다.</b> 여기서 검증하는 것은 계약서대로 왔을 때의
 * 동작이지 상대가 실제로 그렇게 준다는 보장이 아니다. 응답 형태가 다르게 나오면 이
 * 테스트의 JSON부터 고쳐야 한다.
 */
class PaymentClientTest
{

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 17);

    private static final String EXPECTED_URI = "http://payment.test/api/payments?date=2026-08-17";

    private MockRestServiceServer paymentServer;

    private PaymentClient paymentClient;

    @BeforeEach
    void setUp()
    {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payment.test");
        paymentServer = MockRestServiceServer.bindTo(builder).build();
        paymentClient = new PaymentClient(builder.build());
    }

    @DisplayName("날짜를 yyyy-MM-dd로 붙여 부르고, 금액을 BigDecimal로 읽는다")
    @Test
    void parsesPayments()
    {
        paymentServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "payments": [
                            { "paymentId": 100, "driverId": 1, "amount": "15000", "status": "COMPLETED" },
                            { "paymentId": 101, "driverId": 2, "amount": "32000", "status": "CANCELED" }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<PaymentSummary> payments = paymentClient.findPaymentsByDate(TARGET_DATE);

        paymentServer.verify();
        assertThat(payments).hasSize(2);
        assertThat(payments.get(0).paymentId()).isEqualTo(100L);
        assertThat(payments.get(0).driverId()).isEqualTo(1L);
        assertThat(payments.get(0).amount()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(payments.get(0).status()).isEqualTo("COMPLETED");
        assertThat(payments.get(1).status()).isEqualTo("CANCELED");
    }

    /**
     * <b>이 테스트가 {@code PaymentSummary}를 우리가 소유하는 이유 전체다.</b> 결제가
     * 카카오페이 연동으로 {@code tid}·{@code partnerOrderId}를 늘려도 정산은 안 깨져야 한다.
     * 깨진다면 상대의 모든 필드 추가가 우리 배포를 요구하게 된다.
     */
    @DisplayName("결제가 모르는 필드를 추가해도 깨지지 않는다")
    @Test
    void ignoresUnknownFields()
    {
        paymentServer.expect(requestTo(EXPECTED_URI))
                .andRespond(withSuccess("""
                        {
                          "payments": [
                            {
                              "paymentId": 100,
                              "driverId": 1,
                              "amount": "15000",
                              "status": "COMPLETED",
                              "tid": "T1234567890",
                              "partnerOrderId": "ORDER-1",
                              "partnerUserId": "USER-1"
                            }
                          ],
                          "totalCount": 1
                        }
                        """, MediaType.APPLICATION_JSON));

        List<PaymentSummary> payments = paymentClient.findPaymentsByDate(TARGET_DATE);

        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).amount()).isEqualByComparingTo(new BigDecimal("15000"));
    }

    @DisplayName("결제가 없는 날은 빈 목록이다 — null이 아니다")
    @Test
    void returnsEmptyListWhenNoPayments()
    {
        paymentServer.expect(requestTo(EXPECTED_URI))
                .andRespond(withSuccess("{ \"payments\": [] }", MediaType.APPLICATION_JSON));

        assertThat(paymentClient.findPaymentsByDate(TARGET_DATE)).isEmpty();
    }

    @DisplayName("payments 키가 없어도 빈 목록이다 — 대사가 NPE로 죽지 않는다")
    @Test
    void returnsEmptyListWhenPaymentsMissing()
    {
        paymentServer.expect(requestTo(EXPECTED_URI))
                .andRespond(withSuccess("{ }", MediaType.APPLICATION_JSON));

        assertThat(paymentClient.findPaymentsByDate(TARGET_DATE)).isEmpty();
    }

    @DisplayName("결제 서버가 5xx면 PAYMENT_SERVICE_UNAVAILABLE로 바뀐다")
    @Test
    void convertsServerError()
    {
        paymentServer.expect(requestTo(EXPECTED_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> paymentClient.findPaymentsByDate(TARGET_DATE))
                .isInstanceOf(ExternalServiceException.class)
                .satisfies(thrown ->
                {
                    ExternalServiceException e = (ExternalServiceException) thrown;
                    assertThat(e.getCode()).isEqualTo("PAYMENT_SERVICE_UNAVAILABLE");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(e.getServiceName()).isEqualTo("payment");
                });
    }

    /**
     * 이슈의 완료 조건은 5xx만 적었지만 4xx도 잡는다. 안 잡으면 {@code RestClientException}이
     * 그대로 배치까지 올라가 {@code GlobalExceptionHandler}의 공통 에러 포맷을 벗어난다.
     * <b>재시도 여부(4xx는 재시도하지 않는다)는 여전히 호출부가 정한다.</b>
     */
    @DisplayName("4xx도 공통 에러 포맷으로 바뀐다 — RestClientException이 새어나가지 않는다")
    @Test
    void convertsClientError()
    {
        paymentServer.expect(requestTo(EXPECTED_URI)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> paymentClient.findPaymentsByDate(TARGET_DATE))
                .isInstanceOf(ExternalServiceException.class);
    }

}
