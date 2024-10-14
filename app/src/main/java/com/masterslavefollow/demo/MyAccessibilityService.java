package com.masterslavefollow.demo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class MyAccessibilityService extends AccessibilityService {
    public static final int MODE_BLOCK = 0;
    public static final int MODE_NORMAL = 1;
    public static boolean isSlaveMode = false;

    private static final String TAG = "MyAccessibilityService";

    public static void open(boolean slaveMode) {
        isSlaveMode = slaveMode;
        AdbShell.getInstance().execute("content call --uri content://settings/secure --method PUT_secure --arg enabled_accessibility_services --extra _user:i:0 --extra value:s:com.masterslavefollow.demo/.MyAccessibilityService");
        AdbShell.getInstance().execute("content call --uri content://settings/secure --method PUT_secure --arg accessibility_enabled --extra _user:i:0 --extra value:s:1");
    }

    public static void close() {
        AdbShell.getInstance().execute("content call --uri content://settings/secure --method PUT_secure --arg enabled_accessibility_services --extra _user:i:0 --extra value:s:com.android.talkback/com.google.android.marvin.talkback.TalkBackService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service on create");
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Log.d(TAG, "Service on start");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Service on unbind");

        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "Service on rebind");
        super.onRebind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "Service on task removed");
        super.onTaskRemoved(rootIntent);
    }

    @Override
    protected void onServiceConnected() {
        Log.d(TAG, "Service connected");
        super.onServiceConnected();

        setServiceToNormalMode();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service on start command");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Log.v(TAG, "收到键盘事件:" + event);
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Log.v(TAG, "收到辅助功能事件:" + event);
        if (isSlaveMode) {
            slaveMode(event);
        } else {
            masterMode(event);
        }
    }

    @Override
    protected boolean onGesture(int gestureId) {
        return super.onGesture(gestureId);
    }
    @Override
    public void onInterrupt() {
        Log.e(TAG, "服务被Interrupt");
    }

    public void setAccessonilityMode(int mode) {
        if (mode == MODE_BLOCK) {
            setServiceInfoToTouchBlockMode();
        } else if (mode == MODE_NORMAL) {
            setServiceToNormalMode();
        }
    }

    private AccessibilityServiceInfo _getServiceInfo() {
        AccessibilityServiceInfo serviceInfo = getServiceInfo();
        if (serviceInfo == null) {
            serviceInfo = new AccessibilityServiceInfo();
            serviceInfo.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
            serviceInfo.notificationTimeout = 100;
            serviceInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        }

        return serviceInfo;
    }

    private void setServiceInfoToTouchBlockMode() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            Log.e(TAG, "ServiceInfo为空");
            return;
        }
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY |
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                AccessibilityServiceInfo.DEFAULT |
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;

        Log.d(TAG, "辅助功能进入触摸监控模式");
        setServiceInfo(info);
    }

    private void setServiceToNormalMode() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            Log.e(TAG, "ServiceInfo为空");
            return;
        }
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                AccessibilityServiceInfo.DEFAULT;

        Log.d(TAG, "辅助功能进入正常模式");
        setServiceInfo(info);
    }

    private void masterMode(AccessibilityEvent event) {
        int evetType = event.getEventType();
        // TYPE_WINDOWS_CHANGED 事件
        if (AccessibilityEvent.TYPE_WINDOWS_CHANGED == evetType) {
            List<AccessibilityWindowInfo> windowInfos = this.getWindows();
            if (!windowInfos.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                Rect rect = new Rect();

                String inputMethod = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
                inputMethod = inputMethod.substring(0, inputMethod.lastIndexOf("/"));

                for (int i = 0; i < windowInfos.size(); i++) {
                    AccessibilityWindowInfo win = windowInfos.get(i);
                    AccessibilityNodeInfo root = win.getRoot();
                    Log.i(TAG, "root:" + root);
                    if (root != null) {
                        win.getBoundsInScreen(rect);

                        if (sb.length() > 0) {
                            sb.append(";;");
                        }

                        sb.append("TYPE:");
                        String packageName = root.getPackageName().toString();
                        if (packageName.equals(this.getPackageName())) {
                            sb.append("tool");
                        } else if (packageName.equals(Utils.getTargetAppPackageName())) {
                            sb.append("target");
                        } else if (packageName.equals(inputMethod)) {
                            sb.append("inputmethod");
                        } else if (Utils.isNavigationBar(rect)) {
                            sb.append("navigation");
                        } else {
                            sb.append("other");
                        }
                        sb.append(";LEFT:");
                        sb.append(rect.left);
                        sb.append(";TOP:");
                        sb.append(rect.top);
                        sb.append(";RIGHT:");
                        sb.append(rect.right);
                        sb.append(";BOTTOM:");
                        sb.append(rect.bottom);
                    }
                }
                if (sb.length() > 0) {
                    sb.insert(0, "WINDOWS_CHANGED ");

                    sb.append(";;ROTATION:");
                    WindowManager windowManager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
                    sb.append(windowManager.getDefaultDisplay().getRotation());
                    String str = sb.toString();
                    Log.i(TAG, "WINDOWS_CHANGED:" + str);
                    Utils.getBlockingQueue().offer(str);
                }
            }
        } else if (AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED == evetType) {
            Log.i(TAG, "pkgname:" + event.getPackageName());
            Log.i(TAG, "beforeText:" + event.getBeforeText() + " text:" + event.getText());
            Log.i(TAG, "fromIndex:" + event.getFromIndex() + " toIndex:" + event.getToIndex());
            Log.i(TAG, "adddCount:" + event.getAddedCount() + " removedCount:" + event.getRemovedCount());

            if (event.getPackageName().equals(Utils.getTargetAppPackageName())) {
                StringBuilder sb = new StringBuilder();
                sb.append("EDITVIEW_CHANGED ");
                sb.append("TEXT:");
                sb.append(event.getText());

                String str = sb.toString();
                Log.i(TAG, "EDITVIEW_CHANGED:" + str);
                Utils.getBlockingQueue().offer(str);
            }
        }
    }
    private void slaveMode(AccessibilityEvent event) {
        int evetType = event.getEventType();
        // TYPE_WINDOWS_CHANGED 事件
        if (AccessibilityEvent.TYPE_WINDOWS_CHANGED == evetType) {
            List<AccessibilityWindowInfo> windowInfos = this.getWindows();
            if (!windowInfos.isEmpty()) {
                Rect rect = new Rect();

                String inputMethod = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
                inputMethod = inputMethod.substring(0, inputMethod.lastIndexOf("/"));

                for (int i = 0; i < windowInfos.size(); i++) {
                    AccessibilityWindowInfo win = windowInfos.get(i);
                    AccessibilityNodeInfo root = win.getRoot();
                    if (root != null) {
                        win.getBoundsInScreen(rect);
                        Log.i(TAG, "windows:" + root.getPackageName() + " rect:" + rect);

                        String packageName = root.getPackageName().toString();
                        if (!packageName.equals(inputMethod)) {
                            continue;
                        }

                        Log.i(TAG, "inputmethod:" + inputMethod);
                        try {
                            String name = getExternalFilesDir(null).getAbsolutePath() + "/slave/inputMethodRectInfo.txt";
                            File file = new File(name);
                            file.deleteOnExit();
                            file.getParentFile().mkdirs();
                            file.createNewFile();
                            FileOutputStream fos = new FileOutputStream(file);
                            String info = packageName + ";" + rect.left + ";" + rect.top + ";" + rect.right + ";" + rect.bottom + "\n";
                            fos.write(info.getBytes());
                            fos.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
}
