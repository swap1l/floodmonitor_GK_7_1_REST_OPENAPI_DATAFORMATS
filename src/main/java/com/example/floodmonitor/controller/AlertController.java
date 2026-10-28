package com.example.floodmonitor.controller;

import com.example.floodmonitor.model.Alert;
import com.example.floodmonitor.model.ApiError;
import com.example.floodmonitor.model.WarningLevel;
import com.example.floodmonitor.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/alerts",
        produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
@Tag(name = "Warnungen")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @Operation(summary = "Aktuelle Warnungen abrufen",
            description = "Aktive Stationen mit aktueller Warnstufe WARNING oder CRITICAL, kritische zuerst.")
    @ApiResponse(responseCode = "200", description = "Liste der Warnungen (ggf. leer)")
    @ApiResponse(responseCode = "400", description = "Ungültige Warnstufe",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "406", description = "Accept-Header verlangt weder JSON noch XML", content = @Content)
    @GetMapping({"", "/xml"})
    public List<Alert> getAlerts(@Parameter(description = "WARNING oder CRITICAL")
                                 @RequestParam(required = false) WarningLevel warningLevel,
                                 @Parameter(example = "Donau")
                                 @RequestParam(required = false) String river) {
        return alertService.findCurrentAlerts(warningLevel, river);
    }
}
