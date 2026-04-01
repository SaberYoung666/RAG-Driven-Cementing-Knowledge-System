package org.swpu.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.swpu.backend.modules.realtimeconsole.support.ConsoleOutputCapture;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		ConsoleOutputCapture.install("backend");
		SpringApplication.run(BackendApplication.class, args);
	}

}
