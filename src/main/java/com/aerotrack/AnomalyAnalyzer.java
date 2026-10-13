package com.aerotrack;

import java.util.ArrayList;
import java.util.List;

/** Business rules for anomaly detection; rules are deliberately simple for a student project. */
public class AnomalyAnalyzer {
    public record Finding(String type, String severity, String message, double value) { }

    public List<Finding> analyze(TelemetryPacket packet) {
        List<Finding> findings = new ArrayList<>();
        if (packet.temperature() > 35) {
            findings.add(new Finding("HIGH_TEMPERATURE", "WARNING",
                    "Temperature exceeded 35 C", packet.temperature()));
        }
        if (packet.batteryVoltage() < 11) {
            findings.add(new Finding("LOW_BATTERY", "WARNING",
                    "Battery fell below 11 V", packet.batteryVoltage()));
        }
        if (packet.altitude() > 1100) {
            findings.add(new Finding("ABNORMAL_ALTITUDE", "WARNING",
                    "Altitude exceeded 1100 m", packet.altitude()));
        }
        return findings;
    }
}
