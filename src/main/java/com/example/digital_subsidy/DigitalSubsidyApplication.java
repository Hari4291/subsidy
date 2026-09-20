package com.example.digital_subsidy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DigitalSubsidyApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitalSubsidyApplication.class, args);
    }
}
