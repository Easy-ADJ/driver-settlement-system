package com.example.driversettlementsystem.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.driversettlementsystem.TestcontainersConfiguration;
import com.example.driversettlementsystem.auth.AuthDataSourceTestConfiguration;
import com.example.driversettlementsystem.exception.SettlementNotFoundException;
import com.example.driversettlementsystem.settlement.client.DriverLedger;
import com.example.driversettlementsystem.settlement.client.LedgerClient;
import com.example.driversettlementsystem.settlement.client.PaymentDetail;
import com.example.driversettlementsystem.settlement.domain.BatchStatus;
import com.example.driversettlementsystem.settlement.domain.Settlement;
import com.example.driversettlementsystem.settlement.domain.SettlementBatch;
import com.example.driversettlementsystem.settlement.dto.SettlementResponse;
import com.example.driversettlementsystem.settlement.repository.SettlementBatchRepository;
import com.example.driversettlementsystem.settlement.repository.SettlementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 조회가 무엇을 합쳐서 주는지, 그리고 <b>언제 원장을 부르지 않는지</b> 확인한다.
 * <p>
 * 후자가 이 이슈의 핵심이다. `payments`는 정산 DB에 없어 기사마다 원장을 불러야 채워지는데,
 * 전체 조회에서 그걸 하면 기사 수만큼 호출이 늘어난다. <b>호출하지 않는다는 것은 코드를
 * 읽어서는 확신할 수 없고, {@code never()}로 단언해야 회귀가 잡힌다.</b>
 */
@Import({TestcontainersConfiguration.class, AuthDataSourceTestConfiguration.class})
@SpringBootTest(properties = {
        "settlement.client.payment.base-url=http://payment.test",
        "settlement.client.ledger.base-url=http://ledger.test"})
class SettlementQueryServiceTest
{

    /**
     * 테스트마다 날짜를 다르게 쓴다. {@code BATCHES}에는 <b>같은 날짜의 {@code CONFIRMED}가
     * 하나만</b> 들어갈 수 있어(부분 UNIQUE 인덱스 {@code uq_batches_confirmed_date}), 날짜를
     * 공유하면 두 번째 픽스처가 제약에 걸린다.
     */
    private static final LocalDate FULL_QUERY_DATE = LocalDate.of(2026, 11, 1);

    private static final LocalDate RETRY_DATE = LocalDate.of(2026, 11, 2);

    private static final LocalDate EMPTY_DATE = LocalDate.of(2026, 11, 3);

    private static final LocalDate SINGLE_DRIVER_DATE = LocalDate.of(2026, 11, 4);

    private static final LocalDate STATUS_DATE = LocalDate.of(2026, 11, 5);

    @MockitoBean
    private LedgerClient ledgerClient;

    @Autowired
    private SettlementQueryService queryService;

    @Autowired
    private SettlementBatchRepository batchRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @BeforeEach
    void setUp()
    {
        when(ledgerClient.findDriverLedger(any())).thenReturn(
                new DriverLedger(1L, new BigDecimal("20000"), List.of(
                        new PaymentDetail(100L, new BigDecimal("15000"),
                                Instant.parse("2026-11-01T14:30:00Z")),
                        new PaymentDetail(101L, new BigDecimal("5000"),
                                Instant.parse("2026-11-01T18:00:00Z")))));
    }

    /**
     * <b>이 테스트가 A안의 근거 전체다.</b> 기사 2명이면 원장 호출도 2번이 되는데, 기사가
     * 100명이면 100번이고 각 호출에 응답 타임아웃 10초가 걸려 있다.
     */
    @DisplayName("전체 조회는 원장을 한 번도 부르지 않고 payments를 비운다")
    @Test
    void doesNotCallLedgerOnFullQuery()
    {
        long batchId = givenConfirmedBatchWith(FULL_QUERY_DATE, 1L, 2L);

        SettlementResponse response = queryService.findSettlements(FULL_QUERY_DATE, null);

        verify(ledgerClient, never()).findDriverLedger(any());
        assertThat(response.settlements()).hasSize(2);
        assertThat(response.settlements()).allSatisfy(
                driver -> assertThat(driver.payments()).isEmpty());
        assertThat(batchId).isPositive();
    }

