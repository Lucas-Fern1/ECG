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
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;

public class BLEManager {
    private static final String TAG = "BLEManager";

    // UUIDs ECG (ajuste conforme seu dispositivo)
    private static final UUID SERVICE_UUID = UUID.fromString("4FAF0101-FBCF-4309-8A1C-8472B7098485");
    private static final UUID CHARACTERISTIC_DATA_UUID = UUID.fromString("AEB5483E-36E1-4688-B7F5-EA07361B26A9");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String ECG_BLE_NAME = "ECG_SIMULATOR";

    private final AppCompatActivity activity;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeScanner bluetoothScanner;
    private BluetoothGatt bluetoothGatt;

    private boolean isScanning = false;
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private boolean cccdWriteIssued = false;
    private boolean notificationsReady = false;

    private final Queue<byte[]> dataQueue = new LinkedList<>();
    private final Object bufferLock = new Object();
    private boolean isBufferingEnabled = true;

    public interface DataListener {
        void onECGDataReceived(int[] sample);
    }

    private DataListener listener;

    // ======== Construtor ========
    @SuppressLint("MissingPermission")
    public BLEManager(@NonNull Context ctx, DataListener listener) {
        this.context = ctx;
        this.listener = listener;

        if (!(ctx instanceof AppCompatActivity)) {
            throw new IllegalArgumentException("BLEManager requer AppCompatActivity");
        }
        this.activity = (AppCompatActivity) ctx;

        BluetoothManager manager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager != null ? manager.getAdapter() : null;
        bluetoothScanner = bluetoothAdapter != null ? bluetoothAdapter.getBluetoothLeScanner() : null;

        requestBlePermissions();
    }

    // ======== PERMISSÕES ========
    private String[] getPermissionsToRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{ Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT };
        } else {
            return new String[]{ Manifest.permission.ACCESS_FINE_LOCATION };
        }
    }

    private boolean checkBlePermissions() {
        for (String perm : getPermissionsToRequest()) {
            if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

    private ActivityResultLauncher<String[]> permissionLauncher;

    private void requestBlePermissions() {
        if (!checkBlePermissions()) {
            permissionLauncher.launch(getPermissionsToRequest());
        } else {
            startScanning();
        }
    }

    // ======== SCAN ========
    @SuppressLint("MissingPermission")
    private void startScanning() {
        if (isScanning || isConnected || bluetoothScanner == null) return;
        isScanning = true;

        bluetoothScanner.startScan(scanCallback);
        Log.d(TAG, "Iniciando scan BLE...");
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT})
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device.getName() != null && ECG_BLE_NAME.equals(device.getName()) && !isConnecting) {
                bluetoothScanner.stopScan(this);
                isScanning = false;
                connectToDevice(device);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan falhou: " + errorCode);
            isScanning = false;
            handler.postDelayed(BLEManager.this::startScanning, 2000);
        }
    };

    // ======== CONEXÃO ========
    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        if (device == null || isConnecting) return;

        isConnecting = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN})
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                isConnecting = false;
                gatt.discoverServices();
                Log.d(TAG, "Conectado ao GATT.");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                isConnecting = false;
                bluetoothGatt = null;
                handler.postDelayed(BLEManager.this::startScanning, 500);
                Log.d(TAG, "Desconectado do GATT. Reconectando...");
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setCharacteristicNotification(gatt, CHARACTERISTIC_DATA_UUID, true);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_DATA_UUID.equals(characteristic.getUuid())) {
                processData(characteristic.getValue());
            }
        }
    };

    // ======== NOTIFICAÇÕES ========
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void setCharacteristicNotification(BluetoothGatt gatt, UUID uuid, boolean enable) {
        BluetoothGattService service = gatt.getService(SERVICE_UUID);
        if (service == null) return;

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);
        if (characteristic == null) return;

        gatt.setCharacteristicNotification(characteristic, enable);

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
        if (descriptor != null && !cccdWriteIssued) {
            cccdWriteIssued = true;
            descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        }
    }

    // ======== PROCESSAMENTO ========
    private void processData(byte[] data) {
        if (data == null || data.length != 6) return; // exemplo: 3 canais x 2 bytes cada
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int[] sample = new int[3];
        for (int i = 0; i < 3; i++) sample[i] = buffer.getShort() & 0xFFFF;

        synchronized (bufferLock) {
            if (isBufferingEnabled) dataQueue.offer(data);
        }

        if (listener != null) listener.onECGDataReceived(sample);
    }

    // ======== CONFIG ========
    public void enableBuffering(boolean enable) {
        synchronized (bufferLock) { isBufferingEnabled = enable; }
    }

    @SuppressLint("MissingPermission")
    public void shutdown() {
        try { if (bluetoothGatt != null) { bluetoothGatt.disconnect(); bluetoothGatt.close(); } } catch (Exception ignore){}
        bluetoothGatt = null;
        isConnected = false;
        isConnecting = false;
    }
}