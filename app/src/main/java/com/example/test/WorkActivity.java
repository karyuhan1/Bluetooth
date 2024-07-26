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

    // Bluetooth 서비스와 특성의 UUID 정의
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    // BluetoothGatt 객체 선언
    private BluetoothGatt bluetoothGatt;
    // 데이터베이스 헬퍼 객체 선언
    private DatabaseHelper dbHelper;
    // Bluetooth 장치의 MAC 주소를 저장할 변수
    private String deviceMac;
    // 핸들러 및 Runnable 객체 선언
    private Handler handler;
    private Runnable runnable;
    // 데이터 수집 상태를 나타내는 변수
    private boolean isRecording = false;
    // 선택된 성별을 저장할 변수
    private String selectedGender;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_work);

        // 데이터베이스 초기화 및 설정
        dbHelper = new DatabaseHelper(this);

        try {
            dbHelper.createDatabase();
            dbHelper.openDatabase();
        } catch (IOException e) {
            throw new RuntimeException("Error creating database", e);
        }

        // 인텐트에서 Bluetooth 장치의 MAC 주소를 가져옴
        Intent intent = getIntent();
        deviceMac = intent.getStringExtra("device_address");

        // BluetoothAdapter와 BluetoothDevice 객체 초기화
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceMac);

        // Bluetooth 장치에 연결
        connectToDevice(device);

        // 성별 선택 스피너 설정
        Spinner genderSpinner = findViewById(R.id.gender_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.gender_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(adapter);

        // 시작 버튼과 초기화 버튼 설정
        Button startButton = findViewById(R.id.start_button);
        Button resetButton = findViewById(R.id.reset_button);

        startButton.setOnClickListener(v -> {
            // 성별 선택
            selectedGender = genderSpinner.getSelectedItem().toString();
            if (!isRecording) {
                // 데이터 수집 시작
                startRecording();
                startButton.setText(getString(R.string.stop));
            } else {
                // 데이터 수집 중지
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

    // 데이터 수집 시작
    private void startRecording() {
        isRecording = true;
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    // 센서 데이터 읽기
                    readCharacteristicData();
                    handler.postDelayed(this, 50); // 50ms 간격으로 실행 (초당 20번)
                }
            }
        };
        handler.post(runnable);
    }

    // 데이터 수집 중지
    private void stopRecording() {
        isRecording = false;
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    // 센싱 데이터 초기화
    private void resetSensingData() {
        dbHelper.resetSensingData();
        Toast.makeText(this, "학습데이터 초기화 완료.", Toast.LENGTH_SHORT).show();
    }

    // 센싱 데이터 개수 가져오기
    private int getSensingDataCount() {
        Cursor cursor = dbHelper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tb_sensing", null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    // Bluetooth 장치에 연결
    private void connectToDevice(BluetoothDevice device) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            // 연결 성공 시
                            runOnUiThread(() -> Toast.makeText(WorkActivity.this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show());
                            gatt.discoverServices();
                        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                            // 연결 해제 시
                            runOnUiThread(() -> Toast.makeText(WorkActivity.this, "Disconnected from " + device.getName(), Toast.LENGTH_SHORT).show());
                            gatt.close();
                        }
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            // 서비스 검색 성공 시
                            BluetoothGattService service = gatt.getService(SERVICE_UUID);
                            if (service != null) {
                                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                                if (characteristic != null) {
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
                                throw new SecurityException("Bluetooth connect permission not granted");
                            }
                        }
                    }

                    @Override
                    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            // 특성 읽기 성공 시
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

                            // 센싱 데이터 데이터베이스에 저장
                            dbHelper.insertSensingData(deviceMac, selectedGender, middleFlexSensor, middlePressureSensor, ringFlexSensor,
                                    ringPressureSensor, pinkyFlexSensor, acceleration, gyroscope, magneticField, timestamp);

                            // 센싱 데이터 개수가 6000개 이상인 경우 모델학습을 위해 RunningActivity로 이동
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // BluetoothGatt 객체가 null이 아닌 경우 close() 메서드 호출
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (SecurityException e) {
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
        // 데이터베이스 닫기
        dbHelper.close();
    }
}