    @DisplayName("기사를 지정하면 그 기사 것만 나오고 결제 건별 근거가 채워진다")
    @Test
    void fillsPaymentsForSingleDriver()
    {
        givenConfirmedBatchWith(SINGLE_DRIVER_DATE, 1L, 2L);

        SettlementResponse response = queryService.findSettlements(SINGLE_DRIVER_DATE, 1L);

        verify(ledgerClient).findDriverLedger(1L);
        assertThat(response.settlements()).hasSize(1);
        assertThat(response.settlements().get(0).driverId()).isEqualTo(1L);
        assertThat(response.settlements().get(0).payments()).hasSize(2);
        assertThat(response.settlements().get(0).payments().get(0).paymentId()).isEqualTo(100L);
    }

    /**
     * 항목만 돌려주면 관리자는 그 금액이 <b>검증된 값인지</b>(대사) <b>확정된 값인지</b>(배치
     * 상태) 알 수 없다.
     * <p>
     * ⚠️ 지금은 {@code reconciliationStatus}가 항상 {@code null}이다 — 대사(#25)가 아직 없고,
     * {@code SettlementBatch}에 <b>그 값을 기록하는 메서드 자체가 없다.</b> 여기서 확인하는
     * 것은 "조회가 이 필드를 응답에 실어 나른다"까지다.
     */
    @DisplayName("응답에 배치 상태와 대사 결과 자리가 함께 담긴다")
    @Test
    void includesBatchAndReconciliationStatus()
    {
        givenConfirmedBatchWith(STATUS_DATE, 1L);

        SettlementResponse response = queryService.findSettlements(STATUS_DATE, null);

        assertThat(response.targetDate()).isEqualTo(STATUS_DATE);
        assertThat(response.batchStatus()).isEqualTo(BatchStatus.CONFIRMED);
        assertThat(response.reconciliationStatus()).isNull();
    }

    /**
     * 실패한 배치는 재실행할 수 있어야 하므로 같은 날짜에 이력이 여러 건 남는다. 오래된
     * {@code FAILED}를 집어 오면 <b>"정산이 실패했다"고 답하게 된다</b> — 실제로는 다시 돌아
     * 성공했는데도.
     */
    @DisplayName("같은 날짜에 이력이 여러 건이면 가장 최근 배치를 본다")
    @Test
    void readsMostRecentBatchOfTheDay()
    {
        SettlementBatch failed = SettlementBatch.start(RETRY_DATE);
        failed.transitionTo(BatchStatus.FAILED);
        batchRepository.saveAndFlush(failed);

        givenConfirmedBatchWith(RETRY_DATE, 1L);

        SettlementResponse response = queryService.findSettlements(RETRY_DATE, null);

        assertThat(response.batchStatus()).isEqualTo(BatchStatus.CONFIRMED);
    }

    @DisplayName("해당 일자에 배치가 없으면 SETTLEMENT_NOT_FOUND")
    @Test
    void rejectsMissingBatch()
    {
        assertThatThrownBy(() -> queryService.findSettlements(LocalDate.of(2026, 12, 25), null))
                .isInstanceOf(SettlementNotFoundException.class)
                .satisfies(thrown -> assertThat(((SettlementNotFoundException) thrown).getCode())
                        .isEqualTo("SETTLEMENT_NOT_FOUND"));
    }

    /**
     * 미지급금이 0이어서 항목이 만들어지지 않은 기사가 여기 해당한다. 빈 응답을 주면
     * "정산액이 0원"과 구분되지 않는다.
     */
    @DisplayName("배치는 있는데 그 기사 항목이 없으면 SETTLEMENT_NOT_FOUND")
    @Test
    void rejectsMissingDriver()
    {
        givenConfirmedBatchWith(EMPTY_DATE, 1L);

        assertThatThrownBy(() -> queryService.findSettlements(EMPTY_DATE, 999L))
                .isInstanceOf(SettlementNotFoundException.class);
    }

    /**
     * 배치 1번 + 항목 1번. 기사가 몇 명이든 늘어나지 않는다.
     */
    private long givenConfirmedBatchWith(LocalDate targetDate, Long... driverIds)
    {
        SettlementBatch batch = SettlementBatch.start(targetDate);
        batch.transitionTo(BatchStatus.CONFIRMED);
        Long batchId = batchRepository.saveAndFlush(batch).getBatchId();

        for (Long driverId : driverIds)
        {
            settlementRepository.saveAndFlush(Settlement.of(
                    batchId, driverId, new BigDecimal("20000"), new BigDecimal("4000")));
        }

        return batchId;
    }

}
