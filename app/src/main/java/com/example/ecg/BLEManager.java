package com.example.ecg;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class BLEManager {

    private static final String TAG = "BLEManager";

    // ================= UUID =================
    private static final UUID SERVICE_UUID =
            UUID.fromString("4FAF0101-FBCF-4309-8A1C-8472B7098485");

    private static final UUID CHARACTERISTIC_DATA_UUID =
            UUID.fromString("AEB5483E-36E1-4688-B7F5-EA07361B26A9");

    private static final UUID CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final String ECG_BLE_NAME = "ECG_SIMULATOR";

    // ================= Android =================
    private final AppCompatActivity activity;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<String[]> permissionLauncher;

    // ================= BLE =================
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothScanner;
    private BluetoothGatt bluetoothGatt;

    private boolean isScanning = false;
    private boolean isConnecting = false;
    private boolean isConnected = false;

    // ================= Buffer =================
    private final Queue<byte[]> dataQueue = new LinkedList<>();
    private final Object bufferLock = new Object();
    private boolean bufferingEnabled = true;

    // ================= Listener =================
    public interface DataListener {
        void onECGDataReceived(int[] sample);
    }

    private DataListener listener;

    // ======================================================
    // CONSTRUTOR
    // ======================================================

    @SuppressLint("MissingPermission")
    public BLEManager(@NonNull Context ctx, DataListener listener) {

        this.context = ctx;
        this.listener = listener;

        if (!(ctx instanceof AppCompatActivity)) {
            throw new IllegalArgumentException("BLEManager requer AppCompatActivity");
        }

        this.activity = (AppCompatActivity) ctx;

        BluetoothManager manager =
                (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);

        if (manager != null) {
            bluetoothAdapter = manager.getAdapter();
        }

        if (bluetoothAdapter != null) {
            bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        // ================= REGISTRA PERMISSÕES =================

        permissionLauncher =
                activity.registerForActivityResult(
                        new ActivityResultContracts.RequestMultiplePermissions(),
                        result -> {

                            boolean granted = true;

                            for (Boolean value : result.values()) {
                                if (!value) {
                                    granted = false;
                                    break;
                                }
                            }

                            if (granted) {
                                Log.d(TAG, "Permissões BLE concedidas");
                                startScanning();
                            } else {
                                Log.e(TAG, "Permissões BLE negadas");
                            }
                        });

        requestBlePermissions();
    }

    // ======================================================
    // PERMISSÕES
    // ======================================================

    private String[] getPermissionsToRequest() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        } else {
            return new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
    }

    private boolean checkPermissions() {

        for (String perm : getPermissionsToRequest()) {

            if (ContextCompat.checkSelfPermission(context, perm)
                    != PackageManager.PERMISSION_GRANTED) {

                return false;
            }
        }

        return true;
    }

    private void requestBlePermissions() {

        if (!checkPermissions()) {

            permissionLauncher.launch(getPermissionsToRequest());

        } else {

            startScanning();
        }
    }

    // ======================================================
    // SCAN BLE
    // ======================================================

    @SuppressLint("MissingPermission")
    private void startScanning() {

        if (isScanning || isConnected || bluetoothScanner == null)
            return;

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {

            Log.e(TAG, "Bluetooth desligado");
            return;
        }

        Log.d(TAG, "Iniciando scan BLE");

        isScanning = true;

        bluetoothScanner.startScan(scanCallback);
    }

    private final ScanCallback scanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            BluetoothDevice device = result.getDevice();

            if (device.getName() != null &&
                    device.getName().equals(ECG_BLE_NAME) &&
                    !isConnecting) {

                Log.d(TAG, "Dispositivo ECG encontrado");

                if (ContextCompat.checkSelfPermission(context,
                        Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                bluetoothScanner.stopScan(this);
                isScanning = false;

                connectToDevice(device);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {

            Log.e(TAG, "Scan BLE falhou: " + errorCode);

            isScanning = false;

            handler.postDelayed(
                    BLEManager.this::startScanning,
                    2000
            );
        }
    };

    // ======================================================
    // CONEXÃO GATT
    // ======================================================

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {

        if (device == null || isConnecting)
            return;

        Log.d(TAG, "Conectando ao dispositivo ECG...");

        isConnecting = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            bluetoothGatt =
                    device.connectGatt(
                            context,
                            false,
                            gattCallback,
                            BluetoothDevice.TRANSPORT_LE);

        } else {

            bluetoothGatt =
                    device.connectGatt(
                            context,
                            false,
                            gattCallback);
        }
    }

    private final BluetoothGattCallback gattCallback =
            new BluetoothGattCallback() {

                @Override
                public void onConnectionStateChange(
                        BluetoothGatt gatt,
                        int status,
                        int newState) {

                    if (newState == BluetoothProfile.STATE_CONNECTED) {

                        Log.d(TAG, "Conectado ao GATT");

                        isConnected = true;
                        isConnecting = false;

                        if (ContextCompat.checkSelfPermission(context,
                                Manifest.permission.BLUETOOTH_CONNECT)
                                == PackageManager.PERMISSION_GRANTED) {

                            gatt.discoverServices();
                        }

                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                        Log.d(TAG, "Desconectado. Reconectando...");

                        isConnected = false;
                        isConnecting = false;

                        bluetoothGatt = null;

                        handler.postDelayed(
                                BLEManager.this::startScanning,
                                1000);
                    }
                }

                @Override
                public void onServicesDiscovered(
                        BluetoothGatt gatt,
                        int status) {

                    if (status == BluetoothGatt.GATT_SUCCESS) {

                        enableNotifications(gatt);
                    }
                }

                @Override
                public void onCharacteristicChanged(
                        BluetoothGatt gatt,
                        BluetoothGattCharacteristic characteristic) {

                    if (CHARACTERISTIC_DATA_UUID.equals(
                            characteristic.getUuid())) {

                        processData(characteristic.getValue());
                    }
                }
            };

    // ======================================================
    // ATIVAR NOTIFICAÇÕES
    // ======================================================

    @SuppressLint("MissingPermission")
    private void enableNotifications(BluetoothGatt gatt) {

        BluetoothGattService service =
                gatt.getService(SERVICE_UUID);

        if (service == null) return;

        BluetoothGattCharacteristic characteristic =
                service.getCharacteristic(CHARACTERISTIC_DATA_UUID);

        if (characteristic == null) return;

        gatt.setCharacteristicNotification(characteristic, true);

        BluetoothGattDescriptor descriptor =
                characteristic.getDescriptor(CCCD_UUID);

        if (descriptor != null) {

            descriptor.setValue(
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

            gatt.writeDescriptor(descriptor);
        }

        Log.d(TAG, "Notificações ECG ativadas");
    }

    // ======================================================
    // PROCESSAMENTO ECG
    // ======================================================

    private void processData(byte[] data) {

        if (data == null || data.length < 8) return;

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int header = buffer.getShort() & 0xFFFF;
        int packetId = buffer.getShort() & 0xFFFF;
        long timestamp = buffer.getInt() & 0xFFFFFFFFL;

        int samplesCount = (data.length - 8) / 2; // cada amostra 2 bytes
        int[] sample = new int[samplesCount];

        for (int i = 0; i < samplesCount; i++) {
            sample[i] = buffer.getShort();
        }

        // envia cada amostra individualmente para o listener
        if (listener != null) {
            for (int s : sample) {
                listener.onECGDataReceived(new int[]{s});
            }
        }
    }

    // ======================================================
    // CONFIGURAÇÃO BUFFER
    // ======================================================

    public void enableBuffering(boolean enable) {

        synchronized (bufferLock) {

            bufferingEnabled = enable;
        }
    }

    // ======================================================
    // FINALIZA BLE
    // ======================================================

    @SuppressLint("MissingPermission")
    public void shutdown() {

        try {

            if (bluetoothGatt != null) {

                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }

        } catch (Exception ignored) {}

        bluetoothGatt = null;

        isConnected = false;
        isConnecting = false;
    }
}