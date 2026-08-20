package com.example.driversettlementsystem.settlement.batch;

import com.example.driversettlementsystem.settlement.domain.Settlement;
import jakarta.persistence.EntityManager;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * 청크 하나분의 정산 항목을 저장한다.
 * <p>
 * <b>여기서 항목을 고치지 않는다.</b> {@code batchId}·금액·상태는 전부
 * {@code Settlement.of()}가 생성 시점에 정한다. Writer가 값을 보정하려 들면 같은 규칙이
 * 두 곳에 생기고, 그 둘이 어긋나는 날 조회 응답이 거짓말을 시작한다.
 * <p>
 * ⚠️ <b>{@code SettlementRepository.saveAll()}을 쓰지 않는 이유.</b> {@link Settlement}는
 * 복합키를 <b>직접 할당</b>받는다({@code @IdClass}, {@code @GeneratedValue} 없음). 그래서
 * Spring Data는 ID가 채워진 엔티티를 "이미 있는 것"으로 보고 {@code save()}에서 merge —
 * 즉 <b>SELECT 후 UPDATE</b>를 한다. 같은 {@code (batchId, driverId)}가 두 번 오면 제약을
 * 건드리지도 못하고 <b>조용히 덮어써서 정산 한 건이 사라진다.</b> {@code persist()}는 INSERT를
 * 강제하므로 DB가 답한다 — 시끄럽게 실패하는 것이 의도된 것이다.
 * <p>
 * 트랜잭션은 Step의 청크 경계가 관리한다. 이 클래스에 {@code @Transactional}을 붙이면 청크
 * 트랜잭션과 겹쳐 롤백 범위가 헷갈리므로 붙이지 않는다.
 */
@Component
public class SettlementWriter implements ItemWriter<Settlement>
{

    private final EntityManager entityManager;

    public SettlementWriter(EntityManager entityManager)
    {
        this.entityManager = entityManager;
    }

    /**
     * 청크 하나분을 저장한다.
     * <p>
     * INSERT는 {@code persist()} 시점이 아니라 <b>청크 트랜잭션이 커밋될 때</b> 나간다.
     * 복합 PK 위반도 그때 드러나고, 그 청크만 롤백된다. 이전 청크는 이미 커밋돼 남는다 —
     * 기사 1,000명 중 999번째에서 실패해도 998명분이 살아 있는 이유다.
     *
     * @param chunk 저장할 정산 항목들. Processor가 {@code null}을 반환한 기사는 여기
     *              들어오지 않는다
     */
    @Override
    public void write(Chunk<? extends Settlement> chunk)
    {
        chunk.forEach(entityManager::persist);
    }

}
