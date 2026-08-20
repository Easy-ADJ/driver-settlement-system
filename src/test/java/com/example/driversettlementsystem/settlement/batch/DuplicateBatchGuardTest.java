package com.example.driversettlementsystem.settlement.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.driversettlementsystem.TestcontainersConfiguration;
import com.example.driversettlementsystem.exception.DuplicateSettlementException;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import com.example.driversettlementsystem.settlement.repository.SettlementBatchRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * 사전 검사기가 무엇을 막고 무엇을 통과시키는지 확인한다. ({@code FR-B-06})
 * <p>
 * <b>DB 부분 UNIQUE 인덱스는 여기서 다시 검증하지 않는다.</b> 그쪽은
 * {@code SettlementBatchRepositoryTest}가 맡는다. 두 방어선은 역할이 달라서 —
 * 하나는 빠르고 친절한 거부, 하나는 경합에 뚫리지 않는 최종 저지 — 테스트도 나눈다.
 * <p>
 * 실제 PostgreSQL 위에서 돈다. 인메모리 DB로 바꾸면 "재실행 허용"이 같은 스키마·같은
 * 인덱스 위에서도 성립하는지를 확인하지 못한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, DuplicateBatchGuard.class})
class DuplicateBatchGuardTest
{

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 19);

    private static final LocalDate OTHER_DATE = LocalDate.of(2026, 8, 20);

    @Autowired
    private DuplicateBatchGuard duplicateBatchGuard;

    @Autowired
    private SettlementBatchRepository batchRepository;

    @DisplayName("이미 확정된 날짜는 SETTLEMENT_ALREADY_CONFIRMED로 거부한다")
    @Test
    void rejectsConfirmedDate()
    {
        batchRepository.saveAndFlush(confirmedBatch(TARGET_DATE));

        assertThatThrownBy(() -> duplicateBatchGuard.verifyNotConfirmed(TARGET_DATE))
                .isInstanceOf(DuplicateSettlementException.class)
                .satisfies(thrown ->
                {
                    DuplicateSettlementException e = (DuplicateSettlementException) thrown;
                    assertThat(e.getCode()).isEqualTo("SETTLEMENT_ALREADY_CONFIRMED");
                    assertThat(e.getTargetDate()).isEqualTo(TARGET_DATE);
                });
    }

    /**
     * {@code FR-B-06}의 수락 기준이 그대로 여기다. 시작한 뒤 롤백하는 방식이면 이 건수가
     * 늘어난다. <b>거부는 흔적을 남기지 않아야 한다.</b>
     */
    @DisplayName("거부된 실행은 BATCHES에 새 레코드를 남기지 않는다")
    @Test
    void rejectionCreatesNoBatchRecord()
    {
        batchRepository.saveAndFlush(confirmedBatch(TARGET_DATE));
        long countBefore = batchRepository.count();

        assertThatThrownBy(() -> duplicateBatchGuard.verifyNotConfirmed(TARGET_DATE))
                .isInstanceOf(DuplicateSettlementException.class);

        assertThat(batchRepository.count()).isEqualTo(countBefore);
    }

    /**
     * 실패한 배치를 다시 못 돌리면 그날 정산을 영영 못 한다. {@code FAILED}를 종료 상태로
     * 두지 않은 이유({@code FR-B-07})가 여기서 실제로 쓰인다.
     */
    @DisplayName("RUNNING·FAILED 이력만 있는 날짜는 재실행을 허용한다")
    @Test
    void allowsRetryAfterFailure()
    {
        SettlementBatch failed = SettlementBatch.start(TARGET_DATE);
        failed.transitionTo(BatchStatus.FAILED);
        batchRepository.saveAndFlush(failed);
        batchRepository.saveAndFlush(SettlementBatch.start(TARGET_DATE));

        assertThatCode(() -> duplicateBatchGuard.verifyNotConfirmed(TARGET_DATE))
                .doesNotThrowAnyException();
    }

    @DisplayName("다른 날짜가 확정돼 있어도 영향받지 않는다")
    @Test
    void otherDatesAreUnaffected()
    {
        batchRepository.saveAndFlush(confirmedBatch(OTHER_DATE));

        assertThatCode(() -> duplicateBatchGuard.verifyNotConfirmed(TARGET_DATE))
                .doesNotThrowAnyException();
    }

    private static SettlementBatch confirmedBatch(LocalDate targetDate)
    {
        SettlementBatch batch = SettlementBatch.start(targetDate);
        batch.transitionTo(BatchStatus.CONFIRMED);
        return batch;
    }

}
