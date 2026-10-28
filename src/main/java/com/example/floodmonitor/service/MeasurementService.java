package com.example.floodmonitor.service;

import com.example.floodmonitor.exception.InvalidRequestException;
import com.example.floodmonitor.exception.NoMeasurementException;
import com.example.floodmonitor.model.Measurement;
import com.example.floodmonitor.model.Station;
import com.example.floodmonitor.model.StationStatus;
import com.example.floodmonitor.model.Statistics;
import com.example.floodmonitor.model.WarningLevel;
import com.example.floodmonitor.repository.MeasurementRepository;
import com.example.floodmonitor.repository.StationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

@Service
public class MeasurementService {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 1000;

    private final StationRepository stationRepository;
    private final MeasurementRepository measurementRepository;
    private final StationService stationService;
    private final WarningLevelService warningLevelService;
    private final MeasurementSimulator simulator = new MeasurementSimulator(new Random());

    public MeasurementService(StationRepository stationRepository, MeasurementRepository measurementRepository,
                              StationService stationService, WarningLevelService warningLevelService) {
        this.stationRepository = stationRepository;
        this.measurementRepository = measurementRepository;
        this.stationService = stationService;
        this.warningLevelService = warningLevelService;
    }

    /** Erzeugt für jede aktive, nicht ausgefallene Station eine neue Messung zum Zeitpunkt {@code timestamp}. */
    public void simulateTick(Instant timestamp) {
        for (Station station : stationRepository.findAll()) {
            if (!station.isActive() || station.getStatus() == StationStatus.OFFLINE) {
                continue;
            }
            Measurement previous = measurementRepository.findLatest(station.getId()).orElse(null);
            Measurement next = simulator.next(station, previous, timestamp);
            addMeasurement(station, next, previous);
        }
    }

    /** Speichert eine Messung; Warnstufe und Stationsstatus werden serverseitig gesetzt. */
    public Measurement addMeasurement(Station station, Measurement measurement, Measurement previous) {
        measurement.setWarningLevel(warningLevelService.calculate(station, measurement, previous));
        measurementRepository.save(measurement);
        station.setStatus(measurement.getStatus());
        return measurement;
    }

    public Measurement getLatest(String stationId) {
        stationService.findById(stationId);
        return measurementRepository.findLatest(stationId)
                .orElseThrow(() -> new NoMeasurementException(stationId));
    }

    /**
     * Historische Messungen, neueste zuerst.
     *
     * @param limit maximale Anzahl (1..{@value #MAX_LIMIT}), null = {@value #DEFAULT_LIMIT}
     */
    public List<Measurement> getHistory(String stationId, Instant from, Instant to,
                                        Integer limit, WarningLevel warningLevel) {
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
            throw new InvalidRequestException("limit muss zwischen 1 und " + MAX_LIMIT + " liegen, war " + limit);
        }
        return inRange(stationId, from, to)
                .filter(m -> warningLevel == null || m.getWarningLevel() == warningLevel)
                .sorted(Comparator.comparing(Measurement::getTimestamp).reversed())
                .limit(effectiveLimit)
                .toList();
    }

    public Statistics getStatistics(String stationId, Instant from, Instant to) {
        List<Measurement> measurements = inRange(stationId, from, to).toList();

        Statistics stats = new Statistics();
        stats.setStationId(stationId);
        stats.setFrom(from);
        stats.setTo(to);
        stats.setMeasurementCount(measurements.size());
        if (!measurements.isEmpty()) {
            stats.setMinWaterLevel(measurements.stream().mapToDouble(Measurement::getWaterLevel).min().getAsDouble());
            stats.setMaxWaterLevel(measurements.stream().mapToDouble(Measurement::getWaterLevel).max().getAsDouble());
            stats.setAvgWaterLevel(round(measurements.stream().mapToDouble(Measurement::getWaterLevel).average().getAsDouble()));
            stats.setAvgFlowRate(round(measurements.stream().mapToDouble(Measurement::getFlowRate).average().getAsDouble()));
        }
        stats.setRainfallSum(round(measurements.stream().mapToDouble(Measurement::getRainfall).sum()));
        stats.setWarningCount((int) measurements.stream()
                .filter(m -> m.getWarningLevel() == WarningLevel.WARNING || m.getWarningLevel() == WarningLevel.CRITICAL)
                .count());
        stats.setCriticalCount((int) measurements.stream()
                .filter(m -> m.getWarningLevel() == WarningLevel.CRITICAL)
                .count());
        return stats;
    }

    private Stream<Measurement> inRange(String stationId, Instant from, Instant to) {
        stationService.findById(stationId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("from (" + from + ") darf nicht nach to (" + to + ") liegen");
        }
        return measurementRepository.findByStationId(stationId).stream()
                .filter(m -> from == null || !m.getTimestamp().isBefore(from))
                .filter(m -> to == null || !m.getTimestamp().isAfter(to));
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
