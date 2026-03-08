package com.example.ecg;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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
    // ================= UI =================
    private LineChart ecgChart;
    private Button btnStart, btnStop, btnResults;
    private TextView txtRMSSD;
    private TextView txtStatus;

    // ================= Chart =================
    private LineDataSet dataSet;
    private LineData lineData;

    // ================= ECG =================
    private boolean isRunning = false;
    private int sampleIndex = 0;

    private ArrayList<Float> ecgSignal = new ArrayList<>();
    private ArrayList<Integer> rPeaks = new ArrayList<>();
    private ArrayList<Double> rrIntervals = new ArrayList<>();

    private static final float FS = 50f;
    private static final int WINDOW_SIZE = 30;
    private float threshold = 0.8f;

    // ================= BLE =================
    private BLEManager bleManager;

    // ================= Notification =================
    private static final String CHANNEL_ID = "ARRHYTHMIA_CHANNEL";
    private static final int NOTIFICATION_PERMISSION_CODE = 1001;
    private static final long NOTIFICATION_COOLDOWN = 60000;

    private long lastNotificationTime = 0;
    private boolean pendingNotification = false;

    // ====================================================
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
        txtStatus = findViewById(R.id.txtStatus);

        txtStatus.setText("BLE aguardando...");

        setupChart();
        createNotificationChannel();

        // Inicializa BLE
        bleManager = new BLEManager(this, sample -> {

            if(!isRunning) return;

            float ecgValue = sample[0];

            runOnUiThread(() -> {

                txtStatus.setText("📈 Recebendo ECG...");
                processECGSample(ecgValue);

            });

        });

        btnStart.setOnClickListener(v -> startECG());
        btnStop.setOnClickListener(v -> stopECG());
        btnResults.setOnClickListener(v -> openResults());
    }

    // ================= CHART =================
    private void setupChart() {

        dataSet = new LineDataSet(new ArrayList<>(), "ECG");
        dataSet.setColor(Color.RED);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);

        lineData = new LineData(dataSet);
        ecgChart.setData(lineData);

        ecgChart.getDescription().setEnabled(false);
        ecgChart.getAxisRight().setEnabled(false);
        ecgChart.getLegend().setEnabled(false);
    }

    // ================= PROCESSA ECG =================
    private void processECGSample(float value){

        ecgSignal.add(value);

        detectRPeak(value);

        dataSet.addEntry(new Entry(sampleIndex, value));
        sampleIndex++;

        lineData.notifyDataChanged();
        ecgChart.notifyDataSetChanged();
        ecgChart.setVisibleXRangeMaximum(300);
        ecgChart.moveViewToX(sampleIndex);
    }

    // ================= DETECÇÃO R PEAK =================
    private void detectRPeak(float value){

        if(value > threshold){

            if(rPeaks.isEmpty() ||
                    sampleIndex -
                            rPeaks.get(rPeaks.size()-1) > 20){

                if(!rPeaks.isEmpty()){

                    double rrMs =
                            (sampleIndex -
                                    rPeaks.get(rPeaks.size()-1))
                                    *(1000.0/FS);

                    rrIntervals.add(rrMs);

                    if(rrIntervals.size()>WINDOW_SIZE)
                        rrIntervals.remove(0);

                    double rmssd =
                            calculateRMSSD(rrIntervals);

                    updateRMSSDUI(rmssd);
                    checkArrhythmia(rmssd);
                }

                rPeaks.add(sampleIndex);
            }
        }
    }

    // ================= RMSSD =================
    private double calculateRMSSD(ArrayList<Double> rr){

        if(rr.size()<3) return 0;

        double sum=0;

        for(int i=1;i<rr.size();i++){
            double diff=rr.get(i)-rr.get(i-1);
            sum+=diff*diff;
        }

        return Math.sqrt(sum/(rr.size()-1));
    }

    private void updateRMSSDUI(double rmssd){
        runOnUiThread(() ->
                txtRMSSD.setText(
                        String.format(
                                Locale.getDefault(),
                                "RMSSD: %.1f ms",
                                rmssd)));
    }

    // ================= ARRITMIA =================
    private void checkArrhythmia(double rmssd){

        boolean arrhythmiaDetected = rmssd > 120 || rmssd < 15;

        if(arrhythmiaDetected){

            long now = System.currentTimeMillis();

            if(now-lastNotificationTime > NOTIFICATION_COOLDOWN){
                triggerNotification(now);
            }
        }
    }

    private void triggerNotification(long time){

        if(Build.VERSION.SDK_INT>=33 &&
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED){

            pendingNotification=true;

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_CODE);

            return;
        }

        sendArrhythmiaNotification();
        lastNotificationTime=time;
    }

    // ================= NOTIFICAÇÃO ================
    private void sendArrhythmiaNotification(){

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        this,CHANNEL_ID)
                        .setSmallIcon(
                                android.R.drawable.ic_dialog_alert)
                        .setContentTitle(
                                "Arritmia detectada")
                        .setContentText(
                                "Possível arritmia cardíaca.")
                        .setPriority(
                                NotificationCompat.PRIORITY_HIGH);

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                NOTIFICATION_SERVICE);

        manager.notify(1,builder.build());
    }

    private void createNotificationChannel(){

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){

            NotificationChannel channel=
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Arritmia",
                            NotificationManager.IMPORTANCE_HIGH);

            getSystemService(NotificationManager.class)
                    .createNotificationChannel(channel);
        }
    }

    // ================= CONTROLES =================
    private void startECG(){

        if(isRunning) return;

        txtStatus.setText("🔍 Procurando ECG...");

        dataSet.clear();
        ecgSignal.clear();
        rPeaks.clear();
        rrIntervals.clear();

        sampleIndex=0;
        txtRMSSD.setText("RMSSD: -- ms");

        ecgChart.invalidate();

        isRunning=true;
    }

    private void stopECG(){
        isRunning=false;
        txtStatus.setText("⏹ ECG parado");
    }

    private void openResults(){

        Intent intent=
                new Intent(this,
                        ResultsActivity.class);

        intent.putExtra("ecg",ecgSignal);
        intent.putExtra("rr",rrIntervals);

        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(bleManager != null){
            bleManager.shutdown();
        }
    }


}
