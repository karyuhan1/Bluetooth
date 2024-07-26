package com.example.test;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.test.helper.DatabaseHelper;
import com.example.test.helper.NavigationService;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class WorkActivity extends AppCompatActivity {

    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    private BluetoothGatt bluetoothGatt;
    private DatabaseHelper dbHelper;
    private String deviceMac; // device_mac를 저장할 변수
    private Handler handler;
    private Runnable runnable;
    private boolean isRecording = false;
    private String selectedGender; // 선택된 성별을 저장할 변수

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_work);

        dbHelper = new DatabaseHelper(this);

        try {
            dbHelper.createDatabase();
            dbHelper.openDatabase();
        } catch (IOException e) {
            throw new RuntimeException("Error creating database", e);
        }

        Intent intent = getIntent();
        deviceMac = intent.getStringExtra("device_address");

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceMac);

        connectToDevice(device);

        Spinner genderSpinner = findViewById(R.id.gender_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.gender_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(adapter);

        Button startButton = findViewById(R.id.start_button);
        Button resetButton = findViewById(R.id.reset_button);

        startButton.setOnClickListener(v -> {
            selectedGender = genderSpinner.getSelectedItem().toString();
            if (!isRecording) {
                startRecording();
                startButton.setText(getString(R.string.stop));
            } else {
                stopRecording();
                startButton.setText(getString(R.string.start));
            }

            // 학습 데이터 개수 확인 및 RunningActivity로 이동
            if (getSensingDataCount() > 6000) {
                Intent runningIntent = new Intent(WorkActivity.this, RunningActivity.class);
                runningIntent.putExtra("device_address", deviceMac);
                runningIntent.putExtra("selected_gender", selectedGender);
                startActivity(runningIntent);
            }
        });

        resetButton.setOnClickListener(v -> resetSensingData());
    }

    private void startRecording() {
        isRecording = true;
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    readCharacteristicData();
                    handler.postDelayed(this, 50); // 50ms 간격으로 실행 (초당 20번)
                }
            }
        };
        handler.post(runnable);
    }

    private void stopRecording() {
        isRecording = false;
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    private void resetSensingData() {
        dbHelper.resetSensingData();
        Toast.makeText(this, "Sensing data reset", Toast.LENGTH_SHORT).show();
    }

    private int getSensingDataCount() {
        Cursor cursor = dbHelper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tb_sensing", null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    private void readCharacteristicData() {
        try {
            if (bluetoothGatt != null) {
                BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (characteristic != null) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            bluetoothGatt.readCharacteristic(characteristic);
                        } else {
                            throw new SecurityException("Bluetooth connect permission not granted");
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            NavigationService.navigateToMainActivity(this);
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            runOnUiThread(() -> Toast.makeText(WorkActivity.this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show());
                            gatt.discoverServices();
                        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                            runOnUiThread(() -> Toast.makeText(WorkActivity.this, "Disconnected from " + device.getName(), Toast.LENGTH_SHORT).show());
                            gatt.close();
                        }
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            BluetoothGattService service = gatt.getService(SERVICE_UUID);
                            if (service != null) {
                                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                                if (characteristic != null) {
                                    readCharacteristic(gatt, characteristic);
                                }
                            }
                        }
                    }

                    private void readCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                        if (gatt != null && characteristic != null) {
                            if (ContextCompat.checkSelfPermission(WorkActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                gatt.readCharacteristic(characteristic);
                            } else {
                                throw new SecurityException("Bluetooth connect permission not granted");
                            }
                        }
                    }

                    @Override
                    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            byte[] data = characteristic.getValue();
                            int middleFlexSensor = (data[0] << 8) | (data[1] & 0xFF);
                            int middlePressureSensor = (data[2] << 8) | (data[3] & 0xFF);
                            int ringFlexSensor = (data[4] << 8) | (data[5] & 0xFF);
                            int ringPressureSensor = (data[6] << 8) | (data[7] & 0xFF);
                            int pinkyFlexSensor = (data[8] << 8) | (data[9] & 0xFF);
                            int acceleration = (data[10] << 8) | (data[11] & 0xFF);
                            int gyroscope = (data[12] << 8) | (data[13] & 0xFF);
                            int magneticField = (data[14] << 8) | (data[15] & 0xFF);
                            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                            dbHelper.insertSensingData(deviceMac, selectedGender, middleFlexSensor, middlePressureSensor, ringFlexSensor,
                                    ringPressureSensor, pinkyFlexSensor, acceleration, gyroscope, magneticField, timestamp);

                            if (getSensingDataCount() > 6000) {
                                Intent runningIntent = new Intent(WorkActivity.this, RunningActivity.class);
                                runningIntent.putExtra("device_address", deviceMac);
                                runningIntent.putExtra("selected_gender", selectedGender);
                                startActivity(runningIntent);
                            }
                        }
                    }
                });
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
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (SecurityException e) {
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
        dbHelper.close();
    }
}
