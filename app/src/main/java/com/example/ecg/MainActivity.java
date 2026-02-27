package com.example.ecg;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private LineChart ecgChart;
    private Button btnStart, btnStop, btnResults;
    private TextView txtRMSSD;

    private LineDataSet dataSet;
    private LineData lineData;

    private Handler handler = new Handler();
    private boolean isRunning = false;
    private int sampleIndex = 0;

    private ArrayList<Float> ecgSignal = new ArrayList<>();
    private ArrayList<Integer> rPeaks = new ArrayList<>();
    private ArrayList<Double> rrIntervals = new ArrayList<>();

    private static final int WINDOW_SIZE = 30;
    private static final float FS = 50f;
    private float threshold = 0.8f;

    private static final double RMSSD_ARRHYTHMIA_THRESHOLD = 80.0;
    private boolean arrhythmiaDetected = false;

    private static final String CHANNEL_ID = "ARRHYTHMIA_CHANNEL";
    private static final int NOTIFICATION_PERMISSION_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ecgChart = findViewById(R.id.ecgChart);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnResults = findViewById(R.id.btnResults);
        txtRMSSD = findViewById(R.id.txtRMSSD);

        setupChart();
        createNotificationChannel();

        btnStart.setOnClickListener(v -> startECG());
        btnStop.setOnClickListener(v -> stopECG());
        btnResults.setOnClickListener(v -> openResults());
    }

    private void setupChart() {
        dataSet = new LineDataSet(new ArrayList<>(), "ECG");
        dataSet.setColor(Color.RED);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);

        lineData = new LineData(dataSet);
        ecgChart.setData(lineData);

        ecgChart.getDescription().setEnabled(false);
        ecgChart.getLegend().setEnabled(false);
        ecgChart.getAxisRight().setEnabled(false);
    }

    private float getECGSample() {
        return generateECGSample(sampleIndex);
    }

    private float generateECGSample(int i) {
        float hr = 75.0f + 5.0f * (float) Math.sin(2 * Math.PI * i / 500.0);
        float rr = FS * 60.0f / hr;
        float t = i % rr;

        float p  = gaussian(t, 0.20f * rr, 0.025f * rr, 0.10f);
        float q  = gaussian(t, 0.45f * rr, 0.010f * rr, -0.15f);
        float r  = gaussian(t, 0.50f * rr, 0.012f * rr, 1.20f);
        float s  = gaussian(t, 0.55f * rr, 0.010f * rr, -0.25f);
        float tW = gaussian(t, 0.75f * rr, 0.050f * rr, 0.30f);

        return p + q + r + s + tW;
    }

    private float gaussian(float x, float mu, float sigma, float amp) {
        return amp * (float) Math.exp(-0.5f * Math.pow((x - mu) / sigma, 2));
    }

    private Runnable ecgRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            float value = getECGSample();
            ecgSignal.add(value);

            detectRPeak(value);

            dataSet.addEntry(new Entry(sampleIndex, value));
            sampleIndex++;

            lineData.notifyDataChanged();
            ecgChart.notifyDataSetChanged();
            ecgChart.setVisibleXRangeMaximum(300);
            ecgChart.moveViewToX(sampleIndex);

            handler.postDelayed(this, 20);
        }
    };

    private void detectRPeak(float value) {
        if (value > threshold) {
            if (rPeaks.isEmpty() || sampleIndex - rPeaks.get(rPeaks.size() - 1) > 20) {

                if (!rPeaks.isEmpty()) {
                    double rrMs = (sampleIndex - rPeaks.get(rPeaks.size() - 1)) * (1000.0 / FS);
                    rrIntervals.add(rrMs);

                    if (rrIntervals.size() > WINDOW_SIZE) {
                        rrIntervals.remove(0);
                    }

                    double rmssd = calculateRMSSD(rrIntervals);
                    updateRMSSDUI(rmssd);
                    checkArrhythmia(rmssd);
                }

                rPeaks.add(sampleIndex);
            }
        }
    }

    private void checkArrhythmia(double rmssd) {
        if (rmssd > RMSSD_ARRHYTHMIA_THRESHOLD && !arrhythmiaDetected) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {

                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            NOTIFICATION_PERMISSION_CODE);
                    return;
                }
            }

            arrhythmiaDetected = true;
            sendArrhythmiaNotification();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                arrhythmiaDetected = true;
                sendArrhythmiaNotification();
            }
        }
    }

    private double calculateRMSSD(ArrayList<Double> rr) {
        if (rr.size() < 3) return 0.0;

        double sum = 0.0;
        for (int i = 1; i < rr.size(); i++) {
            double diff = rr.get(i) - rr.get(i - 1);
            sum += diff * diff;
        }
        return Math.sqrt(sum / (rr.size() - 1));
    }

    private void updateRMSSDUI(double rmssd) {
        runOnUiThread(() ->
                txtRMSSD.setText(
                        String.format(Locale.getDefault(), "RMSSD: %.1f ms", rmssd)
                ));
    }

    private void startECG() {
        if (isRunning) return;

        dataSet.clear();
        ecgSignal.clear();
        rPeaks.clear();
        rrIntervals.clear();

        sampleIndex = 0;
        arrhythmiaDetected = false;

        txtRMSSD.setText("RMSSD: -- ms");
        ecgChart.invalidate();

        isRunning = true;
        handler.post(ecgRunnable);
    }

    private void stopECG() {
        isRunning = false;
    }

    private void openResults() {
        Intent intent = new Intent(this, ResultsActivity.class);
        intent.putExtra("ecg", ecgSignal);
        intent.putExtra("rr", rrIntervals);
        startActivity(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Arritmia",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void sendArrhythmiaNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Arritmia detectada")
                .setContentText("Foi detectada uma possível arritmia.")
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        manager.notify(1, builder.build());
    }
}