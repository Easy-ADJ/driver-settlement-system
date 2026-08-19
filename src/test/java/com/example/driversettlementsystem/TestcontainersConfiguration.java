package com.example.driversettlementsystem;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration
{

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer()
	{
		return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
	}

	/**
	 * 컨테이너 접속 정보를 {@code spring.datasource.*} 로도 넣는다.
	 * <p>
	 * {@code @ServiceConnection}은 Spring Boot가 <b>자동 설정으로 만든</b> DataSource에만
	 * 걸린다. DataSource를 직접 만드는 설정이 있으면 그쪽은 프로퍼티만 보므로, 값을 함께
	 * 넣어 두 경로가 같은 컨테이너를 가리키게 한다.
	 */
	@Bean
	DynamicPropertyRegistrar datasourcePropertiesRegistrar(PostgreSQLContainer container)
	{
		return registry ->
		{
			registry.add("spring.datasource.url", container::getJdbcUrl);
			registry.add("spring.datasource.username", container::getUsername);
			registry.add("spring.datasource.password", container::getPassword);
		};
	}

}
