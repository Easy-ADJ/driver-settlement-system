package com.example.driversettlementsystem.settlement.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.driversettlementsystem.settlement.client.PaymentDetail;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.ReconciliationStatus;
import com.example.driversettlementsystem.settlement.domain.Settlement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

/**
 * 조회 응답이 <b>실제 JSON으로 어떻게 나가는지</b> 확인한다.
 * <p>
 * 레코드 필드 타입만 봐서는 알 수 없는 것들이 있다 — 금액이 따옴표에 싸여 나가는지,
 * 큰 수가 지수 표기로 새지 않는지, 빈 목록이 생략되지 않는지. 셋 다 <b>클라이언트가
 * 파싱에 실패하거나 조용히 틀린 값을 읽게 되는</b> 지점이다.
 * <p>
 * 애플리케이션이 실제로 쓰는 Jackson 설정으로 직렬화한다({@code @JsonTest}). 직접 만든
 * {@code ObjectMapper}로 검증하면 운영과 다른 것을 확인하게 된다.
 */
@JsonTest
class SettlementResponseTest
{

    @Autowired
    private JacksonTester<SettlementResponse> json;

    /**
     * <b>이 테스트가 이 이슈의 전부다.</b> 금액이 JSON 숫자로 나가면 자바스크립트
     * 클라이언트가 {@code Number}로 파싱하면서 큰 금액에서 정밀도가 깨진다.
     */
    @DisplayName("금액은 문자열로, ID는 숫자로 나간다")
    @Test
    void serializesAmountsAsStringsAndIdsAsNumbers() throws Exception
    {
        String result = json.write(sampleResponse()).getJson();

        assertThat(result).contains("\"fareTotal\":\"20000\"");
        assertThat(result).contains("\"feeAmount\":\"4000\"");
        assertThat(result).contains("\"payoutAmount\":\"16000\"");
        assertThat(result).contains("\"driverId\":1");
        assertThat(result).contains("\"paymentId\":100");
        assertThat(result).contains("\"amount\":\"15000\"");
    }

    @DisplayName("일자·상태가 API 명세의 표기로 나간다")
    @Test
    void serializesDateAndStatuses() throws Exception
    {
        String result = json.write(sampleResponse()).getJson();

        assertThat(result).contains("\"targetDate\":\"2026-08-19\"");
        assertThat(result).contains("\"batchStatus\":\"CONFIRMED\"");
        assertThat(result).contains("\"reconciliationStatus\":\"MATCHED\"");
        assertThat(result).contains("\"payoutStatus\":\"CONFIRMED\"");
        assertThat(result).contains("\"approvedAt\":\"2026-08-19T14:30:00Z\"");
    }

    /**
     * {@code BigDecimal.toString()}은 값에 따라 지수 표기({@code 1E+8})를 낸다. 금액이
     * 그렇게 나가면 클라이언트가 파싱에 실패한다 — {@code toPlainString()}을 쓰는 이유다.
     */
    @DisplayName("큰 금액도 지수 표기로 새지 않는다")
    @Test
    void writesLargeAmountsInPlainNotation() throws Exception
    {
        Settlement settlement = Settlement.of(1L, 1L,
                new BigDecimal("100000000"), new BigDecimal("20000000"));

        String result = json.write(responseWith(
                DriverSettlementResponse.from(settlement, List.of()))).getJson();

        assertThat(result).contains("\"fareTotal\":\"100000000\"");
        assertThat(result).doesNotContain("E+");
    }

    /**
     * 비어 있다고 생략하면 클라이언트가 "필드 없음"과 "결제 없음"을 구분하려고 분기해야
     * 한다. {@code FR-B-08} 추적성의 실체라 <b>항상 나가야 한다.</b>
     */
    @DisplayName("결제 근거가 없어도 payments 배열이 생략되지 않는다")
    @Test
    void keepsEmptyPaymentsArray() throws Exception
    {
        Settlement settlement = Settlement.of(1L, 1L,
                new BigDecimal("20000"), new BigDecimal("4000"));

        String result = json.write(responseWith(
                DriverSettlementResponse.from(settlement, List.of()))).getJson();

        assertThat(result).contains("\"payments\":[]");
    }

    @DisplayName("대사 전이면 reconciliationStatus가 null로 나간다")
    @Test
    void writesNullReconciliationStatusBeforeReconciling() throws Exception
    {
        SettlementResponse response = new SettlementResponse(
                LocalDate.of(2026, 8, 19), BatchStatus.RUNNING, null, List.of());

        assertThat(json.write(response).getJson()).contains("\"reconciliationStatus\":null");
    }

    @DisplayName("fareTotal - feeAmount == payoutAmount 가 응답에서 성립한다")
    @Test
    void keepsAmountConsistentInResponse()
    {
        Settlement settlement = Settlement.of(1L, 1L,
                new BigDecimal("3333"), new BigDecimal("666"));

        DriverSettlementResponse response = DriverSettlementResponse.from(settlement, List.of());

        assertThat(new BigDecimal(response.fareTotal()).subtract(new BigDecimal(response.feeAmount())))
                .isEqualByComparingTo(new BigDecimal(response.payoutAmount()));
    }

    private static SettlementResponse sampleResponse()
    {
        Settlement settlement = Settlement.of(1L, 1L,
                new BigDecimal("20000"), new BigDecimal("4000"));

        List<PaymentDetail> details = List.of(
                new PaymentDetail(100L, new BigDecimal("15000"), Instant.parse("2026-08-19T14:30:00Z")),
                new PaymentDetail(101L, new BigDecimal("5000"), Instant.parse("2026-08-19T18:00:00Z")));

        return responseWith(DriverSettlementResponse.from(settlement, details));
    }

    private static SettlementResponse responseWith(DriverSettlementResponse driver)
    {
        return new SettlementResponse(
                LocalDate.of(2026, 8, 19),
                BatchStatus.CONFIRMED,
                ReconciliationStatus.MATCHED,
                List.of(driver));
    }

}
