package com.example.driversettlementsystem.settlement.client;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 배치가 기사 1명분으로 읽어 들이는 미지급 정보. 청크의 입력 항목이다.
 * <p>
 * 원장 {@code GET /api/ledger/unpaid?date=} 응답의 {@code data[]} 한 줄에 대응한다.
 * <b>정산이 소유한 타입이지 원장의 DTO가 아니다</b> — 원장이 필드를 늘려도 여기는
 * 그대로 둔다.
 * <p>
 * <b>목록 응답이 금액까지 담고 있어 기사마다 되묻지 않는다.</b> ID만 받아 되물으면
 * 기사 100명에 호출이 101번이 된다.
 * <p>
 * <b>처리 단위가 기사 1명인 것이 중요하다.</b> 결제 1건 단위로 읽으면 청크 경계가 한 기사
 * 한가운데 떨어져 같은 기사에게 정산이 두 줄 생길 수 있고, 그때 합계는 정확히 맞아서
 * 대사도 금액 검증도 전부 통과한다. 원장이 기사 단위로 답하므로 그 위험이 구조적으로
 * 사라졌다. 마지막 방어선은 {@code SETTLEMENTS}의 복합 PK {@code (batch_id, driver_id)}다.
 *
 * @param driverId          기사 ID
 * @param totalUnpaidAmount 수수료 차감 <b>전</b> 운임 합계. 원장이 절댓값(양수)으로 준다
 * @param lastApprovedAt    가장 마지막 결제 승인 시각. 대사에서 결제 응답과 대조한다
 */
public record DriverUnpaid(
        Long driverId,
        BigDecimal totalUnpaidAmount,
        Instant lastApprovedAt)
{
}
