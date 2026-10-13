package com.aerotrack;

import java.time.LocalDateTime;

/** One simulated telemetry packet, kept separate from database code. */
public record TelemetryPacket(
        int packetNo, LocalDateTime recordedAt, double altitude, double velocity,
        double temperature, double batteryVoltage, double latitude, double longitude,
        String flightState) {
}
