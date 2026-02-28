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

    private LineChart ecgChart;
    private TextView txtRMSSD;
    private Button btnSaveCSV;

    private ArrhythmiaEvent event;

    private static final int STORAGE_PERMISSION = 200;

    // ===================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_results);

        ecgChart = findViewById(R.id.ecgChartResults);
        txtRMSSD = findViewById(R.id.txtRMSSDResults);
        btnSaveCSV = findViewById(R.id.btnSaveCSV);

        event =
                (ArrhythmiaEvent)
                        getIntent().getSerializableExtra("event");

        if (event == null) {
            Toast.makeText(this,
                    "Evento inválido",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupChart();
        plotECG();
        showRMSSD();

        btnSaveCSV.setOnClickListener(v -> saveECGToCSV());
    }

    // ===================================================
    // CHART
    // ===================================================
    private void setupChart() {

        ecgChart.getDescription().setEnabled(false);
        ecgChart.getAxisRight().setEnabled(false);
        ecgChart.getLegend().setEnabled(false);
    }

    private void plotECG() {

        ArrayList<Entry> entries = new ArrayList<>();

        ArrayList<Float> ecg =
                event.getEcgWindow();

        for (int i = 0; i < ecg.size(); i++) {
            entries.add(new Entry(i, ecg.get(i)));
        }

        LineDataSet dataSet =
                new LineDataSet(entries, "ECG");

        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(2f);

        ecgChart.setData(new LineData(dataSet));
        ecgChart.invalidate();
    }

    // ===================================================
    // RMSSD
    // ===================================================
    private void showRMSSD() {

        txtRMSSD.setText(
                String.format(
                        Locale.getDefault(),
                        "RMSSD: %.1f ms",
                        event.getRmssd()
                ));
    }

    // ===================================================
    // CSV SAVE
    // ===================================================
    private void saveECGToCSV() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION);

            return;
        }

        try {

            File dir = new File(
                    Environment
                            .getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOCUMENTS),
                    "ECG");

            if (!dir.exists())
                dir.mkdirs();

            File file = new File(
                    dir,
                    "arrhythmia_" +
                            event.getTimestamp() +
                            ".csv");

            FileWriter writer =
                    new FileWriter(file);

            // ----- METADADOS -----
            writer.append("Timestamp,RMSSD\n");
            writer.append(
                    event.getTimestamp() + "," +
                            event.getRmssd() + "\n\n");

            // ----- ECG -----
            writer.append("Sample,ECG\n");

            ArrayList<Float> ecg =
                    event.getEcgWindow();

            for (int i = 0; i < ecg.size(); i++) {
                writer.append(
                        i + "," +
                                ecg.get(i) + "\n");
            }

            writer.flush();
            writer.close();

            Toast.makeText(
                    this,
                    "Arquivo salvo em:\n" +
                            file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Erro ao salvar CSV",
                    Toast.LENGTH_LONG).show();

            e.printStackTrace();
        }
    }
}