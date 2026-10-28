package com.example.floodmonitor.service;

import com.example.floodmonitor.exception.InvalidRequestException;
import com.example.floodmonitor.exception.StationConflictException;
import com.example.floodmonitor.exception.StationNotFoundException;
import com.example.floodmonitor.model.Location;
import com.example.floodmonitor.model.Station;
import com.example.floodmonitor.model.StationRequest;
import com.example.floodmonitor.model.WarningLevel;
import com.example.floodmonitor.repository.MeasurementRepository;
import com.example.floodmonitor.repository.StationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class StationService {

    private static final Pattern STATION_ID = Pattern.compile("ST-\\d{3,6}");
    static final double DEFAULT_MEAN_FLOW_RATE = 500;
    static final double DEFAULT_BASE_TEMPERATURE = 12;

    private final StationRepository stationRepository;
    private final MeasurementRepository measurementRepository;
    private final WarningLevelService warningLevelService;

    public StationService(StationRepository stationRepository, MeasurementRepository measurementRepository,
                          WarningLevelService warningLevelService) {
        this.stationRepository = stationRepository;
        this.measurementRepository = measurementRepository;
        this.warningLevelService = warningLevelService;
    }

    /** Alle Stationen; jeder Filter ist optional (null = nicht filtern). */
    public List<Station> findAll(String river, Boolean isActive, WarningLevel warningLevel) {
        return stationRepository.findAll().stream()
                .map(this::withCurrentWarningLevel)
                .filter(s -> river == null || s.getRiver().equalsIgnoreCase(river.trim()))
                .filter(s -> isActive == null || s.isActive() == isActive)
                .filter(s -> warningLevel == null || s.getCurrentWarningLevel() == warningLevel)
                .toList();
    }

    public Station findById(String stationId) {
        return stationRepository.findById(stationId)
                .map(this::withCurrentWarningLevel)
                .orElseThrow(() -> new StationNotFoundException(stationId));
    }

    /** Legt eine neue Station an; ab dem nächsten Simulationsintervall werden Messungen erzeugt. */
    public Station create(StationRequest request) {
        validate(request);
        Station station = new Station(request.getId().trim(), request.getName().trim(), request.getRiver().trim(),
                request.getLocation(), request.getNormalWaterLevel(), request.getWarningWaterLevel(),
                request.getCriticalWaterLevel(), request.getActive() == null || request.getActive(),
                request.getMeanFlowRate() == null ? DEFAULT_MEAN_FLOW_RATE : request.getMeanFlowRate(),
                request.getBaseTemperature() == null ? DEFAULT_BASE_TEMPERATURE : request.getBaseTemperature());
        if (!stationRepository.saveIfAbsent(station)) {
            throw new StationConflictException(station.getId());
        }
        return withCurrentWarningLevel(station);
    }

    /** Löscht eine Station samt ihrer Messungen. */
    public void delete(String stationId) {
        if (!stationRepository.deleteById(stationId)) {
            throw new StationNotFoundException(stationId);
        }
        measurementRepository.deleteByStationId(stationId);
    }

    private static void validate(StationRequest r) {
        List<String> errors = new ArrayList<>();
        if (r.getId() == null || !STATION_ID.matcher(r.getId().trim()).matches()) {
            errors.add("id muss dem Format ST-123 entsprechen");
        }
        if (r.getName() == null || r.getName().isBlank()) {
            errors.add("name fehlt");
        }
        if (r.getRiver() == null || r.getRiver().isBlank()) {
            errors.add("river fehlt");
        }
        Location loc = r.getLocation();
        if (loc == null || Math.abs(loc.getLatitude()) > 90 || Math.abs(loc.getLongitude()) > 180) {
            errors.add("location mit latitude (-90..90) und longitude (-180..180) erforderlich");
        }
        Double normal = r.getNormalWaterLevel();
        Double warning = r.getWarningWaterLevel();
        Double critical = r.getCriticalWaterLevel();
        if (normal == null || warning == null || critical == null) {
            errors.add("normalWaterLevel, warningWaterLevel und criticalWaterLevel sind erforderlich");
        } else if (!(normal > 0 && normal < warning && warning < critical)) {
            errors.add("es muss gelten: 0 < normalWaterLevel < warningWaterLevel < criticalWaterLevel");
        }
        if (r.getMeanFlowRate() != null && r.getMeanFlowRate() <= 0) {
            errors.add("meanFlowRate muss positiv sein");
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(String.join("; ", errors));
        }
    }

    private Station withCurrentWarningLevel(Station station) {
        station.setCurrentWarningLevel(warningLevelService.current(
                measurementRepository.findLatest(station.getId()).orElse(null), Instant.now()));
        return station;
    }
}
