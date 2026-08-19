package com.example.driversettlementsystem.settlement.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.driversettlementsystem.TestcontainersConfiguration;
import com.example.driversettlementsystem.settlement.domain.Settlement;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * {@code SETTLEMENTS} 제약과 집계 쿼리를 실제 PostgreSQL에 대고 검증한다.
 *
 * @see SettlementBatchRepositoryTest 같은 이유로 인메모리 DB를 쓰지 않는다
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SettlementRepositoryTest
{

    private static final Long DRIVER_ID = 7L;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private SettlementBatchRepository batchRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long batchId;

    @BeforeEach
    void createBatch()
    {
        batchId = batchRepository.saveAndFlush(SettlementBatch.start(LocalDate.of(2026, 8, 19)))
                .getBatchId();
    }

    /**
     * 복합키를 PK로 둔 이유가 이 테스트다. 한 배치에 같은 기사가 두 줄 생기면 지급이
     * 두 번 나가는데, 대리키를 쓰면 그 사고가 저장까지 성공한 뒤 조회에서야 드러난다.
     * <p>
     * {@code save()}는 이미 있는 키를 만나면 merge로 UPDATE가 되므로 제약을 건드리지
     * 못한다. 영속성 컨텍스트를 비우고 {@code persist}로 INSERT를 강제해야 DB가 답한다.
     */
    @DisplayName("한 배치에 같은 기사를 두 줄 넣으면 PK 위반으로 거부된다")
    @Test
    void rejectsDuplicateDriverInSameBatch()
    {
        entityManager.persist(settlement(new BigDecimal("42000.00"), new BigDecimal("8400.00")));
        entityManager.flush();
        entityManager.clear();

        Settlement duplicate = settlement(new BigDecimal("10000.00"), new BigDecimal("2000.00"));

        assertThatThrownBy(() ->
        {
            entityManager.persist(duplicate);
            entityManager.flush();
        }).rootCause().hasMessageContaining("settlements_pkey");
    }

    @DisplayName("배치 ID와 (배치, 기사)로 정산 항목을 찾는다")
    @Test
    void findsSettlementsByBatchAndDriver()
    {
        settlementRepository.saveAndFlush(
                settlement(new BigDecimal("42000.00"), new BigDecimal("8400.00")));

        assertThat(settlementRepository.findByBatchId(batchId)).hasSize(1);

        assertThat(settlementRepository.findByBatchIdAndDriverId(batchId, DRIVER_ID))
                .get()
                .extracting(Settlement::getAmount, InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("33600.00");

        assertThat(settlementRepository.findByBatchIdAndDriverId(batchId, 999L)).isEmpty();
    }

    /**
     * 대사가 비교하는 값은 지급액이 아니라 운임 합계다. 두 합계를 따로 뽑을 수 있어야
     * "수수료를 뗀 값과 원장 미지급금을 비교해 늘 어긋나는" 실수를 피할 수 있다.
     */
    @DisplayName("지급액 합계와 운임 합계를 각각 집계한다")
    @Test
    void sumsAmountAndFareTotalSeparately()
    {
        settlementRepository.saveAndFlush(
                settlement(new BigDecimal("42000.00"), new BigDecimal("8400.00")));
        settlementRepository.saveAndFlush(Settlement.of(batchId, 8L,
                new BigDecimal("10000.00"), new BigDecimal("2000.00")));

        assertThat(settlementRepository.sumAmountByBatchId(batchId))
                .isEqualByComparingTo("41600.00");
        assertThat(settlementRepository.sumFareTotalByBatchId(batchId))
                .isEqualByComparingTo("52000.00");
    }

    /** 항목이 없을 때 null이 나오면 호출자가 매번 null을 다뤄야 한다. */
    @DisplayName("항목이 없는 배치의 합계는 null이 아니라 0이다")
    @Test
    void sumsAreZeroWhenBatchHasNoSettlements()
    {
        assertThat(settlementRepository.sumAmountByBatchId(batchId)).isEqualByComparingTo("0");
        assertThat(settlementRepository.sumFareTotalByBatchId(batchId)).isEqualByComparingTo("0");
    }

    private Settlement settlement(BigDecimal fareTotal, BigDecimal feeAmount)
    {
        return Settlement.of(batchId, DRIVER_ID, fareTotal, feeAmount);
    }

}
