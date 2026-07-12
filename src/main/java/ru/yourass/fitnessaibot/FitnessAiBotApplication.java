package ru.yourass.fitnessaibot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan({"ru.yourass.fitnessaibot.config",
        "ru.yourass.fitnessaibot.health"})
@EnableScheduling
public class FitnessAiBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitnessAiBotApplication.class, args);
    }
}
