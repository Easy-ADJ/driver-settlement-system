package com.example.driversettlementsystem.settlement.batch;

import com.example.driversettlementsystem.exception.DuplicateSettlementException;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.repository.SettlementBatchRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 같은 날짜의 정산이 두 번 확정되는 것을 막는 사전 검사기. ({@code FR-B-06})
 * <p>
 * <b>Job이 시작되기 전에</b> 호출된다. 시작한 뒤 롤백하면 {@code BATCHES}에 쓸모없는
 * 레코드가 남고 Spring Batch 메타 테이블에도 실행 이력이 쌓인다. 수락 기준이
 * "새 레코드가 생성되지 않는다"인 이유다.
 * <p>
 * ⚠️ <b>이 검사만으로는 부족하다.</b> 조회와 삽입 사이에 다른 프로세스가 끼어들면 둘 다
 * 검사를 통과한다. 최종 방어선은 {@code BATCHES}의 부분 UNIQUE 인덱스
 * ({@code uq_batches_confirmed_date})이며, 이 클래스는 <b>빠르고 친절한 거부</b>를 맡는다.
 * 결제 서버의 {@code idempotency_key} UNIQUE와 같은 구조다.
 *
 * @see DuplicateSettlementException 거부 시 던지는 예외
 */
@Component
public class DuplicateBatchGuard
{

    private final SettlementBatchRepository batchRepository;

    public DuplicateBatchGuard(SettlementBatchRepository batchRepository)
    {
        this.batchRepository = batchRepository;
    }

    /**
     * 해당 날짜로 정산을 시작해도 되는지 검사한다.
     * <p>
     * {@code CONFIRMED}인 배치만 중복으로 본다. {@code RUNNING}·{@code FAILED}로 끝난
     * 날짜는 <b>재실행이 허용돼야 한다</b> — 실패한 배치를 다시 못 돌리면 그날 정산을
     * 영영 못 한다.
     * <p>
     * {@code CONFIRMED}만 조회하므로 결과는 최대 1건이다. 부분 UNIQUE 인덱스가 그 상태의
     * 행을 날짜당 하나로 강제하기 때문에, 같은 날짜의 재시도 이력이 여러 건 남아 있어도
     * 여기서는 걸리지 않는다.
     *
     * @param targetDate 정산 대상 일자
     * @throws DuplicateSettlementException 이미 확정된 배치가 있을 때
     */
    public void verifyNotConfirmed(LocalDate targetDate)
    {
        batchRepository.findByTargetDateAndStatus(targetDate, BatchStatus.CONFIRMED)
                .ifPresent(confirmed ->
                {
                    throw new DuplicateSettlementException(targetDate);
                });
    }

}
