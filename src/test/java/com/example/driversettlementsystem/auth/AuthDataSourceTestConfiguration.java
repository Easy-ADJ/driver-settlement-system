package com.example.driversettlementsystem.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 테스트에서 로그인 DB 접속 정보를 채운다.
 * <p>
 * 로그인 DB는 남이 소유한 실제 인스턴스라 테스트가 붙을 곳이 없다. 컨텍스트가 뜨려면
 * 접속 가능한 주소가 있어야 하므로 <b>정산 컨테이너를 그대로 가리킨다.</b> 여기서 확인하는
 * 것은 "DataSource 2개가 각각 뜬다"까지이며, {@code DRIVER_ACCOUNTS} 조회는
 * {@code DriverAccountReaderTest}가 테이블을 직접 만들어 검증한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AuthDataSourceTestConfiguration
{

    @Bean
    DynamicPropertyRegistrar authDatasourcePropertiesRegistrar(PostgreSQLContainer container)
    {
        return registry ->
        {
            registry.add("auth.datasource.url", container::getJdbcUrl);
            registry.add("auth.datasource.username", container::getUsername);
            registry.add("auth.datasource.password", container::getPassword);
        };
    }

}
