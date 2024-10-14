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

    public static void registerReceiver(Activity activity) {
        // 注册屏幕旋转方向监听器
        activity.registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent == null) {
                            return;
                        }

                        Log.i(TAG, "Intent extras: " + intent.getExtras());
                        Log.i(TAG, "Intent data: " + intent.getData());
                        Log.i(TAG, "Intent scheme: " + intent.getScheme());
                    }
                 },
            new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));

        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        int rotation = wm.getDefaultDisplay().getRotation();

        Log.i(TAG, "rotation:" + rotation);
    }
}