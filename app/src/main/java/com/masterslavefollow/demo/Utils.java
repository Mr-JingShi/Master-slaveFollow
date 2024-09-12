package com.masterslavefollow.demo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Utils {
    private static String TAG = "MasterActivity";

    public static int APP_CHANNEL_PORT = 8404;
    public static int JAR_CHANNEL_PORT = 8405;
    private static final BlockingQueue<String> mBlockingQueue = new ArrayBlockingQueue<>(128);
    private static final Handler mHandler = new Handler(Looper.getMainLooper());
    private static String mTargetAppPackageName;
    private static int mNavigationBarHeight;
    public static BlockingQueue<String> getBlockingQueue() {
        return mBlockingQueue;
    }

    public static void setTargetAppPackageName(String targetAppPackageName) {
        mTargetAppPackageName = targetAppPackageName;
    }

    public static String getTargetAppPackageName() {
        return mTargetAppPackageName;
    }

    public static void setNavigationBarHeight(int navigationBarHeight) {
        mNavigationBarHeight = navigationBarHeight;

    }
    public static int getNavigationBarHeight() {
        return mNavigationBarHeight;
    }

    public static String getWlanAddress() {
        String cmd = "ifconfig | grep 'inet ' | grep -v 127.0.0.1 | awk '{print $2}'";
        Process process = null;
        BufferedReader reader = null;
        try {
            process = Runtime.getRuntime().exec(cmd);
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(":")) {
                    return line.split(":")[1].trim();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (process != null) {
                process.destroy();
            }
        }
        return null;
    }

    public static String getHostAddress() {
        InetAddress ip = null;
        try {
            Enumeration<NetworkInterface> en_netInterface = NetworkInterface.getNetworkInterfaces();
            while (en_netInterface.hasMoreElements()) {
                NetworkInterface ni = en_netInterface.nextElement();
                Enumeration<InetAddress> en_ip = ni.getInetAddresses();
                while (en_ip.hasMoreElements()) {
                    ip = en_ip.nextElement();
                    if (!ip.isLoopbackAddress() && !ip.getHostAddress().contains(":")) {
                        break;
                    }
                    else {
                        ip = null;
                    }
                }
                if (ip != null) {
                    break;
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
            Log.e(TAG, "getLocalInetAddress exception:" + e.getMessage(), e);
        }

        if (ip == null) {
            Log.e(TAG, "getLocalInetAddress failed");
            throw new RuntimeException("getLocalInetAddress failed");
        }

        return ip.getHostAddress();
    }

    public static void startTargetApp(final String packageName) {
        //先强制关闭后开启
        new Thread(() -> {
            AdbShell.getInstance().execute("am force-stop " + packageName);
            Log.i(TAG, "强制终止应用");
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setPackage(packageName);
            List<ResolveInfo> resolveInfos = DemoApplication.getApp().getPackageManager().queryIntentActivities(intent, 0);
            if (resolveInfos != null && !resolveInfos.isEmpty()) {
                // 多Launcher情景
                for (ResolveInfo resolveInfo : resolveInfos) {
                    Log.d(TAG, "resolveInfo:" + resolveInfo);
                }
                String targetActivity = resolveInfos.get(0).activityInfo.name;
                Log.d(TAG, "targetActivity:" + targetActivity);
                AdbShell.getInstance().execute("am start -n '" + packageName + "/" + targetActivity + "'");
            }
        }).start();
    }

    public static List<ApplicationInfo> loadApplicationList() {
        // 后台加载下应用列表
        List<ApplicationInfo> listPack = DemoApplication.getApp().getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);

        List<ApplicationInfo> removedItems = new ArrayList<>();

        String selfPackage = DemoApplication.getApp().getPackageName();

        for (ApplicationInfo pack: listPack) {
            if ((pack.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                removedItems.add(pack);
            } else if (Objects.equals(selfPackage, pack.packageName)) {
                removedItems.add(pack);
            }
        }
        listPack.removeAll(removedItems);

        // 排序下
        final Comparator c = Collator.getInstance(Locale.CHINA);
        Collections.sort(listPack, new Comparator<ApplicationInfo>() {
            @Override
            public int compare(ApplicationInfo o1, ApplicationInfo o2) {
                String n1 = o1.loadLabel(DemoApplication.getApp().getPackageManager()).toString();
                String n2 = o2.loadLabel(DemoApplication.getApp().getPackageManager()).toString();
                return c.compare(n1, n2);
            }
        });

        Log.i(TAG, "listPack.size:" + listPack.size());

        return listPack;
    }

    public static void toast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    public static void startJar(String wlanAddress) {
        String cmd = "ps -ef|grep com.masterslavefollow.demo.jar.ScreenEventInjector | grep -v grep | awk '{print $2}' | xargs kill -9";
        System.out.println("cmd:" + cmd);
        AdbShell.getInstance().execute(cmd);

        String jarPath = DemoApplication.getApp().getPackageCodePath();
        System.out.println("jarPath:" + jarPath);

        StringBuilder sb = new StringBuilder();
        sb.append("CLASSPATH=");
        sb.append(jarPath);
        sb.append(" app_process / com.masterslavefollow.demo.jar.ScreenEventInjector");
        sb.append(" ");
        sb.append(wlanAddress);
        sb.append(" ");
        sb.append(JAR_CHANNEL_PORT);
        sb.append(" ");
        sb.append(mNavigationBarHeight);

        cmd = sb.toString();
        System.out.println("cmd:" + cmd);

        AdbShell.getInstance().executeWithBlockingQueue(cmd, null);
    }

    public static void hideKeyboard(View view) {
        view.clearFocus();
        InputMethodManager imm = (InputMethodManager)view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        // 系统键盘显示时才需要关闭
        if (imm != null && imm.isActive()) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void runOnUiThread(Runnable runnable) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            runnable.run();
        } else {
            mHandler.post(runnable);
        }
    }

    public static List<String> getAllFiles(String filePath) {
        List<String> files = new ArrayList<>();

        File file = new File(filePath);

        if (!file.exists() || !file.isDirectory()) {
            return files;
        }

        for (File f : file.listFiles()) {
            if (f.isDirectory()) {
                files.addAll(getAllFiles(f.getAbsolutePath()));
            } else {
                files.add(f.getAbsolutePath());
            }
        }

        return files;
    }

    public static List<String> calculateDeviceSize() {
        List<String> result = new ArrayList<>();
        AdbShell.getInstance().execute("getevent -lp", result);

        List<ScreenEventTracker.DeviceInfo> deviceInfoList = new ArrayList<>();
        String add_device = null;
        String name = null;
        int abs_x = 0;
        int abs_y = 0;
        boolean isX = false;
        for (String str : result) {
            Log.i(TAG, "str:" + str);

            if (str.contains("add device")) {
                String[] devices = str.split(":");
                if (devices == null || devices.length != 2) {
                    continue;
                }
                add_device = devices[1].trim();
                Log.i(TAG, "device:" + add_device);
                continue;
            } else if (str.contains("name:")) {
                String[] names = str.split("\"");
                if (names == null || names.length != 2) {
                    continue;
                }
                name = names[1];
                Log.i(TAG, "name:" + name);
                continue;
            } else if (str.contains("ABS_MT_POSITION_X")) {
                isX = true;
            } else if (str.contains("ABS_MT_POSITION_Y")) {
                isX = false;
            } else {
                continue;
            }

            String[] fields = str.split(",");

            int min = -1;
            int max = -1;

            for (String field : fields) {
                field = field.trim();
                if (field.contains("min")) {
                    String[] paras = field.split(" ");
                    if (paras.length == 2) {
                        min = Integer.parseInt(paras[1]);
                    }
                } else if (field.contains("max")) {
                    String[] paras = field.split(" ");
                    if (paras.length == 2) {
                        max = Integer.parseInt(paras[1]);
                    }
                }
            }

            if (isX) {
                abs_x = max - min + 1;
            } else {
                abs_y = max - min + 1;
            }

            if (add_device != null && name != null && abs_x > 0 && abs_y > 0) {
                Log.i(TAG, "add_device:" + add_device + " name:" + name + " abs_x:" + abs_x + " abs_y:" + abs_y);
                ScreenEventTracker.DeviceInfo deviceInfo = new ScreenEventTracker.DeviceInfo();
                deviceInfo.add_device = add_device;
                deviceInfo.name = name;
                deviceInfo.abs_x = abs_x;
                deviceInfo.abs_y = abs_y;

                deviceInfoList.add(deviceInfo);

                add_device = null;
                name = null;
                abs_x = 0;
                abs_y = 0;
            }
        }

        result.clear();
        AdbShell.getInstance().execute("dumpsys input", result);
        ScreenEventTracker.DeviceInfo deviceInfo = null;
        boolean found = false;
        for (String str : result) {
            Log.i(TAG, "str:" + str);

            if (str.contains("Input Reader State")) {
                found = true;
            } else if (found) {
                // Device 4: himax-touchscreen
                if (str.contains("Device ")) {
                    for (ScreenEventTracker.DeviceInfo info : deviceInfoList) {
                        if (str.endsWith(info.name)) {
                            deviceInfo = info;
                        }
                    }
                } else if (deviceInfo != null && (str.contains("Viewport ") || str.contains("Viewport:"))) {
                    // Viewport INTERNAL: displayId=0, uniqueId=local:4630946861623396481, port=129, orientation=0, logicalFrame=[0, 0, 1200, 1920], physicalFrame=[0, 0, 1200, 1920], deviceSize=[1200, 1920], isActive=[0]
                    int index1 = str.indexOf("displayId=");
                    int index2 = str.indexOf(",", index1 + 10);
                    deviceInfo.display_id = Integer.parseInt(str.substring(index1 + 10, index2));

                    Log.i(TAG, "display_id:" + deviceInfo.display_id);

                    if (deviceInfo.display_id < 0) {
                        deviceInfoList.remove(deviceInfo);
                        deviceInfo = null;
                        continue;
                    }
                    index1 = str.indexOf("orientation=");
                    index2 = str.indexOf(",", index1 + 12);
                    deviceInfo.orientation = Integer.parseInt(str.substring(index1 + 12, index2));
                    Log.i(TAG, "orientation:" + deviceInfo.orientation);

                    index1 = str.indexOf("deviceSize=[");
                    index2 = str.indexOf("]", index1 + 12);
                    String[] paras = str.substring(index1 + 12, index2).split(",");

                    int width = Integer.parseInt(paras[0]);
                    int height = Integer.parseInt(paras[1].trim());
                    if (deviceInfo.orientation == 0) {
                        deviceInfo.width = width;
                        deviceInfo.height = height;
                    } else if (deviceInfo.orientation == 1) {
                        deviceInfo.width = height;
                        deviceInfo.height = width;
                    }

                    Log.i(TAG, "width:" + deviceInfo.width + " height:" + deviceInfo.height);

                    if (deviceInfo.width <= 0 || deviceInfo.height <= 0) {
                        deviceInfoList.remove(deviceInfo);
                        deviceInfo = null;
                        continue;
                    }

                    deviceInfo = null;
                } else if (str.endsWith("Viewports:")) {
                    break;
                }
            }
        }

        List<String> selfMessage = new ArrayList<>();
        for (ScreenEventTracker.DeviceInfo info : deviceInfoList) {
            if (info.display_id >= 0 && info.width > 0 && info.height > 0) {
                Log.i(TAG, "deviceInfo:" + info.toString());
                selfMessage.add(info.toString());
            }
        }

        return selfMessage;
    }
}