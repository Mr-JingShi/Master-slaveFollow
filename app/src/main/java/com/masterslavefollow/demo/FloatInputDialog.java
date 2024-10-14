package com.masterslavefollow.demo;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

// 部分逻辑参考自：
// https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/display/OverlayDisplayWindow.java

final class FloatInputDialog {
    private static final String TAG = "FloatInputDialog";
    private View mFloatContent;
    private final MainActivity mMainActivity;
    private float x0 = 0.0f;
    private float y0 = 0.0f;

    public FloatInputDialog(MainActivity activity) {
        mMainActivity = activity;

        LayoutInflater inflater = LayoutInflater.from(mMainActivity);
        mFloatContent = inflater.inflate(R.layout.float_dialog_input, null);

        WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams();
        windowParams.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        windowParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                // | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        windowParams.format = PixelFormat.RGBA_8888;
        windowParams.alpha = 1.0f;
        windowParams.gravity = Gravity.TOP | Gravity.LEFT;

        windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

        WindowManager windowManager = (WindowManager)Utils.getContext().getSystemService(Context.WINDOW_SERVICE);
        windowManager.addView(mFloatContent, windowParams);

        mFloatContent.findViewById(R.id.pair_button).setOnClickListener((view) -> {
            EditText input_port = mFloatContent.findViewById(R.id.input_port);
            String portNumberString = input_port.getText().toString();

            EditText input_pair = mFloatContent.findViewById(R.id.input_pair);
            String pairCodeString = input_pair.getText().toString();

            if (portNumberString != null
                && !portNumberString.isEmpty()
                && TextUtils.isDigitsOnly(portNumberString)
                && pairCodeString != null
                && !pairCodeString.isEmpty()) {
                view.setEnabled(false);
                mFloatContent.findViewById(R.id.cancel_button).setEnabled(false);

                int port = Integer.parseInt(portNumberString);

                Log.i(TAG, "port:" + port + " pairCodeString:" + pairCodeString);

                if (AdbShell.getInstance().pair(port, pairCodeString)) {
                    hide("ADB连接成功");
                } else {
                    hide("ADB连接失败，请输入正确的端口号");
                }
            } else {
                hide("请输入正确的端口号");
            }
        });

        mFloatContent.findViewById(R.id.cancel_button).setOnClickListener((view) -> {
            hide(null);
        });

        final int widthPixels = mMainActivity.getResources().getDisplayMetrics().widthPixels;
        final int heightPixels = mMainActivity.getResources().getDisplayMetrics().heightPixels;

        mFloatContent.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                x0 = event.getRawX();
                y0 = event.getRawY();
            } else if (action == MotionEvent.ACTION_MOVE) {
                float x = windowParams.x + event.getRawX() - x0;
                float y = windowParams.y + event.getRawY() - y0;

                x0 = event.getRawX();
                y0 = event.getRawY();


                x = Math.max(0, Math.min(x, widthPixels - mFloatContent.getWidth()));
                y = Math.max(0, Math.min(y, heightPixels - mFloatContent.getHeight()));

                // Log.i(TAG, "x:" + x + " y:" + y);

                windowParams.x = (int)x;
                windowParams.y = (int)y;

                windowManager.updateViewLayout(mFloatContent, windowParams);
            }
            return true;
        });

        windowParams.x = 20;
        windowParams.y = 100;

        windowManager.updateViewLayout(mFloatContent, windowParams);
    }

    public void show() {
        EditText input_port = mFloatContent.findViewById(R.id.input_port);
        input_port.setText("");
        EditText input_pair = mFloatContent.findViewById(R.id.input_pair);
        input_pair.setText("");

        AdbShell.getInstance().getPairingPort(() -> {
            input_port.setText(String.valueOf(AdbShell.getInstance().getPort()));
        });

        mFloatContent.setVisibility(View.VISIBLE);
        mMainActivity.moveTaskToBack(false);
    }
    private void hide(String msg) {
        mFloatContent.setVisibility(View.GONE);

        Intent intent = new Intent(Utils.getContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (msg != null && !msg.isEmpty()) {
            intent.putExtra("toast", msg);
        }
        mMainActivity.startActivity(intent);
    }
}