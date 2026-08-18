package com.example.driversettlementsystem.settlement.domain;

/**
 * 기사 개별 정산 항목의 지급 상태.
 * <p>
 * 배치 전체 상태인 {@link BatchStatus}와 값 이름이 겹치지만 대상이 다르다.
 * 배치는 "그날 정산 작업 전체", 이쪽은 "기사 1명분 지급액 1건"이다.
 */
public enum PayoutStatus
{

    /** 금액이 확정됐다. */
    CONFIRMED,

    /** 지급 완료로 표시됐다. */
    PAID

}
