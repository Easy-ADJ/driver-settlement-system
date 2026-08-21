package com.example.driversettlementsystem.settlement.service;

import com.example.driversettlementsystem.exception.ExternalServiceException;
import com.example.driversettlementsystem.exception.InvalidStateTransitionException;
import com.example.driversettlementsystem.exception.SettlementNotFoundException;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.Settlement;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import com.example.driversettlementsystem.settlement.dto.DriverSettlementResponse;
import com.example.driversettlementsystem.settlement.dto.SettlementResponse;
import com.example.driversettlementsystem.settlement.repository.SettlementBatchRepository;
import com.example.driversettlementsystem.settlement.repository.SettlementRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치를 다음 상태로 넘기는 두 전이. ({@code RUNNING → CONFIRMED → PAID})
 * <p>
 * <b>둘 다 사람이 명시적으로 불러야 일어난다.</b> 자동으로 넘기지 않는 이유는, 대사가
 * 불일치를 냈을 때 <b>보류된 배치를 사람이 확인하고 진행시키는 지점</b>이 필요하기 때문이다.
 * 확정과 지급을 한 번에 묶으면 그 확인 지점이 사라진다.
 * <p>
 * ⚠️ <b>확정이 원장 상쇄 분개를 남기는 자리다.</b> 이걸 빠뜨리면 미지급금이 줄지 않아
 * 다음날 배치가 같은 금액을 또 정산한다. 그리고 당일 결과만 보면 금액이 정확해서 눈에
 * 띄지 않는다.
 *
 * @see LedgerClient#recordPayoutEntry 상쇄 분개 기록
 */
@Service
@Transactional
public class SettlementLifecycleService
{

    private static final Logger log = LoggerFactory.getLogger(SettlementLifecycleService.class);

    private final SettlementBatchRepository batchRepository;

    private final SettlementRepository settlementRepository;

    private final LedgerClient ledgerClient;

    public SettlementLifecycleService(SettlementBatchRepository batchRepository,
                                      SettlementRepository settlementRepository,
                                      LedgerClient ledgerClient)
    {
        this.batchRepository = batchRepository;
        this.settlementRepository = settlementRepository;
        this.ledgerClient = ledgerClient;
    }

    /**
     * 배치를 확정한다. ({@code RUNNING → CONFIRMED})
     * <p>
     * <b>대사가 불일치를 냈어도 막지 않는다.</b> 이 API의 존재 이유가 <b>보류된 배치를 사람이
     * 확인한 뒤 진행시키는 것</b>이라서다. 여기서 거부하면 보류를 풀 방법이 없어진다.
     * <p>
     * 대신 {@code reconciliation_status}는 그대로 남는다. 나중에 레코드를 보면
     * <b>{@code CONFIRMED} + {@code MISMATCHED}</b> 조합이 "사람이 밀어붙인 확정"이라는 증거가
     * 된다 — 확정을 건너뛰고 바로 지급으로 보내는 방식(A안)을 택했다면 이 구분이 남지 않는다.
     * <p>
     * 상쇄 분개를 <b>먼저</b> 남기고 그다음 확정한다. 순서를 뒤집으면 원장 기록이 실패해도
     * 배치는 이미 확정돼 있어, <b>미지급금이 남은 채로 확정된 배치</b>가 생긴다.
     *
     * @param batchId 확정할 배치
     * @return 확정 후 상태
     * @throws SettlementNotFoundException     없는 배치일 때
     * @throws InvalidStateTransitionException {@code RUNNING}이 아닐 때 (이미 확정·지급됐다)
     * @throws ExternalServiceException        원장 상쇄 분개 기록이 재시도 후에도 실패했을 때
     */
    public SettlementResponse confirm(Long batchId)
    {
        SettlementBatch batch = findBatch(batchId);

        // 원장을 부르기 전에 거부한다. 어차피 거부될 요청으로 원장에 부하를 주지 않는다.
        batch.getStatus().validateTransitionTo(BatchStatus.CONFIRMED);

        List<Settlement> settlements = settlementRepository.findByBatchId(batchId);

        recordPayoutEntries(batchId, settlements);

        batch.transitionTo(BatchStatus.CONFIRMED);

        log.info("정산 배치 확정 — batchId={}, 기사 수={}, 대사={}",
                batchId, settlements.size(), batch.getReconciliationStatus());

        return responseOf(batch, settlements);
    }

