package com.drivewealth.Reconciliation_Engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReconciliationEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReconciliationEngineApplication.class, args);
	}

}
