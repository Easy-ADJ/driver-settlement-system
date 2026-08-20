package com.example.driversettlementsystem.settlement.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.driversettlementsystem.exception.GlobalExceptionHandler;
import com.example.driversettlementsystem.exception.SettlementNotFoundException;
import com.example.driversettlementsystem.settlement.client.PaymentDetail;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.Settlement;
import com.example.driversettlementsystem.settlement.dto.DriverSettlementResponse;
import com.example.driversettlementsystem.settlement.dto.SettlementResponse;
import com.example.driversettlementsystem.settlement.service.SettlementQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP 계층만 확인한다 — 파라미터가 어떻게 매핑되고, 실패가 어떤 상태·코드로 나가는지.
 * <p>
 * 조회 로직은 {@code SettlementQueryServiceTest}가, 직렬화는 {@code SettlementResponseTest}가
 * 이미 맡고 있다. 여기서만 확인할 수 있는 것은 <b>Spring이 던지는 예외가 우리 에러 코드로
 * 바뀌는가</b>이다 — 컨트롤러에 검증 코드를 안 쓰기로 한 결정이 실제로 성립하는지가 여기서
 * 갈린다.
 * <p>
 * {@code GlobalExceptionHandler}를 명시적으로 {@code @Import}한다. 슬라이스 테스트는
 * {@code @RestControllerAdvice}를 자동으로 올려주지만, 이 테스트의 절반이 그 변환이라
 * 의존을 눈에 보이게 둔다.
 */
@WebMvcTest(SettlementController.class)
@Import(GlobalExceptionHandler.class)
class SettlementControllerTest
{

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 19);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementQueryService queryService;

    @DisplayName("date만 주면 그날 전체 정산이 200으로 나온다")
    @Test
    void returnsAllSettlementsForDate() throws Exception
    {
        when(queryService.findSettlements(eq(TARGET_DATE), isNull()))
                .thenReturn(responseWith(driverWithoutPayments()));

        mockMvc.perform(get("/api/settlements").param("date", "2026-08-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetDate").value("2026-08-19"))
                .andExpect(jsonPath("$.batchStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.settlements[0].driverId").value(1))
                .andExpect(jsonPath("$.settlements[0].fareTotal").value("20000"))
                .andExpect(jsonPath("$.settlements[0].payoutAmount").value("16000"))
                .andExpect(jsonPath("$.settlements[0].payments").isArray())
                .andExpect(jsonPath("$.settlements[0].payments").isEmpty());
    }

    @DisplayName("driverId를 주면 그 기사 것만 나오고 결제 근거가 담긴다")
    @Test
    void returnsSingleDriverWithPayments() throws Exception
    {
        when(queryService.findSettlements(eq(TARGET_DATE), eq(1L)))
                .thenReturn(responseWith(driverWithPayments()));

        mockMvc.perform(get("/api/settlements")
                        .param("date", "2026-08-19")
                        .param("driverId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlements.length()").value(1))
                .andExpect(jsonPath("$.settlements[0].payments.length()").value(1))
                .andExpect(jsonPath("$.settlements[0].payments[0].paymentId").value(100))
                .andExpect(jsonPath("$.settlements[0].payments[0].amount").value("15000"));
    }

    /**
     * 컨트롤러에 {@code if (date == null)}을 쓰지 않기로 한 결정이 여기서 검증된다. Spring이
     * 던지고 핸들러가 바꾼다.
     */
    @DisplayName("date를 빼면 400 / MISSING_REQUIRED_PARAMETER")
    @Test
    void rejectsMissingDate() throws Exception
    {
        mockMvc.perform(get("/api/settlements"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_PARAMETER"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty());
    }

    @DisplayName("날짜 형식이 틀리면 400 / INVALID_DATE_FORMAT")
    @Test
    void rejectsMalformedDate() throws Exception
    {
        mockMvc.perform(get("/api/settlements").param("date", "2026-13-99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_FORMAT"));
    }

    @DisplayName("없는 일자면 404 / SETTLEMENT_NOT_FOUND")
    @Test
    void returnsNotFoundForMissingBatch() throws Exception
    {
        when(queryService.findSettlements(any(), any()))
                .thenThrow(SettlementNotFoundException.batchNotFound(TARGET_DATE));

        mockMvc.perform(get("/api/settlements").param("date", "2026-08-19"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    private static SettlementResponse responseWith(DriverSettlementResponse driver)
    {
        return new SettlementResponse(TARGET_DATE, BatchStatus.CONFIRMED, null, List.of(driver));
    }

    private static DriverSettlementResponse driverWithoutPayments()
    {
        return DriverSettlementResponse.from(sampleSettlement(), List.of());
    }

    private static DriverSettlementResponse driverWithPayments()
    {
        return DriverSettlementResponse.from(sampleSettlement(), List.of(
                new PaymentDetail(100L, new BigDecimal("15000"),
                        Instant.parse("2026-08-19T14:30:00Z"))));
    }

    private static Settlement sampleSettlement()
    {
        return Settlement.of(1L, 1L, new BigDecimal("20000"), new BigDecimal("4000"));
    }

}
