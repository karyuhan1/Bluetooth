package com.example.test;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.test.helper.NavigationService;
import com.example.test.helper.PermissionHelper;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;

    // 블루투스 활성화 확인
    private final ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    checkPermissionsAndNavigate(); // 블루투스 활성화 성공 시 권한 확인 및 스캔 액티비티로 이동
                } else {
                    Toast.makeText(this, "블루투스가 활성화되지 않았습니다.", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // 시작 버튼 클릭 리스너 설정
        Button startButton = findViewById(R.id.start_btn);
        startButton.setOnClickListener(v -> handleStartButtonClick());
    }

    private void handleStartButtonClick() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "해당 기기는 블루투스를 지원하지 않습니다.", Toast.LENGTH_SHORT).show();
        } else {
            if (!bluetoothAdapter.isEnabled()) {
                // 블루투스가 비활성화된 경우, 블루투스 활성화 요청
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                enableBluetoothLauncher.launch(enableBtIntent);
            } else {
                // 블루투스가 활성화된 경우, 권한 확인 및 스캔 액티비티로 이동
                checkPermissionsAndNavigate();
            }
        }
    }

    private void checkPermissionsAndNavigate() {
        PermissionHelper.checkAndRequestPermissions(this, new PermissionHelper.PermissionCallback() {
            @Override
            public void onPermissionsGranted() {
                // 권한이 허용된 경우 스캔 액티비티로 이동
                NavigationService.navigateToScanActivity(MainActivity.this);
            }

            @Override
            public void onPermissionsDenied() {
                // 권한이 거부된 경우 토스트 메시지 표시
                Toast.makeText(MainActivity.this, "블루투스 관련 권한이 없습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionHelper.REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                // 모든 권한이 허용된 경우 스캔 액티비티로 이동
                NavigationService.navigateToScanActivity(MainActivity.this);
            } else {
                // 권한이 거부된 경우 토스트 메시지 표시
                Toast.makeText(this, "블루투스 관련 권한이 없습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}



