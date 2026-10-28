package com.example.floodmonitor.service;

import com.example.floodmonitor.model.Measurement;
import com.example.floodmonitor.model.Station;
import com.example.floodmonitor.model.WarningLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Berechnet die Hochwasserwarnstufe serverseitig.
 *
 * <ol>
 *   <li>Keine Messung oder ungültiger Wasserstand (negativ / NaN) → UNKNOWN</li>
 *   <li>Wasserstand ≥ kritischer Wert → CRITICAL</li>
 *   <li>Wasserstand ≥ Warnwert → WARNING</li>
 *   <li>Frühwarnung: Wasserstand ≥ 90 % des Warnwerts UND Anstieg ≥ 10 cm seit der vorherigen
 *       Messung UND Niederschlag ≥ 10 mm/h → WARNING</li>
 *   <li>sonst → NORMAL</li>
 * </ol>
 * Für den aktuellen Zustand einer Station gilt zusätzlich: Ist die letzte Messung älter als
 * {@code warning.stale-after-intervals} Simulationsintervalle, ist die Warnstufe UNKNOWN.
 */
@Service
public class WarningLevelService {

    static final double EARLY_WARNING_RATIO = 0.9;
    static final double EARLY_WARNING_RISE_CM = 10.0;
    static final double EARLY_WARNING_RAINFALL = 10.0;

    private final Duration maxAge;

    public WarningLevelService(@Value("${simulation.interval:10000}") long intervalMillis,
                               @Value("${warning.stale-after-intervals:3}") int staleAfterIntervals) {
        this.maxAge = Duration.ofMillis(intervalMillis * staleAfterIntervals);
    }

    /**
     * Warnstufe einer einzelnen Messung.
     *
     * @param previous vorherige Messung derselben Station, darf null sein
     */
    public WarningLevel calculate(Station station, Measurement measurement, Measurement previous) {
        if (measurement == null || !isValid(measurement.getWaterLevel())) {
            return WarningLevel.UNKNOWN;
        }
        double level = measurement.getWaterLevel();
        if (level >= station.getCriticalWaterLevel()) {
            return WarningLevel.CRITICAL;
        }
        if (level >= station.getWarningWaterLevel()) {
            return WarningLevel.WARNING;
        }
        if (previous != null && isValid(previous.getWaterLevel())
                && level >= station.getWarningWaterLevel() * EARLY_WARNING_RATIO
                && level - previous.getWaterLevel() >= EARLY_WARNING_RISE_CM
                && measurement.getRainfall() >= EARLY_WARNING_RAINFALL) {
            return WarningLevel.WARNING;
        }
        return WarningLevel.NORMAL;
    }

    /** Aktuelle Warnstufe einer Station anhand ihrer letzten Messung. */
    public WarningLevel current(Measurement latest, Instant now) {
        if (latest == null || latest.getWarningLevel() == null
                || latest.getTimestamp().isBefore(now.minus(maxAge))) {
            return WarningLevel.UNKNOWN;
        }
        return latest.getWarningLevel();
    }

    private static boolean isValid(double waterLevel) {
        return !Double.isNaN(waterLevel) && waterLevel >= 0;
    }
}
