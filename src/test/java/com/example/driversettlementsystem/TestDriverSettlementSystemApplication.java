package com.example.driversettlementsystem;

import org.springframework.boot.SpringApplication;

public class TestDriverSettlementSystemApplication
{

	public static void main(String[] args)
	{
		SpringApplication.from(DriverSettlementSystemApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
