package com.example.ecg;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ResultsActivity extends AppCompatActivity {

    private TextView txtEvents;
    private Button btnSaveCSV;
    private ArrayList<ArrhythmiaEvent> events;

    private static final int STORAGE_PERMISSION = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arrhythmia_data);

        txtEvents = findViewById(R.id.txtEvents);
        btnSaveCSV = findViewById(R.id.btnSaveCSV);

        events = loadEvents();
        displayEvents();

        btnSaveCSV.setOnClickListener(v -> saveEventsToCSV());
    }

    private ArrayList<ArrhythmiaEvent> loadEvents() {
        ArrayList<ArrhythmiaEvent> list = new ArrayList<>();
        String serialized = getSharedPreferences("arrhythmia_data", MODE_PRIVATE)
                .getString("events", null);
        if (serialized != null) {
            try {
                list = SerializationHelper.deserialize(serialized);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    private void displayEvents() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        for (ArrhythmiaEvent e : events) {
            String timeStr = sdf.format(new Date(e.getTimestamp()));
            sb.append(String.format(Locale.getDefault(),
                    "Horário: %s, RMSSD: %.1f ms\n", timeStr, e.getRmssd()));
        }
        txtEvents.setText(sb.toString());
    }

    private void saveEventsToCSV() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION);
            return;
        }

        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "ECG");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "ECG_Events_" + System.currentTimeMillis() + ".csv");
            FileWriter writer = new FileWriter(file);

            writer.append("Timestamp,RMSSD\n");
            for (ArrhythmiaEvent e : events) {
                writer.append(e.getTimestamp() + "," + e.getRmssd() + "\n");
            }

            writer.flush();
            writer.close();
            Toast.makeText(this, "Arquivo salvo em:\n" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar CSV", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}