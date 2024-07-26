package com.example.test;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.test.helper.DatabaseHelper;
import com.example.test.helper.NavigationService;
import com.example.test.helper.PermissionHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScanActivity extends AppCompatActivity {

    // BluetoothAdapter와 관련된 변수 선언
    private BluetoothAdapter bluetoothAdapter;
    private List<BluetoothDevice> bluetoothDevices;
    private ArrayAdapter<String> devicesAdapter;
    private DatabaseHelper dbHelper;
    private BluetoothGatt bluetoothGatt;  // BluetoothGatt 객체 선언

    // UUID 정의
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        // DatabaseHelper 초기화 및 데이터베이스 설정
        dbHelper = new DatabaseHelper(this);
        initializeDatabase();

        // BluetoothAdapter와 관련된 변수 초기화
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothDevices = new ArrayList<>();
        devicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        // ListView 설정
        ListView devicesListView = findViewById(R.id.devices_list_view);
        devicesListView.setAdapter(devicesAdapter);

        // ListView 항목 클릭 리스너 설정
        devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = bluetoothDevices.get(position);
            connectToDevice(device);
        });

        // 새로고침 버튼 클릭 리스너 설정
        Button refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> {
            devicesAdapter.clear();
            bluetoothDevices.clear();
            startScanning();
        });

        // 블루투스가 활성화되어 있는지 확인하고 스캔 시작
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            startScanning();
        } else {
            Toast.makeText(this, "Bluetooth is not enabled or not supported", Toast.LENGTH_SHORT).show();
        }
    }

    // 데이터베이스 초기화
    private void initializeDatabase() {
        try {
            dbHelper.createDatabase();
            dbHelper.openDatabase();
        } catch (IOException e) {
            throw new RuntimeException("Error creating database", e);
        }
    }

    // 블루투스 스캔 시작
    private void startScanning() {
        Toast.makeText(this, "사용가능한 기기 검색중...", Toast.LENGTH_SHORT).show();
        try {
            if (PermissionHelper.hasBluetoothScanPermission(this)) {
                // UUID를 사용하여 스캔 필터 설정
                ScanFilter filter = new ScanFilter.Builder()
                        .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                        .build();
                ScanSettings settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                bluetoothAdapter.getBluetoothLeScanner().startScan(List.of(filter), settings, scanCallback);
            } else {
                throw new SecurityException("Bluetooth scan permission not granted"); // 권한이 없으면 SecurityException을 던짐
            }
        } catch (SecurityException e) {
            NavigationService.navigateToMainActivity(this); // 권한이 없으면 메인 액티비티로 이동
        }
    }

    // 스캔 결과 콜백
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            handleScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                handleScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Toast.makeText(ScanActivity.this, "블루투스 기기검색에 실패했습니다 : " + errorCode, Toast.LENGTH_SHORT).show();
        }
    };

    // 스캔 결과 처리
    private void handleScanResult(ScanResult result) {
        BluetoothDevice device = result.getDevice();
        try {
            if (PermissionHelper.hasBluetoothConnectPermission(this) && !bluetoothDevices.contains(device)) {
                bluetoothDevices.add(device);
                devicesAdapter.add(device.getName() + "\n" + device.getAddress());
                devicesAdapter.notifyDataSetChanged();
            } else {
                throw new SecurityException("Bluetooth connect permission not granted"); // 권한이 없으면 SecurityException을 던짐
            }
        } catch (SecurityException e) {
            NavigationService.navigateToMainActivity(this); // 권한이 없으면 메인 액티비티로 이동
        }
    }

    // 디바이스 연결
    private void connectToDevice(BluetoothDevice device) {
        try {
            if (PermissionHelper.hasBluetoothConnectPermission(this)) {
                Toast.makeText(this, "Connecting to " + device.getName(), Toast.LENGTH_SHORT).show();

                // device_mac을 기준으로 tb_deep_learning 테이블을 조회
                boolean isDeviceInDeepLearning = dbHelper.isDeviceInDeepLearning(device.getAddress());

                // device.connectGatt 호출
                bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                        handleConnectionStateChange(newState, device, isDeviceInDeepLearning);
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            // GATT 서비스 검색이 성공했을 때
                            BluetoothGattService service = gatt.getService(SERVICE_UUID);
                            if (service != null) {
                                // 지정된 UUID를 가진 서비스가 존재할 때
                                // 이곳에서 서비스 검색이 성공했음을 알리는 작업을 수행할 수 있습니다.
                                runOnUiThread(() -> Toast.makeText(ScanActivity.this, "Service discovered for " + gatt.getDevice().getName(), Toast.LENGTH_SHORT).show());
                            }
                        }
                    }
                });
            } else {
                throw new SecurityException("Bluetooth connect permission not granted"); // 권한이 없으면 SecurityException을 던짐
            }
        } catch (SecurityException e) {
            NavigationService.navigateToMainActivity(this); // 권한이 없으면 메인 액티비티로 이동
        }
    }

    // 연결 상태 변경 처리
    private void handleConnectionStateChange(int newState, BluetoothDevice device, boolean isDeviceInDeepLearning) {
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            if (isDeviceInDeepLearning) {
                // device_mac이 tb_deep_learning 테이블에 있는 경우 ActualActivity로 이동
                navigateToActivity(ActualActivity.class, device.getAddress());
            } else {
                // device_mac이 tb_deep_learning 테이블에 없는 경우 WorkActivity로 이동
                navigateToActivity(WorkActivity.class, device.getAddress());
            }
        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
            try {
                runOnUiThread(() -> Toast.makeText(ScanActivity.this, "Failed to connect to " + device.getName(), Toast.LENGTH_SHORT).show());
            } catch (SecurityException e){
                NavigationService.navigateToMainActivity(this);
            }
        }
    }

    // 다른 액티비티로 이동
    private void navigateToActivity(Class<?> activityClass, String deviceAddress) {
        Intent intent = new Intent(ScanActivity.this, activityClass);
        intent.putExtra("device_address", deviceAddress);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (PermissionHelper.hasBluetoothScanPermission(this)) {
                bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
            } else {
                throw new SecurityException("Bluetooth scan permission not granted"); // 권한이 없으면 SecurityException을 던짐
            }
        } catch (SecurityException e) {
            NavigationService.navigateToMainActivity(this); // 권한이 없으면 메인 액티비티로 이동
        }

        // bluetoothGatt 객체가 null이 아닌 경우 close() 메서드 호출
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
                bluetoothGatt = null;
            } catch (SecurityException e){
                bluetoothGatt = null;
            }
        }
    }
}
