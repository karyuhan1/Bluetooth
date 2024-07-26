package com.example.test.helper;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionHelper {
    public static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied();
    }

    // 필요한 모든 권한을 확인하고 요청하는 메서드
    public static void checkAndRequestPermissions(Activity activity, PermissionCallback callback) {
        String[] permissions = getRequiredPermissions();
        List<String> permissionsNeeded = getUngrantedPermissions(activity, permissions);

        if (permissionsNeeded.isEmpty()) {
            callback.onPermissionsGranted();
        } else {
            ActivityCompat.requestPermissions(activity, permissionsNeeded.toArray(new String[0]), REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    // SDK 버전에 따라 필요한 권한 반환
    private static String[] getRequiredPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        } else {
            return new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }
    }

    // 부여되지 않은 권한 목록 반환
    private static List<String> getUngrantedPermissions(Activity activity, String[] permissions) {
        List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }
        return permissionsNeeded;
    }

    // 모든 블루투스 관련 권한이 부여되었는지 확인
    public static boolean hasAllBluetoothPermissions(Context context) {
        String[] permissions = getRequiredPermissions();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
