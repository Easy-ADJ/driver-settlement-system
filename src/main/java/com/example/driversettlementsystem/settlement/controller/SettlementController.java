package com.example.driversettlementsystem.settlement.controller;

import com.example.driversettlementsystem.settlement.dto.SettlementResponse;
import com.example.driversettlementsystem.settlement.service.SettlementQueryService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정산 내역 조회 API. ({@code FR-Q-01})
 * <p>
 * <b>정산 서버가 외부에 제공하는 유일한 엔드포인트다.</b> 대시보드는 원장 서버만 보기로 해서
 * 지금 이 API를 부르는 클라이언트는 없지만, <b>정산 결과를 사람이 확인할 수단이 이것뿐이다</b> —
 * 없으면 DB를 직접 열어보는 것 말고 방법이 없다.
 * <p>
 * 컨트롤러는 <b>HTTP를 자바 호출로 옮기기만 한다.</b> 조회 로직은
 * {@link SettlementQueryService}, 에러 변환은 {@code GlobalExceptionHandler}가 맡는다. 여기에
 * {@code try-catch}나 조건 분기가 쌓이기 시작하면 책임이 새고 있는 것이다.
 *
 * @see SettlementQueryService 실제 조회를 수행하는 곳
 */
@RestController
@RequestMapping("/api/settlements")
public class SettlementController
{

    private final SettlementQueryService queryService;

    public SettlementController(SettlementQueryService queryService)
    {
        this.queryService = queryService;
    }

    /**
     * 정산 내역을 조회한다.
     * <p>
     * <b>여기서 파라미터를 직접 검증하지 않는다.</b> {@code date}가 없으면 Spring이
     * {@code MissingServletRequestParameterException}을, 형식이 틀리면
     * {@code MethodArgumentTypeMismatchException}을 던지고, 둘 다
     * {@code GlobalExceptionHandler}가 우리 에러 코드로 바꾼다. 손으로 검증하면 엔드포인트가
     * 늘어날 때마다 같은 코드가 복사된다.
     *
     * @param date     정산 대상 일자. {@code yyyy-MM-dd} 형식이며 필수다
     * @param driverId 기사 ID. 생략하면 해당 일자 전체 기사이며, 이때 응답의 {@code payments}는
     *                 빈 배열이다 — 채우려면 기사마다 원장을 불러야 해서다
     * @return 200 OK + 정산 내역
     */
    @GetMapping
    public SettlementResponse getSettlements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long driverId)
    {
        return queryService.findSettlements(date, driverId);
    }

}
