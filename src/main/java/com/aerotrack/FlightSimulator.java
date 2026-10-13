package com.aerotrack;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Generates one packet at a time. It does not access SQL directly. */
public class FlightSimulator {
    public static final int PACKETS_PER_RUN = 30;

    public TelemetryPacket createPacket(int packetNo, String scenario,
                                        String manualAnomaly, int manualIndex) {
        int n = packetNo;
        double altitude, velocity;
        String phase;

        if (n < 3) {
            altitude = 0; velocity = 0; phase = "PAD";
        } else if (n < 13) {
            altitude = 100.0 * (n - 2) + 4 * Math.sin(n * 0.8);
            velocity = 25 + 5 * Math.sin(n * 0.5); phase = "ASCENT";
        } else if (n < 16) {
            altitude = 1000 + 15 * Math.sin(n); velocity = 2 * Math.cos(n); phase = "APOGEE";
        } else if (n < 29) {
            altitude = Math.max(0, 1000 - (n - 15) * 78 + 5 * Math.sin(n * 0.7));
            velocity = -35 + 4 * Math.sin(n * 0.6); phase = "DESCENT";
        } else {
            altitude = 0; velocity = 0; phase = "LANDED";
        }

        double temperature = 27 + 0.32 * n + 0.7 * Math.sin(n * 0.55);
        double battery = 12.6 - 0.075 * n + 0.025 * Math.cos(n * 0.4);
        double latitude = 17.385 + n * 0.00015;
        double longitude = 78.4867 + n * 0.00012;

        if ("High Temperature".equals(scenario) && n >= 8) temperature += 12;
        if ("Low Battery".equals(scenario) && n >= 7) battery -= 2.5;
        if ("Abnormal Altitude".equals(scenario) && n >= 8 && n <= 18) altitude += 300;

        if (n == manualIndex) {
            if ("High Temperature".equals(manualAnomaly)) temperature = 42;
            if ("Low Battery".equals(manualAnomaly)) battery = 9.5;
            if ("Abnormal Altitude".equals(manualAnomaly)) altitude = 1250;
        }

        return new TelemetryPacket(n, LocalDateTime.now(), Math.max(0, altitude),
                velocity, temperature, Math.max(0, battery), latitude, longitude, phase);
    }

    /** Example of Java Collections use: creates a safe snapshot of generated packets. */
    public List<TelemetryPacket> preview(String scenario) {
        List<TelemetryPacket> packets = new ArrayList<>();
        for (int i = 0; i < PACKETS_PER_RUN; i++) {
            packets.add(createPacket(i, scenario, "None", -1));
        }
        return List.copyOf(packets);
    }
}
