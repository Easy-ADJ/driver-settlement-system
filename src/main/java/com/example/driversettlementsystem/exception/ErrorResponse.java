package com.example.driversettlementsystem.exception;

/**
 * 팀 공통 에러 응답 포맷.
 * <p>
 * 세 필드의 역할이 다르다.
 * <ul>
 *   <li>{@code code} — <b>계약</b>이다. 호출자는 이 값으로만 분기한다</li>
 *   <li>{@code message} — 계약이 아니다. 사람이 읽는 용도이며 언제든 문구가 바뀔 수 있다</li>
 *   <li>{@code transactionId} — 로그 추적용. 장애 문의가 오면 이 값으로 서버 로그를 찾는다</li>
 * </ul>
 *
 * @param code          에러 코드 (예: {@code SETTLEMENT_NOT_FOUND})
 * @param message       사람이 읽는 설명
 * @param transactionId 요청 추적 ID
 */
public record ErrorResponse(String code, String message, String transactionId)
{
}
