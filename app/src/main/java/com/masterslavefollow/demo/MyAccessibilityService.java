package com.masterslavefollow.demo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.Rect;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

public class MyAccessibilityService extends AccessibilityService {
    public static final int MODE_BLOCK = 0;
    public static final int MODE_NORMAL = 1;

    private static final String TAG = "MyAccessibilityService";

    public static void open() {
        AdbShell.getInstance().execute("content call --uri content://settings/secure --method PUT_secure --arg enabled_accessibility_services --extra _user:i:0 --extra value:s:com.masterslavefollow.demo/.MyAccessibilityService");
        AdbShell.getInstance().execute("content call --uri content://settings/secure --method PUT_secure --arg accessibility_enabled  --extra _user:i:0 --extra value:s:1");
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

        // TYPE_WINDOWS_CHANGED 事件
        if (AccessibilityEvent.TYPE_WINDOWS_CHANGED == event.getEventType()) {
            List<AccessibilityWindowInfo> windowInfos = this.getWindows();
            if (!windowInfos.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                Rect rect = new Rect();

                for (int i = 0; i < windowInfos.size(); i++) {
                    AccessibilityWindowInfo win = windowInfos.get(i);
                    AccessibilityNodeInfo root = win.getRoot();
                    if (root != null) {
                        win.getBoundsInScreen(rect);
                        Log.i(TAG, "windows:" + root.getPackageName() + " rect:" + rect);
                        // 当前访问窗口:com.zui.launcher 当前窗口区域：Rect(24, 1122 - 1836, 1188)
                        // 当前访问窗口:com.iflytek.inputmethod.custom 当前窗口区域：Rect(234, 672 - 821, 707)
                        // 当前访问窗口:com.iflytek.inputmethod.custom 当前窗口区域：Rect(0, 707 - 1920, 1110)
                        // 当前访问窗口:com.autosdk.demo 当前窗口区域：Rect(0, 0 - 1920, 1200)
                        if (i == 0) {
                            sb.append("WINDOWS_CHANGED ");
                        }
                        sb.append("TYPE:");
                        String packageName = root.getPackageName().toString();
                        if (packageName.equals(this.getPackageName())) {
                            sb.append("tool");
                        } else if (packageName.equals(Utils.getTargetAppPackageName())) {
                            sb.append("target");
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
                        if (i != windowInfos.size() - 1) {
                            sb.append(";;");
                        }
                    }
                }

                String str = sb.toString();
                Log.i(TAG, "WINDOWS_CHANGED:" + str);
                Utils.getBlockingQueue().offer(str);
            }
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
}
