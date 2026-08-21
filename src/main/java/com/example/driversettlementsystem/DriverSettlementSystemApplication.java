package com.example.driversettlementsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ⚠️ {@code @EnableScheduling}이 없으면 {@code @Scheduled}가 <b>조용히 무시된다.</b>
 * 예외도 경고도 없이 그냥 아무 일도 일어나지 않는다.
 */
@EnableScheduling
@SpringBootApplication
public class DriverSettlementSystemApplication
{

	public static void main(String[] args)
	{
		SpringApplication.run(DriverSettlementSystemApplication.class, args);
	}

}
