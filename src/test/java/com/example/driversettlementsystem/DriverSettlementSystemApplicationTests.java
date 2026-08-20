package com.example.driversettlementsystem;

import com.example.driversettlementsystem.auth.AuthDataSourceTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 서버 주소는 가짜 값을 넣는다. <b>이 테스트에서 실제로 호출하지는 않지만</b>
 * {@code RestClientConfig}가 기본값 없이 주소를 요구하므로, 없으면 컨텍스트가 뜨지 않는다.
 * 그 "없으면 못 뜬다"는 동작 자체는 {@code RestClientConfigTest}가 따로 지킨다.
 */
@Import({TestcontainersConfiguration.class, AuthDataSourceTestConfiguration.class})
@SpringBootTest(properties = {
		"settlement.client.payment.base-url=http://payment.test",
		"settlement.client.ledger.base-url=http://ledger.test"})
class DriverSettlementSystemApplicationTests
{

	@Test
	void contextLoads()
	{
	}

}
