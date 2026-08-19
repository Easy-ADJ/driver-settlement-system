package com.example.driversettlementsystem.settlement.repository;

import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code BATCHES} 접근.
 * <p>
 * 여기의 조회는 중복 실행 판정에 쓰이지만, <b>판정만으로 중복을 막지는 못한다.</b>
 * 검사와 삽입 사이에 다른 프로세스가 끼어들 수 있어 최종 방어는 부분 UNIQUE 인덱스
 * {@code uq_batches_confirmed_date}가 한다.
 */
public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, Long>
{

    /**
     * 해당 일자에 특정 상태인 배치를 찾는다.
     * <p>
     * {@code DuplicateBatchGuard}가 {@code (targetDate, CONFIRMED)}로 호출해
     * Job 시작 여부를 판정한다.
     *
     * @param targetDate 정산 대상 일자
     * @param status     찾으려는 상태
     * @return 해당 배치. 없으면 빈 {@link Optional}
     */
    Optional<SettlementBatch> findByTargetDateAndStatus(LocalDate targetDate, BatchStatus status);

    /**
     * 해당 일자의 가장 최근 배치를 찾는다.
     * <p>
     * 같은 날짜에 실패·재시도 이력이 여러 건 남을 수 있으므로 조회 API는 최신 1건만 본다.
     *
     * @param targetDate 정산 대상 일자
     * @return 실행 시각이 가장 늦은 배치. 없으면 빈 {@link Optional}
     */
    Optional<SettlementBatch> findFirstByTargetDateOrderByExecutedAtDesc(LocalDate targetDate);

}
