package com.example.floodmonitor.exception;

public class NoMeasurementException extends RuntimeException {
    public NoMeasurementException(String stationId) {
        super("Für Station " + stationId + " liegt keine Messung vor");
    }
}
