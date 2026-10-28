package com.example.floodmonitor.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.time.Instant;
import java.util.Locale;

/**
 * Aktuelle Hochwasserwarnung einer Station, abgeleitet aus ihrem aktuellsten Messwert.
 */
@JacksonXmlRootElement(localName = "alert")
public class Alert {
    private String stationId;
    private String stationName;
    private String river;
    private WarningLevel warningLevel;
    private double waterLevel;
    private double warningWaterLevel;
    private double criticalWaterLevel;
    private Instant timestamp;
    private String message;

    public Alert() {}

    public Alert(Station station, Measurement measurement) {
        this.stationId = station.getId();
        this.stationName = station.getName();
        this.river = station.getRiver();
        this.warningLevel = measurement.getWarningLevel();
        this.waterLevel = measurement.getWaterLevel();
        this.warningWaterLevel = station.getWarningWaterLevel();
        this.criticalWaterLevel = station.getCriticalWaterLevel();
        this.timestamp = measurement.getTimestamp();
        this.message = String.format(Locale.ROOT, "%s: Wasserstand %.1f cm an Station %s (%s)",
                warningLevel, waterLevel, station.getName(), station.getRiver());
    }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    public String getRiver() { return river; }
    public void setRiver(String river) { this.river = river; }
    public WarningLevel getWarningLevel() { return warningLevel; }
    public void setWarningLevel(WarningLevel warningLevel) { this.warningLevel = warningLevel; }
    public double getWaterLevel() { return waterLevel; }
    public void setWaterLevel(double waterLevel) { this.waterLevel = waterLevel; }
    public double getWarningWaterLevel() { return warningWaterLevel; }
    public void setWarningWaterLevel(double warningWaterLevel) { this.warningWaterLevel = warningWaterLevel; }
    public double getCriticalWaterLevel() { return criticalWaterLevel; }
    public void setCriticalWaterLevel(double criticalWaterLevel) { this.criticalWaterLevel = criticalWaterLevel; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
