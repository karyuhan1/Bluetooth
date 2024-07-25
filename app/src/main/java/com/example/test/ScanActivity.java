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
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.test.helper.NavigationService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScanActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private List<BluetoothDevice> bluetoothDevices;
    private ArrayAdapter<String> devicesAdapter;
    // 통신에 사용할 아두이노 UUID 설정
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        // 블루투스 어댑터 초기화
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothDevices = new ArrayList<>();
        devicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        // 스캔한 블루투스 기기 보여줄 ListView 설정
        ListView devicesListView = findViewById(R.id.devices_list_view);
        devicesListView.setAdapter(devicesAdapter);

        // ListView 항목 클릭 시 장치에 연결
        devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = bluetoothDevices.get(position);
            connectToDevice(device);
        });

        // 블루투스 활성화 여부 확인 후 스캔 시작
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            startScanning();
        } else {
            Toast.makeText(this, "Bluetooth is not enabled or not supported", Toast.LENGTH_SHORT).show();
        }
    }

    private void startScanning() {
        Toast.makeText(this, "Starting Bluetooth scanning...", Toast.LENGTH_SHORT).show();
        try {
            // 블루투스 스캔 권한 확인 및 설정된 UUID와 일치하는 기기 검색
            if (checkPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                ScanFilter filter = new ScanFilter.Builder()
                        .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                        .build();
                ScanSettings settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                // 스캔 시작
                bluetoothAdapter.getBluetoothLeScanner().startScan(List.of(filter), settings, scanCallback);
            } else {
                throw new SecurityException("Bluetooth scan permission not granted");
            }
        } catch (SecurityException e) {
            NavigationService.navigateToMainActivity(this);
        }
    }

    // 블루투스 스캔 콜백
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            try {
                // 블루투스 연결 권한 확인
                if (checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    if (!bluetoothDevices.contains(device)) {
                        bluetoothDevices.add(device);
                        devicesAdapter.add(device.getName() + "\n" + device.getAddress());
                        devicesAdapter.notifyDataSetChanged();
                    }
                } else {
                    throw new SecurityException("블루투스 관련 권한이 없습니다");
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
                    // 블루투스 연결 권한 확인 후 리스트에 스캔한 디바이스 기기 정보 저장
                    if (checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                        if (!bluetoothDevices.contains(device)) {
                            bluetoothDevices.add(device);
                            devicesAdapter.add(device.getName() + "\n" + device.getAddress());
                            devicesAdapter.notifyDataSetChanged();
                        }
                    } else {
                        throw new SecurityException("블루투스 관련 권한이 없습니다");
                    }
                } catch (SecurityException e) {
                    //메인으로 다시 가서 권한 요청 하기
                    NavigationService.navigateToMainActivity(ScanActivity.this);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Toast.makeText(ScanActivity.this, "스캔 과정중 오류가 발생했습니다.\n 어플을 종료합니다.", Toast.LENGTH_SHORT).show();
            finishAndRemoveTask();
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    };
    //디바이스 연결 시도
    private void connectToDevice(BluetoothDevice device) {
        try {
            if (checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                Toast.makeText(this, "연결 시도중 :  " + device.getName(), Toast.LENGTH_SHORT).show();

                device.connectGatt(this, false, new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                        //기기 연결에 성공하면 해당 정보를 워킹 액티비티에 전달 후 시작
                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            runOnUiThread(() -> {
                                Intent intent = new Intent(ScanActivity.this, WorkActivity.class);
                                intent.putExtra("device_name", device.getName());
                                intent.putExtra("device_address", device.getAddress());
                                startActivity(intent);
                                finish();
                            });
                        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                            runOnUiThread(() -> Toast.makeText(ScanActivity.this, "기기 연결에 실패했습니다." + device.getName(), Toast.LENGTH_SHORT).show());
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
            if (checkPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
            } else {
                throw new SecurityException("Bluetooth scan permission not granted");
            }
        } catch (SecurityException e) {
            // 권한이 없으면 스캔을 멈출 수 없음, 무시
        }
    }

    // 권한 확인 메서드
    private boolean checkPermission(String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            Toast.makeText(this, "Permission " + permission + " not granted", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
