package com.example.floodmonitor.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Messstation. Alle Wasserstände in cm.
 */
@JacksonXmlRootElement(localName = "station")
@JsonPropertyOrder({"id", "name", "river", "location", "normalWaterLevel", "warningWaterLevel",
        "criticalWaterLevel", "isActive", "status", "currentWarningLevel"})
public class Station {
    private String id;
    private String name;
    private String river;
    private Location location;
    private double normalWaterLevel;
    private double warningWaterLevel;
    private double criticalWaterLevel;
    @JsonProperty("isActive")
    private volatile boolean active;
    private volatile StationStatus status = StationStatus.ONLINE;
    /** Wird serverseitig aus dem aktuellsten Messwert berechnet. */
    private volatile WarningLevel currentWarningLevel = WarningLevel.UNKNOWN;
    /** Mittlere Durchflussmenge bei Normalwasserstand (m³/s), Basis für die Simulation. */
    @JsonIgnore
    private double meanFlowRate;
    /** Typische Lufttemperatur am Standort (°C), Basis für die Simulation. */
    @JsonIgnore
    private double baseTemperature;

    public Station() {}

    public Station(String id, String name, String river, Location location,
                   double normalWaterLevel, double warningWaterLevel, double criticalWaterLevel,
                   boolean active, double meanFlowRate, double baseTemperature) {
        this.id = id;
        this.name = name;
        this.river = river;
        this.location = location;
        this.normalWaterLevel = normalWaterLevel;
        this.warningWaterLevel = warningWaterLevel;
        this.criticalWaterLevel = criticalWaterLevel;
        this.active = active;
        this.meanFlowRate = meanFlowRate;
        this.baseTemperature = baseTemperature;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRiver() { return river; }
    public void setRiver(String river) { this.river = river; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public double getNormalWaterLevel() { return normalWaterLevel; }
    public void setNormalWaterLevel(double normalWaterLevel) { this.normalWaterLevel = normalWaterLevel; }
    public double getWarningWaterLevel() { return warningWaterLevel; }
    public void setWarningWaterLevel(double warningWaterLevel) { this.warningWaterLevel = warningWaterLevel; }
    public double getCriticalWaterLevel() { return criticalWaterLevel; }
    public void setCriticalWaterLevel(double criticalWaterLevel) { this.criticalWaterLevel = criticalWaterLevel; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public StationStatus getStatus() { return status; }
    public void setStatus(StationStatus status) { this.status = status; }
    public WarningLevel getCurrentWarningLevel() { return currentWarningLevel; }
    public void setCurrentWarningLevel(WarningLevel currentWarningLevel) { this.currentWarningLevel = currentWarningLevel; }
    public double getMeanFlowRate() { return meanFlowRate; }
    public void setMeanFlowRate(double meanFlowRate) { this.meanFlowRate = meanFlowRate; }
    public double getBaseTemperature() { return baseTemperature; }
    public void setBaseTemperature(double baseTemperature) { this.baseTemperature = baseTemperature; }
}
