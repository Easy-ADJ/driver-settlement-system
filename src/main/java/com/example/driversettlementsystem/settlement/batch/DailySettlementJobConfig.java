package com.example.driversettlementsystem.settlement.batch;

import com.example.driversettlementsystem.settlement.client.DriverUnpaid;
import com.example.driversettlementsystem.settlement.domain.Settlement;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.DefaultJobParametersValidator;
import org.springframework.batch.core.job.parameters.JobParametersValidator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 일 단위 정산 Job의 구조 정의.
 * <p>
 * <b>이 클래스는 조립만 한다.</b> 무엇을 읽고, 어떻게 계산하고, 어디에 쓸지는 각각
 * Reader·Processor·Writer가 안다. 비즈니스 로직이 여기 들어오기 시작하면 배치 구조와
 * 계산 규칙이 한 파일에 엉켜, 계산 하나를 테스트하려고 배치 컨텍스트 전체를 띄우게 된다.
 *
 * <pre>
 *   Job: dailySettlementJob
 *    └── Step: settlementStep  (chunk = 100)
 *          Reader    UnpaidDriverReader         원장에서 미지급 기사 읽기
 *          Processor DriverSettlementProcessor  수수료 차감
 *          Writer    SettlementWriter           DB 저장
 * </pre>
 * <p>
 * <b>Step이 구체 클래스가 아니라 {@code ItemReader}·{@code ItemProcessor}·
 * {@code ItemWriter} 인터페이스에 의존한다.</b> 아래 no-op 빈 3개는 임시이고, 실제 구현이
 * 들어올 때 각각 지운다. 그때 이 Step의 시그니처는 바뀌지 않는다.
 */
@Configuration
public class DailySettlementJobConfig
{

    /**
     * 청크 크기. 이 건수만큼 처리할 때마다 커밋한다.
     * <p>
     * Job 전체를 트랜잭션 하나로 묶으면 기사 1,000명 중 999번째에서 실패했을 때 998명분이
     * 전부 날아간다. 크게 잡으면 커밋 횟수가 줄어 빠르지만 실패 시 되돌아가는 양이 커진다.
     * 100은 시작점이며 실제 기사 수를 보고 조정한다.
     */
    static final int CHUNK_SIZE = 100;

    /** 정산 대상 일자를 담는 Job 파라미터 이름. */
    static final String TARGET_DATE_PARAMETER = "targetDate";

    /**
     * 정산 Job.
     * <p>
     * {@code targetDate}를 Job 파라미터로 받는다. Spring Batch는 <b>파라미터가 같은 Job
     * 인스턴스를 두 번 실행하지 않으므로</b> 이것만으로도 1차 중복 방어가 된다. 다만 이건
     * 부수 효과일 뿐 정식 방어선이 아니다 — {@code DuplicateBatchGuard}와 {@code BATCHES}의
     * 부분 UNIQUE 인덱스가 정식 방어선이다.
     *
     * @param jobRepository  Spring Batch 메타 저장소
     * @param settlementStep 읽기 → 가공 → 쓰기 Step
     * @param listener       배치 레코드 생성과 상태 전이를 맡는다
     * @return 등록된 정산 Job
     */
    @Bean
    public Job dailySettlementJob(JobRepository jobRepository,
                                  Step settlementStep,
                                  SettlementJobListener listener)
    {
        return new JobBuilder("dailySettlementJob", jobRepository)
                .validator(targetDateValidator())
                .listener(listener)
                .start(settlementStep)
                .build();
    }

    /**
     * 읽기 → 가공 → 쓰기 한 Step.
     * <p>
     * 청크 단위 트랜잭션이다. {@code CHUNK_SIZE}건마다 커밋하고, 실패하면 <b>그 청크만</b>
     * 롤백된다.
     * <p>
     * 처리 단위는 <b>기사 1명 = 1건</b>이다. 청크 100은 "기사 100명마다 커밋"이 된다.
     *
     * @param jobRepository      Spring Batch 메타 저장소
     * @param transactionManager 청크 커밋에 쓸 트랜잭션 매니저
     * @param reader             미지급 기사를 읽는다
     * @param processor          수수료를 차감해 정산 1건을 만든다
     * @param writer             정산 항목을 저장한다
     * @return 청크 단위로 커밋하는 Step
     */
    @Bean
    public Step settlementStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               ItemReader<DriverUnpaid> reader,
                               ItemProcessor<DriverUnpaid, Settlement> processor,
                               ItemWriter<Settlement> writer)
    {
        return new StepBuilder("settlementStep", jobRepository)
                .<DriverUnpaid, Settlement>chunk(CHUNK_SIZE)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .transactionManager(transactionManager)
                .build();
    }

    /**
     * {@code targetDate} 없이 실행하면 Job이 시작되지 않게 한다.
     * <p>
     * 없으면 어느 날짜를 정산하는지 모르는 채로 Job이 돌고, 그게 <b>성공으로 끝난다.</b>
     */
    private JobParametersValidator targetDateValidator()
    {
        return new DefaultJobParametersValidator(new String[] {TARGET_DATE_PARAMETER}, new String[] {});
    }

    /**
     * 🚧 임시 — 정산 항목 저장 Writer가 들어오면 <b>이 빈을 지운다.</b>
     *
     * @return 아무것도 저장하지 않는 Writer
     */
    @Bean
    public ItemWriter<Settlement> settlementWriter()
    {
        return chunk ->
        {
        };
    }

}
