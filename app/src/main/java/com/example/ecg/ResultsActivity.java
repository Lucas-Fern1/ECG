package com.example.ecg;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Locale;

public class ResultsActivity extends AppCompatActivity {

    // UI
    private LineChart ecgChart;
    private TextView txtRMSSD;
    private Button btnSaveCSV;

    // Dados
    private ArrayList<Float> ecgSignal;
    private ArrayList<Double> rrIntervals;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_results);

        ecgChart = findViewById(R.id.ecgChartResults);
        txtRMSSD = findViewById(R.id.txtRMSSDResults);
        btnSaveCSV = findViewById(R.id.btnSaveCSV);

        // Recebe dados do MainActivity
        ecgSignal = (ArrayList<Float>) getIntent().getSerializableExtra("ecg");
        rrIntervals = (ArrayList<Double>) getIntent().getSerializableExtra("rr");

        if (ecgSignal == null || rrIntervals == null) {
            Toast.makeText(this, "Erro ao carregar dados", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupChart();
        plotECG();
        showRMSSD();

        btnSaveCSV.setOnClickListener(v -> saveECGToCSV());
    }

    // ===============================
    // GRÁFICO
    // ===============================
    private void setupChart() {
        ecgChart.getDescription().setEnabled(false);
        ecgChart.getAxisRight().setEnabled(false);
        ecgChart.getLegend().setEnabled(false);
    }

    private void plotECG() {
        ArrayList<Entry> entries = new ArrayList<>();

        for (int i = 0; i < ecgSignal.size(); i++) {
            entries.add(new Entry(i, ecgSignal.get(i)));
        }

        LineDataSet dataSet = new LineDataSet(entries, "ECG");
        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(2f);

        LineData data = new LineData(dataSet);
        ecgChart.setData(data);
        ecgChart.invalidate();
    }

    // ===============================
    // RMSSD
    // ===============================
    private void showRMSSD() {
        if (rrIntervals.size() < 2) {
            txtRMSSD.setText("RMSSD: -- ms");
            return;
        }

        double rmssd = calculateRMSSD(rrIntervals);

        txtRMSSD.setText(
                String.format(Locale.getDefault(),
                        "RMSSD: %.1f ms", rmssd)
        );
    }

    private double calculateRMSSD(ArrayList<Double> rr) {
        double sum = 0.0;

        for (int i = 1; i < rr.size(); i++) {
            double diff = rr.get(i) - rr.get(i - 1);
            sum += diff * diff;
        }
        return Math.sqrt(sum / (rr.size() - 1));
    }

    // ===============================
    // SALVAR CSV (opcional pelo usuário)
    // ===============================
    private void saveECGToCSV() {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    100);
            return;
        }

        try {
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOCUMENTS),
                    "ECG"
            );

            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "ecg_data.csv");
            FileWriter writer = new FileWriter(file);

            writer.append("Sample,ECG\n");

            for (int i = 0; i < ecgSignal.size(); i++) {
                writer.append(i + "," + ecgSignal.get(i) + "\n");
            }

            writer.flush();
            writer.close();

            Toast.makeText(this,
                    "ECG salvo em: " + file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this,
                    "Erro ao salvar CSV",
                    Toast.LENGTH_LONG).show();
        }
    }
}