package com.example.floodmonitor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Startet die Simulation: erzeugt beim Start eine rückwirkende Historie und danach
 * alle {@code simulation.interval} Millisekunden eine neue Messung pro aktiver Station.
 * Mit {@code simulation.enabled=false} (z. B. in Tests) wird nichts simuliert.
 */
@Component
@ConditionalOnProperty(name = "simulation.enabled", havingValue = "true", matchIfMissing = true)
public class SimulationScheduler {

    private final MeasurementService measurementService;
    private final Duration interval;
    private final int historySize;

    public SimulationScheduler(MeasurementService measurementService,
                               @Value("${simulation.interval:10000}") long intervalMillis,
                               @Value("${simulation.history-size:60}") int historySize) {
        this.measurementService = measurementService;
        this.interval = Duration.ofMillis(intervalMillis);
        this.historySize = historySize;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createHistory() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        for (int i = historySize; i > 0; i--) {
            measurementService.simulateTick(now.minus(interval.multipliedBy(i)));
        }
    }

    @Scheduled(fixedRateString = "${simulation.interval:10000}", initialDelayString = "${simulation.interval:10000}")
    public void tick() {
        measurementService.simulateTick(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }
}
