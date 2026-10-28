package com.example.floodmonitor.service;

import com.example.floodmonitor.model.Alert;
import com.example.floodmonitor.model.Station;
import com.example.floodmonitor.model.WarningLevel;
import com.example.floodmonitor.repository.MeasurementRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Aktuelle Warnungen: aktive Stationen, deren aktuelle Warnstufe WARNING oder CRITICAL ist.
 */
@Service
public class AlertService {

    private final StationService stationService;
    private final MeasurementRepository measurementRepository;

    public AlertService(StationService stationService, MeasurementRepository measurementRepository) {
        this.stationService = stationService;
        this.measurementRepository = measurementRepository;
    }

    /** Kritische Warnungen zuerst; Filter sind optional (null = nicht filtern). */
    public List<Alert> findCurrentAlerts(WarningLevel warningLevel, String river) {
        return stationService.findAll(river, true, warningLevel).stream()
                .filter(s -> s.getCurrentWarningLevel() == WarningLevel.WARNING
                        || s.getCurrentWarningLevel() == WarningLevel.CRITICAL)
                .map(this::toAlert)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Alert::getWarningLevel).reversed()
                        .thenComparing(Alert::getStationId))
                .toList();
    }

    private Alert toAlert(Station station) {
        return measurementRepository.findLatest(station.getId())
                .map(m -> new Alert(station, m))
                .orElse(null);
    }
}
