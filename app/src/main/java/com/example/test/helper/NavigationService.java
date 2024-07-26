package com.example.test.helper;

import android.app.Activity;
import android.content.Intent;

import com.example.test.MainActivity;
import com.example.test.ScanActivity;
import com.example.test.WorkActivity;

public class NavigationService {

    public static void navigateToMainActivity(Activity activity) {
        Intent intent = new Intent(activity, MainActivity.class);
        activity.startActivity(intent);
        activity.finish();
    }

    public static void navigateToWorkActivity(Activity activity) {
        Intent intent = new Intent(activity, WorkActivity.class);
        activity.startActivity(intent);
        activity.finish();
    }

    public static void navigateToScanActivity(Activity activity) {
        Intent intent = new Intent(activity, ScanActivity.class);
        activity.startActivity(intent);
    }
}
