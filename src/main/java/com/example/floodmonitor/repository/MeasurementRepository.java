package com.example.floodmonitor.repository;

import com.example.floodmonitor.model.Measurement;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Memory-Speicher für Messungen. Pro Station werden die Messungen chronologisch
 * (älteste zuerst) gehalten; ältere Einträge werden ab {@link #MAX_PER_STATION} verworfen.
 */
@Repository
public class MeasurementRepository {

    static final int MAX_PER_STATION = 10_000;

    private final Map<String, List<Measurement>> measurements = new ConcurrentHashMap<>();

    public void save(Measurement measurement) {
        List<Measurement> list = measurements.computeIfAbsent(measurement.getStationId(), id -> new ArrayList<>());
        synchronized (list) {
            list.add(measurement);
            if (list.size() > MAX_PER_STATION) {
                list.remove(0);
            }
        }
    }

    /** Alle Messungen einer Station, chronologisch (älteste zuerst). */
    public List<Measurement> findByStationId(String stationId) {
        List<Measurement> list = measurements.get(stationId);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    public Optional<Measurement> findLatest(String stationId) {
        List<Measurement> list = measurements.get(stationId);
        if (list == null) {
            return Optional.empty();
        }
        synchronized (list) {
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1));
        }
    }

    public void deleteByStationId(String stationId) {
        measurements.remove(stationId);
    }

    public void deleteAll() {
        measurements.clear();
    }
}
