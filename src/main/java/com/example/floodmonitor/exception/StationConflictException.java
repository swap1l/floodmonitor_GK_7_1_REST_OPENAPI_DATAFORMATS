package com.example.floodmonitor.exception;

public class StationConflictException extends RuntimeException {
    public StationConflictException(String stationId) {
        super("Station " + stationId + " existiert bereits");
    }
}
