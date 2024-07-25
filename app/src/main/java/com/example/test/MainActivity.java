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

import com.example.test.helper.PermissionHelper;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    //블루투스 활성화 확인
    private final ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (PermissionHelper.checkAndRequestPermissions(this)) {
                        goToScanActivity();
                    }
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
        //시작버튼이 눌렸을때 블루투스 권한과 활성화 체크후 결과값에 따라 반환 최종적으로 스캔 액티비티로 이동
        Button startButton = findViewById(R.id.start_btn);
        startButton.setOnClickListener(v -> {
            if (bluetoothAdapter == null) {
                Toast.makeText(this, "해당 기기는 블루투스를 지원하지 않습니다.", Toast.LENGTH_SHORT).show();
            } else {
                if (!bluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    enableBluetoothLauncher.launch(enableBtIntent);
                } else {
                    if (PermissionHelper.checkAndRequestPermissions(this)) {
                        goToScanActivity();
                    }
                }
            }
        });
    }
    //스캔 액티비티로 이동
    private void goToScanActivity() {
        Intent intent = new Intent(this, ScanActivity.class);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //블루투스 권환 확인
        if (requestCode == PermissionHelper.REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                goToScanActivity();
            } else {
                Toast.makeText(this, "블루투스 관련 권한이 없습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
