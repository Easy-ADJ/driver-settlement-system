package com.example.driversettlementsystem.settlement.service;

import com.example.driversettlementsystem.exception.SettlementNotFoundException;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import com.example.driversettlementsystem.settlement.client.PaymentDetail;
import com.example.driversettlementsystem.settlement.domain.Settlement;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import com.example.driversettlementsystem.settlement.dto.DriverSettlementResponse;
import com.example.driversettlementsystem.settlement.dto.SettlementResponse;
import com.example.driversettlementsystem.settlement.repository.SettlementBatchRepository;
import com.example.driversettlementsystem.settlement.repository.SettlementRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 내역 조회. ({@code FR-Q-01})
 * <p>
 * 이 서비스의 책임은 <b>"계산 근거가 보이는 응답으로 조립하는 것"</b>이다. 항목만 돌려주면
 * 관리자는 그 금액이 검증된 값인지(대사 결과), 확정된 값인지(배치 상태) 알 수 없다. 그래서
 * 배치 정보와 기사별 상세를 합쳐서 준다.
 * <p>
 * 조회 전용이므로 {@code @Transactional(readOnly = true)}를 붙인다 — 더티 체킹을 건너뛰어
 * 가볍고, 실수로 쓰기가 일어나는 것도 막는다.
 *
 * @see SettlementResponse 조립 결과
 */
@Service
@Transactional(readOnly = true)
public class SettlementQueryService
{

    private final SettlementBatchRepository batchRepository;

    private final SettlementRepository settlementRepository;

    private final LedgerClient ledgerClient;

    public SettlementQueryService(SettlementBatchRepository batchRepository,
                                  SettlementRepository settlementRepository,
                                  LedgerClient ledgerClient)
    {
        this.batchRepository = batchRepository;
        this.settlementRepository = settlementRepository;
        this.ledgerClient = ledgerClient;
    }

    /**
     * 정산 내역을 조회한다.
     * <p>
     * <b>배치를 먼저 한 번 찾고 그 {@code batchId}로 항목을 읽는다.</b> 순서를 뒤집어 항목마다
     * 배치를 되묻는 구조로 만들면 기사 수만큼 쿼리가 늘어난다. 이 순서면 DB 쿼리는 기사가
     * 몇 명이든 2번이다.
     * <p>
     * 같은 날짜에 배치가 여러 건 남아 있을 수 있다 — 실패한 배치는 재실행할 수 있어야 하므로
     * {@code FAILED}·{@code RUNNING} 이력이 쌓인다. <b>가장 최근 것 하나만 본다.</b>
     *
     * @param date     정산 대상 일자 (필수)
     * @param driverId 기사 ID. {@code null}이면 그날 전체 기사이며, 이때 {@code payments}는
     *                 빈 배열이다 (아래 {@link #paymentDetailsOf} 참고)
     * @return 배치 정보 + 기사별 상세
     * @throws SettlementNotFoundException 해당 일자의 배치가 없거나 그 기사의 항목이 없을 때
     */
    public SettlementResponse findSettlements(LocalDate date, Long driverId)
    {
        SettlementBatch batch = batchRepository.findFirstByTargetDateOrderByExecutedAtDesc(date)
                .orElseThrow(() -> SettlementNotFoundException.batchNotFound(date));

        List<Settlement> settlements = findSettlementsOf(batch.getBatchId(), date, driverId);

        List<DriverSettlementResponse> details = settlements.stream()
                .map(settlement -> DriverSettlementResponse.from(
                        settlement, paymentDetailsOf(settlement.getDriverId(), driverId)))
                .toList();

        return new SettlementResponse(
                batch.getTargetDate(),
                batch.getStatus(),
                batch.getReconciliationStatus(),
                details);
    }

    /**
     * 기사를 지정했으면 그 한 건만, 아니면 배치 전체를 읽는다.
     *
     * @param batchId  조회할 배치
     * @param date     예외 메시지에 쓸 일자
     * @param driverId 기사 ID. {@code null}이면 전체
     * @return 정산 항목 목록
     */
    private List<Settlement> findSettlementsOf(Long batchId, LocalDate date, Long driverId)
    {
        if (driverId == null)
        {
            return settlementRepository.findByBatchId(batchId);
        }

        return List.of(settlementRepository.findByBatchIdAndDriverId(batchId, driverId)
                .orElseThrow(() -> SettlementNotFoundException.driverNotFound(date, driverId)));
    }

    /**
     * 결제 건별 근거를 원장에서 가져온다.
     * <p>
     * ⚠️ <b>전체 조회에서는 원장을 부르지 않는다.</b> {@code payments}는 정산 DB에 없어서
     * 기사마다 {@code GET /api/ledger?driver_id=}를 불러야 채워지는데, 기사 100명이면 조회
     * 한 번에 원장 호출 100번이고 각 호출에 응답 타임아웃 10초가 걸려 있다.
     * <p>
     * 실제 사용 흐름이 <b>"목록에서 훑고 → 한 명을 눌러 상세를 본다"</b>라서, 목록에서
     * 기사 전원의 결제 내역을 펼칠 일이 없다. 대신 <b>같은 필드가 상황에 따라 다르게 찬다</b>는
     * 것이 대가이며, 그래서 API 명세에 명시했다.
     *
     * @param settlementDriverId 지금 조립 중인 항목의 기사
     * @param requestedDriverId  요청이 지정한 기사. {@code null}이면 전체 조회다
     * @return 결제 건별 내역. 전체 조회면 빈 목록
     */
    private List<PaymentDetail> paymentDetailsOf(Long settlementDriverId, Long requestedDriverId)
    {
        if (requestedDriverId == null)
        {
            return List.of();
        }

        return ledgerClient.findDriverLedger(settlementDriverId).paymentDetails();
    }

}
