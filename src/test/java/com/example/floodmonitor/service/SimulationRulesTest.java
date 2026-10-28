package com.example.floodmonitor.service;

import com.example.floodmonitor.model.Location;
import com.example.floodmonitor.model.Measurement;
import com.example.floodmonitor.model.Station;
import com.example.floodmonitor.model.StationStatus;
import com.example.floodmonitor.model.WarningLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationRulesTest {

    private final Station station = new Station("ST-X", "Test", "Donau", new Location(48, 16),
            300, 450, 600, true, 1000, 12);
    private final WarningLevelService warningLevelService = new WarningLevelService(10_000, 3);

    private Measurement measurement(double waterLevel, double rainfall) {
        return new Measurement("ST-X", Instant.now(), waterLevel, 1000, rainfall, 12, 80, StationStatus.ONLINE);
    }

    @Test
    void warningLevelThresholds() {
        assertEquals(WarningLevel.NORMAL, warningLevelService.calculate(station, measurement(449.9, 0), null));
        assertEquals(WarningLevel.WARNING, warningLevelService.calculate(station, measurement(450, 0), null));
        assertEquals(WarningLevel.CRITICAL, warningLevelService.calculate(station, measurement(600, 0), null));
        assertEquals(WarningLevel.UNKNOWN, warningLevelService.calculate(station, null, null));
        assertEquals(WarningLevel.UNKNOWN, warningLevelService.calculate(station, measurement(-1, 0), null));
    }

    @Test
    void earlyWarningOnFastRiseWithHeavyRain() {
        Measurement previous = measurement(400, 20);
        assertEquals(WarningLevel.WARNING, warningLevelService.calculate(station, measurement(415, 20), previous));
        assertEquals(WarningLevel.NORMAL, warningLevelService.calculate(station, measurement(415, 0), previous));
    }

    @Test
    void staleMeasurementIsUnknown() {
        Measurement m = measurement(300, 0);
        m.setWarningLevel(WarningLevel.NORMAL);
        assertEquals(WarningLevel.NORMAL, warningLevelService.current(m, m.getTimestamp().plusSeconds(10)));
        assertEquals(WarningLevel.UNKNOWN, warningLevelService.current(m, m.getTimestamp().plusSeconds(31)));
        assertEquals(WarningLevel.UNKNOWN, warningLevelService.current(null, Instant.now()));
    }

    @Test
    void simulatedValuesStayPlausible() {
        MeasurementSimulator simulator = new MeasurementSimulator(new Random(42));
        Measurement previous = null;
        for (int i = 0; i < 5000; i++) {
            Measurement next = simulator.next(station, previous, Instant.now());
            assertTrue(next.getWaterLevel() >= 0);
            // Regen hebt den Pegel nur bis zum Höchststand, darüber nur noch Messrauschen
            assertTrue(next.getWaterLevel() <= MeasurementSimulator.maxWaterLevel(station) + 10);
            assertTrue(next.getBatteryLevel() >= 0 && next.getBatteryLevel() <= 100);
            assertTrue(next.getRainfall() >= 0);
            if (previous != null) {
                assertTrue(Math.abs(next.getWaterLevel() - previous.getWaterLevel())
                        <= MeasurementSimulator.MAX_LEVEL_CHANGE_CM + 0.1);
            }
            if (next.getStatus() == StationStatus.OFFLINE) {
                break;
            }
            previous = next;
        }
    }

    @Test
    void heavyRainRaisesWaterLevelAndFlow() {
        MeasurementSimulator simulator = new MeasurementSimulator(new Random(1));
        double dry = 0;
        double wet = 0;
        for (int i = 0; i < 200; i++) {
            dry += simulator.nextWaterLevel(station, 300, 0);
            wet += simulator.nextWaterLevel(station, 300, 30);
        }
        assertTrue(wet > dry);
        assertTrue(simulator.flowRate(station, 500) > simulator.flowRate(station, 300));
    }

    @Test
    void lowBatterySwitchesToMaintenance() {
        MeasurementSimulator simulator = new MeasurementSimulator(new Random(7));
        Measurement previous = measurement(300, 0);
        previous.setBatteryLevel(10);
        Measurement next = simulator.next(station, previous, Instant.now());
        assertTrue(next.getStatus() == StationStatus.MAINTENANCE || next.getBatteryLevel() == 100);
    }
}
