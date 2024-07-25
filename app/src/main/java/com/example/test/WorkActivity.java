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
import android.widget.Button;
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

    // 아두이노와의 통신에 사용할 서비스와 특성의 UUID
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    private BluetoothGatt bluetoothGatt;
    private DatabaseHelper dbHelper;
    private int deviceIdx; // device_idx를 저장할 변수
    private int userIdx; // user_idx를 저장할 변수
    private Handler handler;
    private Runnable runnable;
    private boolean isRecording = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_work);

        dbHelper = new DatabaseHelper(this);

        // 데이터베이스 초기화
        try {
            dbHelper.createDatabase();
            dbHelper.openDatabase();
        } catch (IOException e) {
            throw new RuntimeException("Error creating database", e);
        }

        Intent intent = getIntent();
        String deviceAddress = intent.getStringExtra("device_address");

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);

        // (임시) device_idx와 user_idx 설정
        deviceIdx = 1;
        userIdx = 1;

        connectToDevice(device);

        Button startButton = findViewById(R.id.start_button);
        Button resetButton = findViewById(R.id.reset_button);

        // 시작 버튼 클릭 시
        startButton.setOnClickListener(v -> {
            if (!isRecording) {
                startRecording();
                startButton.setText("Stop");
            } else {
                stopRecording();
                startButton.setText("Start");
            }

            // 학습 데이터 개수 확인 및 RunningActivity로 이동
            if (getSensingDataCount() > 6000) {
                Intent runningIntent = new Intent(WorkActivity.this, RunningActivity.class);
                startActivity(runningIntent);
            }
        });

        // 리셋 버튼 클릭 시
        resetButton.setOnClickListener(v -> resetSensingData());
    }

    // 기록 시작
    private void startRecording() {
        isRecording = true;
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    // BLE 데이터를 읽고 데이터베이스에 저장
                    readCharacteristicData();
                    handler.postDelayed(this, 50); // 50ms 간격으로 실행 (초당 20번)
                }
            }
        };
        handler.post(runnable);
    }

    // 기록 중지
    private void stopRecording() {
        isRecording = false;
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    // 센싱 데이터 초기화
    private void resetSensingData() {
        dbHelper.resetSensingData();
        Toast.makeText(this, "Sensing data reset", Toast.LENGTH_SHORT).show();
    }

    // 센싱 데이터 개수 확인
    private int getSensingDataCount() {
        Cursor cursor = dbHelper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tb_sensing", null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    // 특성 데이터 읽기
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

    // 블루투스 장치에 연결 확인
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
                                    // 아두이노 센서 데이터 읽기
                                    readCharacteristic(gatt, characteristic);
                                }
                            }
                        }
                    }

                    // 특성 읽기
                    private void readCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                        if (gatt != null && characteristic != null) {
                            if (ContextCompat.checkSelfPermission(WorkActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                gatt.readCharacteristic(characteristic);
                            } else {
                                throw new SecurityException("블루투스 권한 없음");
                            }
                        }
                    }

                    // 특성 읽기 성공 시
                    @Override
                    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            byte[] data = characteristic.getValue();
                            // 데이터 파싱, 각 센서 데이터를 2바이트라고 가정
                            int middleFlexSensor = (data[0] << 8) | (data[1] & 0xFF);
                            int middlePressureSensor = (data[2] << 8) | (data[3] & 0xFF);
                            int ringFlexSensor = (data[4] << 8) | (data[5] & 0xFF);
                            int ringPressureSensor = (data[6] << 8) | (data[7] & 0xFF);
                            int pinkyFlexSensor = (data[8] << 8) | (data[9] & 0xFF);
                            int acceleration = (data[10] << 8) | (data[11] & 0xFF);
                            int gyroscope = (data[12] << 8) | (data[13] & 0xFF);
                            int magneticField = (data[14] << 8) | (data[15] & 0xFF);
                            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                            // 데이터베이스에 센싱 데이터 저장
                            dbHelper.insertSensingData(deviceIdx, middleFlexSensor, middlePressureSensor, ringFlexSensor,
                                    ringPressureSensor, pinkyFlexSensor, acceleration, gyroscope, magneticField, timestamp, userIdx);

                            // 학습 데이터 개수 확인 및 RunningActivity로 이동
                            if (getSensingDataCount() > 6000) {
                                Intent runningIntent = new Intent(WorkActivity.this, RunningActivity.class);
                                startActivity(runningIntent);
                            }
                        }
                    }
                });
            } else {
                throw new SecurityException("블루투스 권한 없음");
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
