package com.example.ecg;

import java.io.Serializable;
import java.util.ArrayList;

public class ArrhythmiaEvent implements Serializable {

    private long timestamp;
    private double rmssd;
    private ArrayList<Float> ecgWindow;
    private ArrayList<Double> rrWindow;

    public ArrhythmiaEvent(
            long timestamp,
            double rmssd,
            ArrayList<Float> ecgWindow,
            ArrayList<Double> rrWindow) {

        this.timestamp = timestamp;
        this.rmssd = rmssd;
        this.ecgWindow = ecgWindow;
        this.rrWindow = rrWindow;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getRmssd() {
        return rmssd;
    }

    public ArrayList<Float> getEcgWindow() {
        return ecgWindow;
    }

    public ArrayList<Double> getRrWindow() {
        return rrWindow;
    }
}