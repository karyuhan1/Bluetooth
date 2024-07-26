package com.example.test;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.test.helper.DatabaseHelper;
import com.example.test.helper.NavigationService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScanActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private List<BluetoothDevice> bluetoothDevices;
    private ArrayAdapter<String> devicesAdapter;
    private DatabaseHelper dbHelper;

    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        dbHelper = new DatabaseHelper(this);
        try {
            dbHelper.createDatabase();
            dbHelper.openDatabase();
        } catch (IOException e) {
            throw new RuntimeException("Error creating database", e);
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothDevices = new ArrayList<>();
        devicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        ListView devicesListView = findViewById(R.id.devices_list_view);
        devicesListView.setAdapter(devicesAdapter);

        devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = bluetoothDevices.get(position);
            connectToDevice(device);
        });

        Button refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> {
            devicesAdapter.clear();
            bluetoothDevices.clear();
            startScanning();
        });

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            startScanning();
        } else {
            Toast.makeText(this, "Bluetooth is not enabled or not supported", Toast.LENGTH_SHORT).show();
        }
    }

    private void startScanning() {
        Toast.makeText(this, "Starting Bluetooth scanning...", Toast.LENGTH_SHORT).show();
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                ScanFilter filter = new ScanFilter.Builder()
                        .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                        .build();
                ScanSettings settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                bluetoothAdapter.getBluetoothLeScanner().startScan(List.of(filter), settings, scanCallback);
            } else {
                throw new SecurityException("Bluetooth scan permission not granted");
            }
        } catch (SecurityException e) {
            NavigationService.navigateToMainActivity(this);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            try {
                if (ContextCompat.checkSelfPermission(ScanActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    if (!bluetoothDevices.contains(device)) {
                        bluetoothDevices.add(device);
                        devicesAdapter.add(device.getName() + "\n" + device.getAddress());
                        devicesAdapter.notifyDataSetChanged();
                    }
                } else {
                    throw new SecurityException("Bluetooth connect permission not granted");
                }
            } catch (SecurityException e) {
                NavigationService.navigateToMainActivity(ScanActivity.this);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) {
                BluetoothDevice device = result.getDevice();
                try {
                    if (ContextCompat.checkSelfPermission(ScanActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        if (!bluetoothDevices.contains(device)) {
                            bluetoothDevices.add(device);
                            devicesAdapter.add(device.getName() + "\n" + device.getAddress());
                            devicesAdapter.notifyDataSetChanged();
                        }
                    } else {
                        throw new SecurityException("Bluetooth connect permission not granted");
                    }
                } catch (SecurityException e) {
                    NavigationService.navigateToMainActivity(ScanActivity.this);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Toast.makeText(ScanActivity.this, "Scan failed with error: " + errorCode, Toast.LENGTH_SHORT).show();
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Connecting to " + device.getName(), Toast.LENGTH_SHORT).show();

                // Check if the device is already in tb_deep_learning
                if (dbHelper.isDeviceInDeepLearning(device.getAddress())) {
                    Intent intent = new Intent(ScanActivity.this, ActualActivity.class);
                    intent.putExtra("device_address", device.getAddress());
                    startActivity(intent);
                    finish();
                } else {
                    device.connectGatt(this, false, new BluetoothGattCallback() {
                        @Override
                        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                            if (newState == BluetoothGatt.STATE_CONNECTED) {
                                runOnUiThread(() -> {
                                    Intent intent = new Intent(ScanActivity.this, WorkActivity.class);
                                    intent.putExtra("device_address", device.getAddress());
                                    startActivity(intent);
                                    finish();
                                });
                            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                                runOnUiThread(() -> Toast.makeText(ScanActivity.this, "Failed to connect to " + device.getName(), Toast.LENGTH_SHORT).show());
                            }
                        }

                        @Override
                        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                                if (service != null) {
                                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                                    if (characteristic != null) {
                                        // 이곳에서 아두이노와의 통신을 위한 설정을 수행할 수 있습니다.
                                    }
                                }
                            }
                        }
                    });
                }
            } else {
                throw new SecurityException("Bluetooth connect permission not granted");
            }
        } catch (SecurityException e) {
            NavigationService.navigateToMainActivity(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
            } else {
                throw new SecurityException("Bluetooth scan permission not granted");
            }
        } catch (SecurityException e) {
            // 권한이 없으면 스캔을 멈출 수 없음, 무시
        }
    }
}
