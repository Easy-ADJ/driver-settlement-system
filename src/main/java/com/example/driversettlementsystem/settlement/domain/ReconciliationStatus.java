package com.example.driversettlementsystem.settlement.domain;

/**
 * 대사(reconciliation) 판정 결과.
 * <p>
 * <b>결제 서버의 전일 결제 합계</b>와 <b>원장 미지급금 합계</b>를 대조한 결과다.
 * 금액을 원장에서 받아왔으므로 원장을 원장과 비교하면 검증이 되지 않는다 —
 * 결제 서버가 독립된 두 번째 출처다.
 * <p>
 * 조회 API 응답에 그대로 노출되므로, 관리자가 "이 금액이 검증된 값인지" 판단하는
 * 근거가 된다.
 */
public enum ReconciliationStatus
{

    /** 두 합계가 일치한다. {@code CONFIRMED}로 전이할 수 있다. */
    MATCHED,

    /**
     * 두 합계가 어긋난다.
     * <p>
     * ⚠️ <b>{@code CONFIRMED}로 올리지 않고 보류한다.</b> 틀린 금액이 지급 단계로
     * 넘어가지 않게 막는 것이 이 값의 존재 이유다.
     */
    MISMATCHED,

    /** 대사를 수행하지 못했다. (예: 결제 서버 호출 실패) */
    SKIPPED

}
