package com.example.driversettlementsystem;

import com.example.driversettlementsystem.auth.AuthDataSourceTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import({TestcontainersConfiguration.class, AuthDataSourceTestConfiguration.class})
@SpringBootTest
class DriverSettlementSystemApplicationTests
{

	@Test
	void contextLoads()
	{
	}

}
