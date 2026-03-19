package com.example.footballanalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FootballAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(FootballAnalysisApplication.class, args);
    }

}
