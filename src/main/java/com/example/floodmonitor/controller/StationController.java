package com.example.floodmonitor.controller;

import com.example.floodmonitor.config.OpenApiConfig;
import com.example.floodmonitor.model.ApiError;
import com.example.floodmonitor.model.Measurement;
import com.example.floodmonitor.model.Station;
import com.example.floodmonitor.model.StationRequest;
import com.example.floodmonitor.model.Statistics;
import com.example.floodmonitor.model.WarningLevel;
import com.example.floodmonitor.service.MeasurementService;
import com.example.floodmonitor.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/stations",
        produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
@Tag(name = "Stationen", description = "Messstationen, Messwerte und Statistik")
@ApiResponse(responseCode = "400", description = "Ungültige Eingabe (Parameter, Zeitraum, Limit, Warnstufe)",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "406", description = "Accept-Header verlangt weder JSON noch XML (ohne Body)",
        content = @Content)
@ApiResponse(responseCode = "500", description = "Interner Fehler (ohne Stacktrace)",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class StationController {

    private final StationService stationService;
    private final MeasurementService measurementService;

    public StationController(StationService stationService, MeasurementService measurementService) {
        this.stationService = stationService;
        this.measurementService = measurementService;
    }

    @Operation(summary = "Alle Messstationen abrufen", description = "Alle Filter sind optional und kombinierbar.")
    @ApiResponse(responseCode = "200", description = "Liste der Stationen")
    @GetMapping({"", "/xml"})
    public List<Station> getStations(
            @Parameter(description = "Fluss, Groß-/Kleinschreibung egal", example = "Donau")
            @RequestParam(required = false) String river,
            @Parameter(description = "nur aktive bzw. deaktivierte Stationen")
            @RequestParam(required = false) Boolean isActive,
            @Parameter(description = "aktuelle Warnstufe der Station")
            @RequestParam(required = false) WarningLevel warningLevel) {
        return stationService.findAll(river, isActive, warningLevel);
    }

    @Operation(summary = "Einzelne Station abrufen")
    @ApiResponse(responseCode = "200", description = "Station gefunden")
    @ApiResponse(responseCode = "404", description = "Station nicht gefunden",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping({"/{stationId}", "/{stationId}/xml"})
    public Station getStation(@Parameter(example = "ST-001") @PathVariable String stationId) {
        return stationService.findById(stationId);
    }

    @Operation(summary = "Neue Station anlegen",
            description = "Body als JSON oder XML. Warnstufe und Status werden serverseitig bestimmt. Benötigt X-API-Key.",
            security = @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME))
    @ApiResponse(responseCode = "201", description = "Station angelegt, Location-Header zeigt auf die neue Ressource")
    @ApiResponse(responseCode = "401", description = "X-API-Key fehlt",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "X-API-Key ungültig",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Station mit dieser ID existiert bereits",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type weder JSON noch XML",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    public ResponseEntity<Station> createStation(@RequestBody StationRequest request) {
        Station station = stationService.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(station.getId()).toUri())
                .body(station);
    }

    @Operation(summary = "Station löschen", description = "Löscht die Station samt Messungen. Benötigt X-API-Key.",
            security = @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME))
    @ApiResponse(responseCode = "204", description = "Station gelöscht, keine Antwortdaten")
    @ApiResponse(responseCode = "401", description = "X-API-Key fehlt",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "X-API-Key ungültig",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Station nicht gefunden",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @DeleteMapping("/{stationId}")
    public ResponseEntity<Void> deleteStation(@PathVariable String stationId) {
        stationService.delete(stationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Aktuellsten Messwert abrufen")
    @ApiResponse(responseCode = "200", description = "Aktuellste Messung")
    @ApiResponse(responseCode = "404", description = "Station nicht gefunden oder noch keine Messung",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping({"/{stationId}/measurements/latest", "/{stationId}/measurements/latest/xml"})
    public Measurement getLatestMeasurement(@Parameter(example = "ST-001") @PathVariable String stationId) {
        return measurementService.getLatest(stationId);
    }

    @Operation(summary = "Historische Messwerte abrufen", description = "Neueste zuerst. from/to sind inklusive.")
    @ApiResponse(responseCode = "200", description = "Liste der Messungen")
    @ApiResponse(responseCode = "404", description = "Station nicht gefunden",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping({"/{stationId}/measurements", "/{stationId}/measurements/xml"})
    public List<Measurement> getMeasurements(
            @Parameter(example = "ST-001") @PathVariable String stationId,
            @Parameter(description = "Beginn (ISO-8601)", example = "2026-05-01T08:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Ende (ISO-8601), darf nicht vor from liegen", example = "2026-05-01T12:00:00Z")
            @RequestParam(required = false) Instant to,
            @Parameter(description = "maximale Anzahl, 1–" + MeasurementService.MAX_LIMIT
                    + ", Standard " + MeasurementService.DEFAULT_LIMIT)
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "nur Messungen mit dieser Warnstufe")
            @RequestParam(required = false) WarningLevel warningLevel) {
        return measurementService.getHistory(stationId, from, to, limit, warningLevel);
    }

    @Operation(summary = "Statistik einer Station",
            description = "Min/Max/Durchschnitt Wasserstand, Durchschnitt Durchfluss, Niederschlagssumme, Anzahl Messungen/Warnungen.")
    @ApiResponse(responseCode = "200", description = "Statistik")
    @ApiResponse(responseCode = "404", description = "Station nicht gefunden",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping({"/{stationId}/statistics", "/{stationId}/statistics/xml"})
    public Statistics getStatistics(@Parameter(example = "ST-001") @PathVariable String stationId,
                                    @RequestParam(required = false) Instant from,
                                    @RequestParam(required = false) Instant to) {
        return measurementService.getStatistics(stationId, from, to);
    }
}
