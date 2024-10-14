package com.masterslavefollow.demo;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {
    private static String TAG = "MainActivity";
    private static int REQUEST_CODE = 1001;
    private FloatInputDialog mFloatInputDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "MainActivity onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            findViewById(R.id.adb_pair).setVisibility(View.GONE);
        }

        if (!hasPermission()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE);
        }

        getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
            Log.i(TAG, "onApplyWindowInsetsListener:" + insets);
            Log.i(TAG, "Top:" + insets.getStableInsetTop() + " Left:" + insets.getStableInsetLeft() + " Right:" + insets.getStableInsetRight() + " Bottom:" + insets.getStableInsetBottom());
            int navigationBarHeight = insets.getStableInsetBottom();
            if (navigationBarHeight == 0) {
                navigationBarHeight = insets.getStableInsetLeft();
                if (navigationBarHeight == 0) {
                    navigationBarHeight = insets.getStableInsetRight();
                }
            }
            Log.i(TAG, "navigationBarHeight:" + navigationBarHeight);
            Utils.setNavigationBarHeight(navigationBarHeight);
            return insets;
        });
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "MainActivity onDestroy");
        super.onDestroy();
        AdbShell.getInstance().disconnect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "MainActivity onResume");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            String msg = intent.getStringExtra("toast");
            if (msg != null && !msg.isEmpty()) {
                Utils.toast(MainActivity.this, msg);
            }
        }
    }

    public void master_mode(View view) {
        if (getConnectStatus()) {
            Intent intent = new Intent(MainActivity.this, MasterActivity.class);
            intent.putExtra("isSingleMachineMode", false);
            startActivity(intent);
        }
    }

    public void slave_mode(View view) {
        if (getConnectStatus()) {
            Intent intent = new Intent(MainActivity.this, SlaveActivity.class);
            startActivity(intent);
        }
    }

    public void adb_tcpip(View view) {
        View inputView = LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_input, null);

        EditText input_text = inputView.findViewById(R.id.input_text);
        input_text.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        String portString = getPort();
        if (portString == null || portString.isEmpty()) {
            portString = "5555";
        }
        input_text.setText(portString);

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(inputView);

        builder.setTitle("ADB-TCPIP调试");
        builder.setMessage("请开启ADB-TCPIP调试，在PC端执行：adb tcpip 5555");

        builder.setNegativeButton("取消", null);
        builder.setPositiveButton("连接", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String portNumberString = input_text.getText().toString();

                if (portNumberString != null
                        && !portNumberString.isEmpty()
                        && TextUtils.isDigitsOnly(portNumberString)) {
                    int port = Integer.parseInt(portNumberString);
                    AdbShell.getInstance().connect(port);

                    if (AdbShell.getInstance().getConnectStatus()) {
                        Utils.toast(MainActivity.this, "ADB连接成功");

                        adb_gone();
                    } else {
                        Utils.toast(MainActivity.this, "ADB连接失败，请输入正确的端口号");
                    }
                } else {
                    Utils.toast(MainActivity.this, "请输入正确的端口号");
                }
            }
        });
        builder.show();
    }

    private void adb_gone() {
        findViewById(R.id.adb_conntion).setVisibility(View.GONE);
    }

    private String getPort() {
        String port = null;
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            port = (String)(get.invoke(c, "service.adb.tcp.port", port));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return port;
    }

    private boolean getConnectStatus() {
        if (AdbShell.getInstance().getConnectStatus()) {
            return true;
        }
        Utils.toast(MainActivity.this, "请先连接ADB");
        return false;
    }

    public void adb_pair(View view) {
        if (hasPermission()) {
            startFloatDialog();
        } else {
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE && !hasPermission()) {
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show();
        }
    }

    private void startFloatDialog() {
        Log.i(TAG, "MainActivity startFloatWindow");

        if (mFloatInputDialog == null) {
            mFloatInputDialog = new FloatInputDialog(MainActivity.this);
        }
        mFloatInputDialog.show();

        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private boolean hasPermission() {
        return Build.VERSION.SDK_INT >= 23 ? Settings.canDrawOverlays(this) : true;
    }

    public void single_mode(View view) {
        if (getConnectStatus()) {
            Intent intent = new Intent(MainActivity.this, MasterActivity.class);
            intent.putExtra("isSingleMachineMode", true);
            Log.i(TAG, "isSingleMachineMode");
            startActivity(intent);
        }
    }
}
