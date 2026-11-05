package com.rackguard.rackguardserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RackGuardApplication {
    public static void main(String[] args) {
        SpringApplication.run(RackGuardApplication.class, args);
    }
}
