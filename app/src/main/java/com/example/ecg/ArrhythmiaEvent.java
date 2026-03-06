package com.example.ecg;

import java.io.Serializable;

public class ArrhythmiaEvent implements Serializable {
    private long timestamp;
    private double rmssd;

    public ArrhythmiaEvent(long timestamp, double rmssd) {
        this.timestamp = timestamp;
        this.rmssd = rmssd;
    }

    public long getTimestamp() { return timestamp; }
    public double getRmssd() { return rmssd; }
}

