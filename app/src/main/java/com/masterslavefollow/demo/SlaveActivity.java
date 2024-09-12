package com.masterslavefollow.demo;

import android.animation.ObjectAnimator;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public class SlaveActivity extends AppCompatActivity {
    private static String TAG = "SlaveActivity";
    private String mWlanAddress;
    private String mPackageName;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "MainActivity onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_slave);

        findViewById(R.id.connect_animator).setVisibility(View.GONE);
        findViewById(R.id.connect_success_layout).setVisibility(View.GONE);

        mWlanAddress = PrivatePreferences.getString("host_address", "");
        EditText wlanAddress = findViewById(R.id.wlan_address);
        wlanAddress.setText(mWlanAddress);

        findViewById(R.id.connect_button).setOnClickListener((view) -> {
            mWlanAddress = wlanAddress.getText().toString();
            if (mWlanAddress != null && !mWlanAddress.isEmpty()) {
                view.setEnabled(false);

                PrivatePreferences.putString("host_address", mWlanAddress);

                Utils.hideKeyboard(view);

                Thread thread = new AppChanelThread();
                thread.start();
            } else {
                Utils.toast(SlaveActivity.this, "请输入正确的WLAN地址");
            }
        });
    }

    private void success(String packageName) {
        findViewById(R.id.wlan_address_layout).setVisibility(View.GONE);
        findViewById(R.id.connect_animator).setVisibility(View.VISIBLE);
        findViewById(R.id.connect_success_layout).setVisibility(View.VISIBLE);

        try {
            ApplicationInfo app = getPackageManager().getApplicationInfo(packageName, 0);

            ImageView appIcon = findViewById(R.id.app_icon);
            appIcon.setBackground(app.loadIcon(getPackageManager()));
            TextView appLabel = findViewById(R.id.app_label);
            appLabel.setText(app.loadLabel(getPackageManager()));
            TextView appPkgName = findViewById(R.id.app_pakagename);
            appPkgName.setText(app.packageName);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(findViewById(R.id.connect_animator), "rotation", 0, 360);
        objectAnimator.setDuration(10000);
        objectAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        objectAnimator.start();

        Utils.startJar(mWlanAddress);
    }

    private class AppChanelThread extends Thread {
        public AppChanelThread() {
            super("AppChanelThread");
            Log.i(TAG, "AppChanelThread");
        }

        @Override
        public void run() {
            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(mWlanAddress, Utils.APP_CHANNEL_PORT), 3000);

                Log.i(TAG, "AppChanel connect success");

                byte[] eventBuffer = new byte[0];
                byte[] lengthBuffer = new byte[4];
                int len = 0;

                ByteBuffer byteBuffer = ByteBuffer.allocate(10);

                InputStream inputStream = socket.getInputStream();
                while (!Thread.currentThread().isInterrupted()) {
                    recv(inputStream, lengthBuffer, lengthBuffer.length);

                    len = byte4ToInt(lengthBuffer);
                    if (eventBuffer.length < len) {
                        System.out.println("eventBuffer.length:" + eventBuffer.length + " < len:" + len);
                        eventBuffer = new byte[len];
                    }
                    recv(inputStream, eventBuffer, len);

                    String line = new String(eventBuffer, 0, len);

                    System.out.println("event event1:" + line);

                    if (line.startsWith("MASTER_CONFIG")) {
                        // MASTER_CONFIG KEYBOARD:true;NAVIGATION:true;APP:com.autosdk.demo
                        String[] splited = line.substring("MASTER_CONFIG".length() + 1).split(";");

                        for (String s : splited) {
                            String[] kv = s.split(":");
                            if (kv.length == 2) {
                                System.out.println("kv[0]:" + kv[0] + " kv[1]:" + kv[1]);

                                if (kv[0].equals("APP")) {
                                    try {
                                        ApplicationInfo app =  getPackageManager().getApplicationInfo(kv[1], 0);
                                        if (app != null) {
                                            mPackageName = app.packageName;
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }

                        String result = mPackageName != null ? "OK" : "FAIL";
                        len = result.length();
                        byteBuffer.putInt(len);
                        byteBuffer.put(result.getBytes("UTF-8"), 0, len);
                        byteBuffer.flip();

                        socket.getOutputStream().write(byteBuffer.array(), 0, 4 + len);
                        socket.getOutputStream().flush();

                        SlaveActivity.this.runOnUiThread(() -> {
                            if (mPackageName != null) {
                                success(mPackageName);
                            } else {
                                Utils.toast(SlaveActivity.this, "未找到应用");
                            }
                        });
                    } else if (line.startsWith("APP_START")) {
                        SlaveActivity.this.runOnUiThread(() -> {
                            Utils.startTargetApp(mPackageName);
                        });
                    }
                }
            } catch (Exception e) {
                System.out.println("socket exception:" + e);
            } finally {
                try {
                    if (socket != null) {
                        socket.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void recv(InputStream inputStream, byte[] buffer, int sum) throws Exception {
            int read = 0;
            while (sum - read > 0) {
                int len = inputStream.read(buffer, read, sum - read);
                if (len == -1) {
                    throw new RuntimeException("socket closed");
                }
                read += len;
            }
        }

        public int byte4ToInt(byte[] bytes) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            int b2 = bytes[2] & 0xFF;
            int b3 = bytes[3] & 0xFF;
            return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
        }
    }
}
