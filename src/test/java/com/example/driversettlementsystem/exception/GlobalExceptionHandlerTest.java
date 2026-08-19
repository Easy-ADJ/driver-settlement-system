package com.example.driversettlementsystem.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예외가 팀 공통 에러 포맷으로 나가는지 검증한다.
 * <p>
 * 조회 컨트롤러가 아직 없으므로 <b>테스트 전용 컨트롤러</b>를 세워 예외만 발생시킨다.
 * 핸들러는 어떤 컨트롤러에서 왔는지 보지 않으므로, 실제 컨트롤러가 생겨도 이 검증은
 * 그대로 유효하다.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.FailingController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.FailingController.class})
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest
{

    @Autowired
    private MockMvc mockMvc;

    /** 예외가 스스로 들고 온 {@code code}·HTTP 상태가 그대로 응답에 실려야 한다. */
    @DisplayName("SettlementException은 자기 code와 HTTP 상태로 변환된다")
    @Test
    void convertsSettlementExceptionToItsOwnCodeAndStatus() throws Exception
    {
        mockMvc.perform(get("/test/duplicate"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(409))
                .andExpect(result -> assertThat(body(result))
                        .contains("\"code\":\"SETTLEMENT_ALREADY_CONFIRMED\""));

        mockMvc.perform(get("/test/invalid-transition"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(409))
                .andExpect(result -> assertThat(body(result))
                        .contains("\"code\":\"INVALID_STATE_TRANSITION\""));
    }

    @DisplayName("필수 파라미터가 빠지면 400 MISSING_REQUIRED_PARAMETER")
    @Test
    void convertsMissingParameterTo400() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/test/settlements")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).contains("\"code\":\"MISSING_REQUIRED_PARAMETER\"");
    }

    @DisplayName("날짜 형식이 틀리면 400 INVALID_DATE_FORMAT")
    @Test
    void convertsTypeMismatchTo400() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/test/settlements").param("date", "2026-13-99"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).contains("\"code\":\"INVALID_DATE_FORMAT\"");
    }

    /**
     * 내부 구조가 응답으로 새어 나가면 안 된다. 그렇다고 원인을 못 찾으면 곤란하므로
     * 스택트레이스는 <b>로그에만</b> 남는지 함께 본다.
     */
    @DisplayName("예상 못 한 예외는 500 INTERNAL_ERROR — 응답엔 스택트레이스가 없고 로그엔 있다")
    @Test
    void hidesStackTraceFromResponseButLogsIt(CapturedOutput output) throws Exception
    {
        MvcResult result = mockMvc.perform(get("/test/boom")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(body(result))
                .contains("\"code\":\"INTERNAL_ERROR\"")
                .doesNotContain("IllegalStateException")
                .doesNotContain("com.example.driversettlementsystem");

        assertThat(output).contains("java.lang.IllegalStateException: 예상하지 못한 실패")
                .contains("at com.example.driversettlementsystem");
    }

    @DisplayName("모든 에러 응답에 transactionId가 채워진다")
    @Test
    void fillsTransactionIdOnEveryErrorResponse() throws Exception
    {
        for (String path : new String[] {"/test/duplicate", "/test/settlements", "/test/boom"})
        {
            MvcResult result = mockMvc.perform(get(path)).andReturn();

            assertThat(body(result)).doesNotContain("\"transactionId\":null")
                    .containsPattern("\"transactionId\":\"[0-9a-f-]{36}\"");
        }
    }

    private static String body(MvcResult result) throws Exception
    {
        result.getResponse().setCharacterEncoding("UTF-8");
        return result.getResponse().getContentAsString();
    }

    /** 예외를 던지기만 하는 테스트 전용 컨트롤러. */
    @RestController
    static class FailingController
    {

        @GetMapping(value = "/test/settlements", produces = MediaType.APPLICATION_JSON_VALUE)
        String settlements(@RequestParam LocalDate date)
        {
            return date.toString();
        }

        @GetMapping("/test/duplicate")
        String duplicate()
        {
            throw new DuplicateSettlementException(LocalDate.of(2026, 8, 19));
        }

        @GetMapping("/test/invalid-transition")
        String invalidTransition()
        {
            throw new InvalidStateTransitionException(BatchStatus.PAID, BatchStatus.RUNNING);
        }

        @GetMapping("/test/boom")
        String boom()
        {
            throw new IllegalStateException("예상하지 못한 실패");
        }

    }

}
