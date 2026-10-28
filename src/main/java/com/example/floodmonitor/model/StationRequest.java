package com.example.floodmonitor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Request-Body zum Anlegen einer Station (JSON oder XML). Status und Warnstufe sind nicht enthalten,
 * weil sie serverseitig bestimmt werden. Wasserstände in cm.
 */
@JacksonXmlRootElement(localName = "station")
public class StationRequest {
    private String id;
    private String name;
    private String river;
    private Location location;
    private Double normalWaterLevel;
    private Double warningWaterLevel;
    private Double criticalWaterLevel;
    @JsonProperty("isActive")
    private Boolean active;
    /** Optional: mittlere Durchflussmenge bei Normalwasserstand (m³/s), Standard 500. */
    private Double meanFlowRate;
    /** Optional: typische Lufttemperatur (°C), Standard 12. */
    private Double baseTemperature;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRiver() { return river; }
    public void setRiver(String river) { this.river = river; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public Double getNormalWaterLevel() { return normalWaterLevel; }
    public void setNormalWaterLevel(Double normalWaterLevel) { this.normalWaterLevel = normalWaterLevel; }
    public Double getWarningWaterLevel() { return warningWaterLevel; }
    public void setWarningWaterLevel(Double warningWaterLevel) { this.warningWaterLevel = warningWaterLevel; }
    public Double getCriticalWaterLevel() { return criticalWaterLevel; }
    public void setCriticalWaterLevel(Double criticalWaterLevel) { this.criticalWaterLevel = criticalWaterLevel; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Double getMeanFlowRate() { return meanFlowRate; }
    public void setMeanFlowRate(Double meanFlowRate) { this.meanFlowRate = meanFlowRate; }
    public Double getBaseTemperature() { return baseTemperature; }
    public void setBaseTemperature(Double baseTemperature) { this.baseTemperature = baseTemperature; }
}
