package com.grid07.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Grid07ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(Grid07ApiApplication.class, args);
    }
}
