package com.example.driversettlementsystem.settlement.repository;

import com.example.driversettlementsystem.settlement.domain.Settlement;
import com.example.driversettlementsystem.settlement.domain.SettlementId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code SETTLEMENTS} 접근.
 * <p>
 * 복합키 엔티티라 ID 타입이 {@link SettlementId}다.
 */
public interface SettlementRepository extends JpaRepository<Settlement, SettlementId>
{

    /**
     * 배치에 속한 정산 항목 전체.
     *
     * @param batchId 배치 ID
     * @return 정산 항목 목록. 없으면 빈 목록
     */
    List<Settlement> findByBatchId(Long batchId);

    /**
     * 배치 안에서 기사 1명의 정산 항목.
     *
     * @param batchId  배치 ID
     * @param driverId 기사 ID
     * @return 해당 항목. 없으면 빈 {@link Optional}
     */
    Optional<Settlement> findByBatchIdAndDriverId(Long batchId, Long driverId);

    /**
     * 배치의 지급액 총합.
     * <p>
     * 항목을 전부 메모리로 읽어 합산하면 기사 수가 늘 때 부담이 커지므로 DB에서 집계한다.
     * 항목이 없으면 {@code null}이 아니라 0이 나온다 — 호출자가 null을 다루지 않게 한다.
     *
     * @param batchId 배치 ID
     * @return 지급액 합계. 항목이 없으면 0
     */
    @Query("select coalesce(sum(s.amount), 0) from Settlement s where s.batchId = :batchId")
    BigDecimal sumAmountByBatchId(@Param("batchId") Long batchId);

    /**
     * 배치의 운임 합계.
     * <p>
     * 대사가 결제·원장 합계와 비교할 때 쓴다. <b>비교 대상은 지급액이 아니라
     * 운임 합계다</b> — 수수료를 뗀 뒤 값과 원장 미지급금을 비교하면 당연히 안 맞는다.
     *
     * @param batchId 배치 ID
     * @return 운임 합계. 항목이 없으면 0
     */
    @Query("select coalesce(sum(s.fareTotal), 0) from Settlement s where s.batchId = :batchId")
    BigDecimal sumFareTotalByBatchId(@Param("batchId") Long batchId);

}
