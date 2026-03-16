package com.digitalisyours;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DigitalIsYoursBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(DigitalIsYoursBackendApplication.class, args);
    }
}