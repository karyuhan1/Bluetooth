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
    public static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;

    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied();
    }

    public static void checkAndRequestPermissions(Activity activity, PermissionCallback callback) {
        String[] permissions = getPermissionsBasedOnSDK();
        List<String> permissionsNeeded = getUngrantedPermissions(activity, permissions);

        if (permissionsNeeded.isEmpty()) {
            callback.onPermissionsGranted();
        } else {
            ActivityCompat.requestPermissions(activity, permissionsNeeded.toArray(new String[0]), REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    private static String[] getPermissionsBasedOnSDK() {
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

    private static List<String> getUngrantedPermissions(Activity activity, String[] permissions) {
        List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }
        return permissionsNeeded;
    }

    public static boolean hasBluetoothScanPermission(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return true;  // SDK 31 이하에서는 BLUETOOTH_SCAN 권한이 필요하지 않음
    }

    public static boolean hasBluetoothConnectPermission(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;  // SDK 31 이하에서는 BLUETOOTH_CONNECT 권한이 필요하지 않음
    }
}


