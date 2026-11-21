package dev.codebasecopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CodebaseCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodebaseCopilotApplication.class, args);
    }
}
