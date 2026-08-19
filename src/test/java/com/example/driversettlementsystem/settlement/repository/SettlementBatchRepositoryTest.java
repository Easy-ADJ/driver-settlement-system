package com.example.driversettlementsystem.settlement.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.driversettlementsystem.TestcontainersConfiguration;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * {@code BATCHES} 제약과 조회를 실제 PostgreSQL에 대고 검증한다.
 * <p>
 * <b>인메모리 DB로 대체하지 않는다.</b> 이 테스트의 핵심인 부분 UNIQUE 인덱스는
 * PostgreSQL 기능이라, 다른 DB로 바꾸면 정작 확인해야 할 것을 확인하지 못한다.
 * 테이블은 Flyway 마이그레이션이 만들고 {@code ddl-auto=validate}가 엔티티와 대조하므로,
 * 이 클래스가 뜨는 것 자체가 매핑 일치의 증거이기도 하다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SettlementBatchRepositoryTest
{

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 19);

    @Autowired
    private SettlementBatchRepository batchRepository;

    /**
     * 애플리케이션 사전검사가 아니라 <b>DB가</b> 막는지 확인한다. 두 프로세스가 동시에
     * 검사를 통과하는 경합에서 마지막까지 남는 방어선이 이것뿐이다.
     */
    @DisplayName("같은 날짜로 CONFIRMED 배치를 두 건 넣으면 DB가 거부한다")
    @Test
    void rejectsSecondConfirmedBatchOnSameDate()
    {
        batchRepository.saveAndFlush(confirmedBatch(TARGET_DATE));

        SettlementBatch duplicate = confirmedBatch(TARGET_DATE);

        assertThatThrownBy(() -> batchRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("uq_batches_confirmed_date");
    }

    /**
     * 실패한 배치는 다시 돌려야 하므로 같은 날짜에 이력이 여러 건 남는다. 인덱스가
     * 날짜 전체에 걸려 있으면 재시도 자체가 불가능해진다.
     */
    @DisplayName("같은 날짜라도 RUNNING·FAILED 이력은 여러 건 남는다")
    @Test
    void allowsMultipleUnconfirmedBatchesOnSameDate()
    {
        SettlementBatch failed = SettlementBatch.start(TARGET_DATE);
        failed.transitionTo(BatchStatus.FAILED);

        assertThatCode(() ->
        {
            batchRepository.saveAndFlush(failed);
            batchRepository.saveAndFlush(SettlementBatch.start(TARGET_DATE));
            batchRepository.saveAndFlush(SettlementBatch.start(TARGET_DATE));
        }).doesNotThrowAnyException();
    }

    @DisplayName("findByTargetDateAndStatus는 그 날짜의 해당 상태 배치만 찾는다")
    @Test
    void findsBatchByTargetDateAndStatus()
    {
        batchRepository.saveAndFlush(SettlementBatch.start(TARGET_DATE));
        SettlementBatch confirmed = batchRepository.saveAndFlush(confirmedBatch(TARGET_DATE));

        assertThat(batchRepository.findByTargetDateAndStatus(TARGET_DATE, BatchStatus.CONFIRMED))
                .get()
                .extracting(SettlementBatch::getBatchId)
                .isEqualTo(confirmed.getBatchId());

        assertThat(batchRepository.findByTargetDateAndStatus(TARGET_DATE, BatchStatus.PAID))
                .isEmpty();
    }

    @DisplayName("findFirstByTargetDateOrderByExecutedAtDesc는 가장 최근 배치를 찾는다")
    @Test
    void findsMostRecentBatchOfDate() throws InterruptedException
    {
        batchRepository.saveAndFlush(SettlementBatch.start(TARGET_DATE));

        // executedAt은 start()가 Instant.now()로 채운다. 연속 호출이 같은 시각으로
        // 찍히면 정렬 결과가 갈리므로, 두 배치의 시각을 확실히 벌린다.
        Thread.sleep(2);

        SettlementBatch later = batchRepository.saveAndFlush(SettlementBatch.start(TARGET_DATE));

        assertThat(batchRepository.findFirstByTargetDateOrderByExecutedAtDesc(TARGET_DATE))
                .get()
                .extracting(SettlementBatch::getBatchId)
                .isEqualTo(later.getBatchId());
    }

    private static SettlementBatch confirmedBatch(LocalDate targetDate)
    {
        SettlementBatch batch = SettlementBatch.start(targetDate);
        batch.transitionTo(BatchStatus.CONFIRMED);
        return batch;
    }

}