    /**
     * 배치를 지급 완료로 표시한다. ({@code CONFIRMED → PAID})
     * <p>
     * <b>실제 송금은 일어나지 않는다.</b> 데모 범위에서 {@code PAID}는 "정산 처리 완료"
     * 표식이다. PG·계좌 이체 연동을 전제한 코드를 여기 넣지 않는다.
     * <p>
     * 이미 {@code PAID}인 배치에 다시 부르면 {@link InvalidStateTransitionException}이 나간다
     * ({@code PAID}에서 갈 수 있는 다음 상태가 없다). 같은 결과를 돌려주는 대신 거부하는
     * 쪽을 택한 이유는, <b>두 번 눌렀다는 사실 자체를 호출자가 알아야</b> 하기 때문이다.
     *
     * @param batchId 지급 표시할 배치
     * @return 전이 후 상태
     * @throws SettlementNotFoundException     없는 배치일 때
     * @throws InvalidStateTransitionException {@code CONFIRMED}가 아닐 때
     */
    public SettlementResponse markAsPaid(Long batchId)
    {
        SettlementBatch batch = findBatch(batchId);

        batch.transitionTo(BatchStatus.PAID);

        List<Settlement> settlements = settlementRepository.findByBatchId(batchId);
        settlements.forEach(Settlement::markPaid);

        log.info("정산 배치 지급 완료 표시 — batchId={}, 기사 수={}", batchId, settlements.size());

        return responseOf(batch, settlements);
    }

    /**
     * 기사별로 원장에 지급 상쇄 분개를 남기고 그 원장 ID를 정산 항목에 연결한다.
     * <p>
     * 상쇄 금액은 <b>지급액이 아니라 운임 합계</b>다. 수수료를 뺀 금액만 상쇄하면 그 수수료가
     * 미지급금으로 남아 다음날 배치가 이 기사를 또 선별한다.
     * <p>
     * 중간에 실패하면 예외가 그대로 올라가 <b>트랜잭션 전체가 롤백되고 배치는 확정되지
     * 않는다.</b> 원장에 이미 기록된 앞쪽 기사들은 남지만, 멱등 키가
     * {@code batchId + driverId}라서 재실행하면 원장이 걸러내고 나머지만 기록한다.
     *
     * @param batchId     확정 중인 배치
     * @param settlements 이 배치의 정산 항목
     * @throws ExternalServiceException 재시도 후에도 원장 기록이 실패했을 때
     */
    private void recordPayoutEntries(Long batchId, List<Settlement> settlements)
    {
        for (Settlement settlement : settlements)
        {
            Long ledgerId = ledgerClient.recordPayoutEntry(
                    batchId, settlement.getDriverId(), settlement.getFareTotal());

            settlement.linkLedgerEntry(ledgerId);
        }
    }

    /**
     * @param batchId 찾을 배치 ID
     * @return 배치
     * @throws SettlementNotFoundException 없는 배치일 때
     */
    private SettlementBatch findBatch(Long batchId)
    {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> SettlementNotFoundException.batchIdNotFound(batchId));
    }

    /**
     * 전이 후 상태를 조회 API와 같은 형태로 조립한다.
     * <p>
     * {@code payments}(결제 건별 근거)는 채우지 않는다. 채우려면 기사마다 원장을 한 번씩 더
     * 불러야 하는데, <b>상태를 바꾼 직후에 필요한 정보가 아니다</b> — 필요하면 조회 API에
     * {@code driverId}를 붙여 부르면 된다.
     *
     * @param batch       전이한 배치
     * @param settlements 이 배치의 정산 항목
     * @return 조회 API와 같은 형태의 응답
     */
    private static SettlementResponse responseOf(SettlementBatch batch, List<Settlement> settlements)
    {
        return new SettlementResponse(
                batch.getTargetDate(),
                batch.getStatus(),
                batch.getReconciliationStatus(),
                settlements.stream()
                        .map(settlement -> DriverSettlementResponse.from(settlement, List.of()))
                        .toList());
    }

}
