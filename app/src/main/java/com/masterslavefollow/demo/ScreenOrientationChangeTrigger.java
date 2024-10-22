package com.masterslavefollow.demo;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.WindowManager;


public class ScreenOrientationChangeTrigger {
    private static final String TAG = ScreenOrientationChangeTrigger.class.getSimpleName();

    public static void registerReceiver() {
        // 注册屏幕旋转方向监听器
        Utils.getContext().registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String action = intent.getAction();
                // 屏幕旋转
                if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                    int rotation = getRotation();
                }
            }
        }
        }, new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));

        int rotation = getRotation();
    }

    public static int getRotation() {
        WindowManager wm = (WindowManager) Utils.getContext().getSystemService(Context.WINDOW_SERVICE);
        int rotation = wm.getDefaultDisplay().getRotation();
        Log.i(TAG, "rotation:" + rotation);
        return rotation;
    }
}