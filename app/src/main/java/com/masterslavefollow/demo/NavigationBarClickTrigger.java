package com.masterslavefollow.demo;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.WindowManager;


public class NavigationBarClickTrigger {
    private static final String TAG = ScreenOrientationChangeTrigger.class.getSimpleName();

    private static final String SYSTEM_DIALOG_REASON_KEY = "reason";
    private static final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
    private static final String SYSTEM_DIALOG_REASON_RECENTAPPS_KEY = "recentapps";

    public static void registerReceiver(Activity activity) {
        activity.registerReceiver(new BroadcastReceiver () {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);

                        Log.i(TAG, "reason:" + reason);

                        if (reason.equals(SYSTEM_DIALOG_REASON_HOME_KEY)) {

                        } else if (reason.equals(SYSTEM_DIALOG_REASON_RECENTAPPS_KEY)) {

                        }
                    }
                },
            new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }
}