package com.example.driversettlementsystem.settlement.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.driversettlementsystem.exception.ExternalServiceException;
import com.example.driversettlementsystem.settlement.client.DriverUnpaid;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Reader가 언제 원장을 부르고 언제 끝나는지 확인한다.
 * <p>
 * <b>{@code read()}의 {@code null} 계약이 이 클래스의 전부다.</b> Spring Batch는 null을
 * "입력 끝"으로 해석한다. 빈 객체나 빈 리스트를 돌려주면 Step이 무한 루프에 빠진다.
 * <p>
 * {@code @StepScope}로 주입되는 {@code targetDate}는 여기서 생성자로 직접 넣는다 —
 * Job 파라미터에서 실제로 오는지는 {@code DailySettlementJobConfigTest}가 확인한다.
 */
class UnpaidDriverReaderTest
{

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 19);

    private static final String UNPAID_URI = "http://ledger.test/api/ledger/unpaid?date=2026-08-19";

    private MockRestServiceServer ledgerServer;

    private UnpaidDriverReader reader;

    @BeforeEach
    void setUp()
    {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ledger.test");
        ledgerServer = MockRestServiceServer.bindTo(builder).build();
        reader = new UnpaidDriverReader(new LedgerClient(builder.build()), TARGET_DATE);
    }

    /**
     * 기사가 2명이면 {@code read()}는 2번 값을 주고 3번째에 {@code null}을 준다.
     * <b>원장 호출은 그동안 한 번뿐이다</b> — 기사마다 되물으면 여기가 3번이 된다.
     */
    @DisplayName("기사를 하나씩 내보내고 다 떨어지면 null로 끝낸다 — 원장은 한 번만 부른다")
    @Test
    void readsOneDriverAtATime()
    {
        ledgerServer.expect(once(), requestTo(UNPAID_URI))
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

        DriverUnpaid first = reader.read();
        DriverUnpaid second = reader.read();
        DriverUnpaid end = reader.read();

        ledgerServer.verify();
        assertThat(first.driverId()).isEqualTo(1L);
        assertThat(first.totalUnpaidAmount()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(second.driverId()).isEqualTo(2L);
        assertThat(end).isNull();
    }

    /**
     * 정산할 것이 없는 날에 Job이 실패하면 안 된다. 주말이나 운행이 없던 날이 그렇다.
     */
    @DisplayName("미지급 기사가 0명이면 첫 read()가 바로 null이다")
    @Test
    void endsImmediatelyWhenNoUnpaidDrivers()
    {
        ledgerServer.expect(requestTo(UNPAID_URI))
                .andRespond(withSuccess("""
                        { "targetDate": "2026-08-19", "data": [] }
                        """, MediaType.APPLICATION_JSON));

        assertThat(reader.read()).isNull();
    }

    /**
     * 대상을 못 읽은 채 "성공"으로 끝나면 <b>그날 정산이 통째로 비는데도 아무도 모른다.</b>
     * 예외를 잡지 않고 그대로 올려 Job을 실패시킨다.
     */
    @DisplayName("원장이 계속 실패하면 예외가 그대로 올라간다 — Job이 실패해야 한다")
    @Test
    void propagatesLedgerFailure()
    {
        ledgerServer.expect(times(3), requestTo(UNPAID_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> reader.read())
                .isInstanceOf(ExternalServiceException.class);

        ledgerServer.verify();
    }

}
