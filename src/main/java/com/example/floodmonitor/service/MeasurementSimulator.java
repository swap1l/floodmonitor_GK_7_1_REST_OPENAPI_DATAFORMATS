package com.example.floodmonitor.service;

import com.example.floodmonitor.model.Measurement;
import com.example.floodmonitor.model.Station;
import com.example.floodmonitor.model.StationStatus;

import java.time.Instant;
import java.util.Random;

/**
 * Erzeugt aus dem vorherigen Messwert einer Station den nächsten Messwert.
 * Die Regeln sind in der README unter "Simulationsregeln" beschrieben.
 * Die Warnstufe wird hier nicht gesetzt, das übernimmt {@link WarningLevelService}.
 */
public class MeasurementSimulator {

    // Niederschlag
    static final double RAIN_START_PROBABILITY = 0.04;
    static final double HEAVY_RAIN_PROBABILITY = 0.2;
    static final double RAIN_STOP_PROBABILITY = 0.05;
    static final double MAX_RAINFALL = 60.0;
    static final double RAIN_TREND_MIN = 0.85;
    static final double RAIN_TREND_RANGE = 0.33;
    // Wasserstand
    static final double RAIN_EFFECT_CM_PER_MM = 0.8;
    static final double RECESSION_RATE = 0.06;
    static final double MAX_LEVEL_CHANGE_CM = 25.0;
    // Akku
    static final double BATTERY_DRAIN_PROBABILITY = 0.3;
    static final int MAINTENANCE_BATTERY_THRESHOLD = 15;
    static final double BATTERY_REPLACEMENT_PROBABILITY = 0.15;

    private final Random random;

    public MeasurementSimulator(Random random) {
        this.random = random;
    }

    public Measurement next(Station station, Measurement previous, Instant timestamp) {
        if (previous == null) {
            return initial(station, timestamp);
        }

        double rainfall = nextRainfall(previous.getRainfall());
        double waterLevel = nextWaterLevel(station, previous.getWaterLevel(), rainfall);
        double flowRate = flowRate(station, waterLevel);
        double temperature = previous.getTemperature()
                + 0.05 * (station.getBaseTemperature() - previous.getTemperature())
                + random.nextGaussian() * 0.2
                - (rainfall > 0 ? 0.05 : 0);

        int battery = previous.getBatteryLevel();
        StationStatus status = previous.getStatus();
        if (status == StationStatus.MAINTENANCE && random.nextDouble() < BATTERY_REPLACEMENT_PROBABILITY) {
            battery = 100; // Techniker hat den Akku getauscht
        } else if (random.nextDouble() < BATTERY_DRAIN_PROBABILITY) {
            battery = Math.max(0, battery - 1);
        }
        if (battery == 0) {
            status = StationStatus.OFFLINE;
        } else if (battery < MAINTENANCE_BATTERY_THRESHOLD) {
            status = StationStatus.MAINTENANCE;
        } else {
            status = StationStatus.ONLINE;
        }

        return new Measurement(station.getId(), timestamp, round(waterLevel), round(flowRate),
                round(rainfall), round(temperature), battery, status);
    }

    private Measurement initial(Station station, Instant timestamp) {
        double waterLevel = station.getNormalWaterLevel() * (0.95 + random.nextDouble() * 0.1);
        double temperature = station.getBaseTemperature() + random.nextGaussian();
        int battery = 60 + random.nextInt(41);
        return new Measurement(station.getId(), timestamp, round(waterLevel), round(flowRate(station, waterLevel)),
                0.0, round(temperature), battery, StationStatus.ONLINE);
    }

    /** Niederschlag als Markov-Kette: Regenphasen beginnen, halten an und enden zufällig. */
    double nextRainfall(double previousRainfall) {
        if (previousRainfall <= 0) {
            if (random.nextDouble() >= RAIN_START_PROBABILITY) {
                return 0.0;
            }
            return random.nextDouble() < HEAVY_RAIN_PROBABILITY
                    ? 10 + random.nextDouble() * 15
                    : 0.5 + random.nextDouble() * 6;
        }
        if (random.nextDouble() < RAIN_STOP_PROBABILITY) {
            return 0.0;
        }
        // Intensität ändert sich pro Messung um den Faktor 0,85…1,18 (begrenzt auf MAX_RAINFALL)
        double rainfall = previousRainfall * (RAIN_TREND_MIN + random.nextDouble() * RAIN_TREND_RANGE) + random.nextGaussian() * 0.5;
        return clamp(rainfall, 0.1, MAX_RAINFALL);
    }

    /**
     * Niederschlag hebt den Wasserstand, ohne Regen sinkt er langsam Richtung Normalwasserstand.
     * Oberhalb des Warnwerts wirkt Regen umso schwächer, je näher der Pegel am Höchststand ({@link #maxWaterLevel}) ist
     * (das Wasser verteilt sich auf Überflutungsflächen). Die Änderung pro Messung ist begrenzt,
     * der Wasserstand nie negativ.
     */
    double nextWaterLevel(Station station, double previousLevel, double rainfall) {
        double normal = station.getNormalWaterLevel();
        double max = maxWaterLevel(station);
        double headroom = clamp((max - previousLevel) / (max - station.getWarningWaterLevel()), 0, 1);
        double delta = rainfall * RAIN_EFFECT_CM_PER_MM * headroom
                - RECESSION_RATE * (previousLevel - normal)
                + random.nextGaussian();
        delta = clamp(delta, -MAX_LEVEL_CHANGE_CM, MAX_LEVEL_CHANGE_CM);
        return Math.max(0, previousLevel + delta);
    }

    /** Höchststand, bei dem Regen den Pegel nicht weiter hebt: kritischer Wert + halber Abstand Warn→Kritisch. */
    static double maxWaterLevel(Station station) {
        return station.getCriticalWaterLevel()
                + 0.5 * (station.getCriticalWaterLevel() - station.getWarningWaterLevel());
    }

    /** Pegel-Abfluss-Beziehung: Durchfluss wächst überproportional mit dem Wasserstand. */
    double flowRate(Station station, double waterLevel) {
        double ratio = waterLevel / station.getNormalWaterLevel();
        double flow = station.getMeanFlowRate() * Math.pow(ratio, 1.5) * (1 + random.nextGaussian() * 0.01);
        return Math.max(0, flow);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
