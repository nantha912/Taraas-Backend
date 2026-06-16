package com.LocalService.lsp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LspApplication {

	public static void main(String[] args) {
		SpringApplication.run(LspApplication.class, args);
	}

}
