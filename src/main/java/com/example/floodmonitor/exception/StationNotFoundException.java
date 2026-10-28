package com.example.floodmonitor.exception;

public class StationNotFoundException extends RuntimeException {
    public StationNotFoundException(String stationId) {
        super("Station " + stationId + " wurde nicht gefunden");
    }
}
