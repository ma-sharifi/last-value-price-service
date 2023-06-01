package com.example.bootstrap;

import com.example.model.Instrument;
import com.example.model.PriceData;
import com.example.service.PriceTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

@Configuration
@Profile("!prod")
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final Environment environment;
    private final PriceTrackingService priceTrackingService;

    private final List<Instrument> instrumentFixedList = List.of(
            new Instrument("1", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 100),
            new Instrument("2", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 200),
            new Instrument("3", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 300),
            new Instrument("4", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 400),
            new Instrument("5", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 500),
            new Instrument("6", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 600),
            new Instrument("7", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 700),
            new Instrument("8", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 800),
            new Instrument("9", LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(1, 1, 1)), 900)
    );


    public DataLoader(Environment environment, PriceTrackingService priceTrackingService) {
        this.environment = environment;
        this.priceTrackingService = priceTrackingService;
    }

    @Override
    public void run(String... args) {
        log.info("#data are loading.....");
        loadData();
        log.info("#Currently active profile - " + Arrays.toString(environment.getActiveProfiles()));

    }

    public void loadData() {
        priceTrackingService.putAll(instrumentFixedList);
        log.info("#Template data are loaded. Size of loaded data is: " + priceTrackingService.size());
    }
}
