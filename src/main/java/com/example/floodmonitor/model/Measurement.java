package com.example.floodmonitor.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.time.Instant;

/**
 * Einzelne Messung einer Station.
 * waterLevel in cm, flowRate in m³/s, rainfall in mm/h, temperature in °C, batteryLevel in %.
 */
@JacksonXmlRootElement(localName = "measurement")
@JsonPropertyOrder({"stationId", "timestamp", "waterLevel", "flowRate", "rainfall",
        "temperature", "batteryLevel", "status", "warningLevel"})
public class Measurement {
    private String stationId;
    private Instant timestamp;
    private double waterLevel;
    private double flowRate;
    private double rainfall;
    private double temperature;
    private int batteryLevel;
    private StationStatus status;
    private WarningLevel warningLevel;

    public Measurement() {}

    public Measurement(String stationId, Instant timestamp, double waterLevel, double flowRate,
                       double rainfall, double temperature, int batteryLevel, StationStatus status) {
        this.stationId = stationId;
        this.timestamp = timestamp;
        this.waterLevel = waterLevel;
        this.flowRate = flowRate;
        this.rainfall = rainfall;
        this.temperature = temperature;
        this.batteryLevel = batteryLevel;
        this.status = status;
    }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public double getWaterLevel() { return waterLevel; }
    public void setWaterLevel(double waterLevel) { this.waterLevel = waterLevel; }
    public double getFlowRate() { return flowRate; }
    public void setFlowRate(double flowRate) { this.flowRate = flowRate; }
    public double getRainfall() { return rainfall; }
    public void setRainfall(double rainfall) { this.rainfall = rainfall; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(int batteryLevel) { this.batteryLevel = batteryLevel; }
    public StationStatus getStatus() { return status; }
    public void setStatus(StationStatus status) { this.status = status; }
    public WarningLevel getWarningLevel() { return warningLevel; }
    public void setWarningLevel(WarningLevel warningLevel) { this.warningLevel = warningLevel; }
}
