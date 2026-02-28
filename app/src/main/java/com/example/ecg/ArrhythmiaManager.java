package com.example.ecg;

import java.util.ArrayList;

public class ArrhythmiaManager {

    private static final double RMSSD_THRESHOLD = 80.0;
    private static final long COOLDOWN = 60000;

    private boolean isArrhythmic = false;
    private long lastEventTime = 0;

    public ArrhythmiaEvent check(
            double rmssd,
            ArrayList<Float> ecg,
            ArrayList<Double> rr) {

        long now = System.currentTimeMillis();

        boolean detected = rmssd > RMSSD_THRESHOLD;

        // entrou em arritmia
        if (detected &&
                !isArrhythmic &&
                now - lastEventTime > COOLDOWN) {

            isArrhythmic = true;
            lastEventTime = now;

            return new ArrhythmiaEvent(
                    now,
                    rmssd,
                    new ArrayList<>(ecg),
                    new ArrayList<>(rr)
            );
        }

        // saiu da arritmia
        if (!detected)
            isArrhythmic = false;

        return null;
    }
}