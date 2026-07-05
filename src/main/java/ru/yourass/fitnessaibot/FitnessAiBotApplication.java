package ru.yourass.fitnessaibot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("ru.yourass.fitnessaibot.config")
public class FitnessAiBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitnessAiBotApplication.class, args);
    }
}
