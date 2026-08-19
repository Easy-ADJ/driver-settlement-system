package com.example.driversettlementsystem.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * DataSource 2개 설정 — 정산 DB와 로그인 DB.
 * <p>
 * <b>{@code @Primary}가 붙은 쪽이 정산 DB다.</b> JPA·Spring Batch 메타 테이블·Flyway가
 * 전부 기본 DataSource를 잡으므로, 이걸 잘못 걸면 배치 메타 테이블과 정산 스키마가
 * 로그인 DB에 생긴다. 그쪽 사고는 부팅 성공 후에야 드러나 훨씬 찾기 어렵다.
 * <p>
 * ⚠️ <b>두 DB에 걸친 트랜잭션은 불가능하다.</b> 별도 인스턴스라 JOIN도 FK도 안 된다.
 * 기사 정보가 필요하면 정산 결과를 읽은 뒤 애플리케이션에서 합친다.
 * <p>
 * DataSource가 2개면 Spring Boot의 자동 설정이 걸리지 않으므로 정산 쪽도 직접 만든다.
 */
@Configuration
public class DataSourceConfig
{

    /** 정산 DB 접속 정보. */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties settlementDataSourceProperties()
    {
        return new DataSourceProperties();
    }

    /** 정산 DB. JPA·Batch·Flyway가 이걸 쓴다. */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource settlementDataSource()
    {
        return settlementDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /** 로그인 DB 접속 정보. 읽기 전용 계정({@code settlement_ro})을 쓴다. */
    @Bean
    @ConfigurationProperties("auth.datasource")
    public DataSourceProperties authDataSourceProperties()
    {
        return new DataSourceProperties();
    }

    /**
     * 로그인 DB.
     * <p>
     * 풀 크기는 프로퍼티에서 3으로 묶는다. 세 서버와 대시보드가 한 인스턴스에 붙어
     * 기본값(10)이면 서버 3대만으로 30 커넥션을 잡는다. 기사 정보 조회는 빈도가 낮다.
     */
    @Bean
    @ConfigurationProperties("auth.datasource.hikari")
    public HikariDataSource authDataSource()
    {
        return authDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * 로그인 DB 전용 JDBC 템플릿.
     * <p>
     * JPA를 붙이지 않는다 — 읽는 테이블이 {@code DRIVER_ACCOUNTS} 하나뿐이라
     * EntityManager를 두 벌 띄울 이유가 없다. 기사 여러 명을 {@code IN} 절로 한 번에
     * 읽으므로 이름 파라미터를 쓰는 쪽을 등록한다.
     *
     * @param authDataSource 로그인 DB DataSource
     * @return 로그인 DB에 묶인 템플릿
     */
    @Bean
    public NamedParameterJdbcTemplate authJdbcTemplate(
            @Qualifier("authDataSource") DataSource authDataSource)
    {
        return new NamedParameterJdbcTemplate(authDataSource);
    }

}
