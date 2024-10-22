package com.masterslavefollow.demo.jar;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.masterslavefollow.demo.ScreenEventTracker;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;

public class ScreenEventInjector {
    private static String HOST;
    private static String EXTERNAL_PATH;
    private static int PORT;
    private static int NAVIGATION_BAR_HEIGHT;
    private static String ANDROID_ID;
    private static boolean LOCAL_PLAYBACK = false;

    public static void main(String[] args) {
        try {
            HOST = args[0];
            EXTERNAL_PATH = args[1];
            PORT = Integer.parseInt(args[2]);
            NAVIGATION_BAR_HEIGHT = Integer.parseInt(args[3]);
            ANDROID_ID = args[4];

            Thread thread = new ScreenEventInjectorThread();
            thread.start();

            thread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class FloatRectInfo {
        boolean valid;
        int left;
        int top;
        int right;
        int bottom;

        public FloatRectInfo() {
            valid = false;
        }

        @Override
        public String toString() {
            if (valid) {
                return "left:" + left + ", top:" + top + ", right:" + right + ", bottom:" + bottom;
            }
            return "invalid";
        }
    }

    private static class ScreenEventInjectorThread extends Thread {
        private String editViewText;
        private ScreenEventTracker.DeviceInfo remoteDeviceInfo = null;
        private FloatRectInfo remoteTargetFRI = new FloatRectInfo();
        private FloatRectInfo remoteToolFRI = new FloatRectInfo();
        private FloatRectInfo remoteNavigationFRI = new FloatRectInfo();
        private FloatRectInfo remoteInputmethodFRI = new FloatRectInfo();
        private FloatRectInfo selfInputmethodFRI = new FloatRectInfo();
        private float xRate = 0.0f;
        private float yRate = 0.0f;
        private int selfWidth = 0;
        private int selfHeight = 0;
        private int[] floatXs;
        private int[] floatYs;
        private List<Integer> listSlots;
        private Queue<Integer> queueSlots;
        private MotionEvent.PointerProperties[] pointerProperties;
        private MotionEvent.PointerCoords[] pointerCoords;
        private long lastTouchDown;
        private int trackingIds = 0;
        private int currentSlot = 0;
        private int currentAction = MotionEvent.ACTION_DOWN;
        private boolean needSendDown = false;

        public ScreenEventInjectorThread() {
            super("ScreenEventInjectorThread");
            System.out.println("ScreenEventInjectorThread");
        }

        public static String execReadOutput(String... cmd) throws IOException, InterruptedException {
            Process process = Runtime.getRuntime().exec(cmd);

            StringBuilder builder = new StringBuilder();
            Scanner scanner = new Scanner(process.getInputStream());
            while (scanner.hasNextLine()) {
                builder.append(scanner.nextLine()).append('\n');
            }
            String output = builder.toString();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Command " + Arrays.toString(cmd) + " returned with value " + exitCode);
            }
            return output;
        }

        @Override
        public void run() {
            try {
                String output = execReadOutput("wm", "size");
                // Physical size: 1200x1920
                String[] splited = output.split(":");
                if (splited.length == 2) {
                    String[] splited2 = splited[1].split("x");
                    if (splited2.length == 2) {
                        selfWidth = Integer.parseInt(splited2[0].trim());
                        selfHeight = Integer.parseInt(splited2[1].trim());
                        System.out.println("selfWidth:" + selfWidth + ", selfHeight:" + selfHeight);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            floatXs = new int[10];
            floatYs = new int[10];
            listSlots = new ArrayList<>(10);
            queueSlots = new ArrayDeque<>(10);
            pointerProperties = new MotionEvent.PointerProperties[10];
            pointerCoords = new MotionEvent.PointerCoords[10];
            for (int i = 0; i < 10; i++) {
                pointerProperties[i] = new MotionEvent.PointerProperties();
                pointerProperties[i].id = i;
                pointerProperties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                pointerCoords[i] = new MotionEvent.PointerCoords();
                pointerCoords[i].x = 0;
                pointerCoords[i].y = 0;
                pointerCoords[i].pressure = 1.0f;
                pointerCoords[i].size = 1.0f;
            }

            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(HOST, PORT), 3000);

                System.out.println("ScreenEventClientThread connect");

                byte[] eventBuffer = new byte[0];
                byte[] lengthBuffer = new byte[4];
                int len = 0;
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

                    System.out.println("slave recv event:" + line);

                    if (!LOCAL_PLAYBACK && remoteInputmethodFRI.valid && !selfInputmethodFRI.valid) {
                        String name = EXTERNAL_PATH + "/slave/inputMethodRectInfo.txt";
                        File file = new File(name);
                        if (file.exists()) {
                            FileInputStream fis = new FileInputStream(file);
                            byte[] buf = new byte[(int)file.length()];
                            fis.read(buf, 0, buf.length);
                            fis.close();

                            String content = new String(buf, 0, buf.length - 1, "UTF-8");
                            System.out.println("content:" + content);
                            String[] splited = content.split(";");
                            if (splited.length == 5) {
                                selfInputmethodFRI.valid = true;
                                selfInputmethodFRI.left = Integer.parseInt(splited[1]);
                                selfInputmethodFRI.top = Integer.parseInt(splited[2]);
                                selfInputmethodFRI.right = Integer.parseInt(splited[3]);
                                selfInputmethodFRI.bottom = Integer.parseInt(splited[4]);
                                System.out.println("selfInputmethodFRI:" + selfInputmethodFRI.toString());
                            }
                        }
                    }

                    if (line.contains("BTN_TOUCH")) {
                        String[] splited = line.split("BTN_TOUCH");
                        String action = splited[splited.length - 1].trim();
                        System.out.println("BTN_TOUCH:" + action);
                        if (action.equals("DOWN")) {
                            needSendDown = true;
                        }
                    } else if (line.contains("ABS_MT_TRACKING_ID")) {
                        String[] splited = line.split("ABS_MT_TRACKING_ID");
                        String id = splited[splited.length - 1].trim();
                        System.out.println("ABS_MT_TRACKING_ID:" + id);
                        if (id.equals("ffffffff")) {
                            if (--trackingIds == 0) {
                                currentAction = MotionEvent.ACTION_UP;
                            } else {
                                currentAction = MotionEvent.ACTION_POINTER_UP | currentSlot * 0x0100;
                            }
                            queueSlots.offer(Integer.valueOf(currentSlot));
                        } else {
                            if (trackingIds++ == 0) {
                                listSlots.clear();
                                queueSlots.clear();
                                currentSlot = 0;
                                currentAction = MotionEvent.ACTION_DOWN;
                            } else {
                                currentAction = MotionEvent.ACTION_POINTER_DOWN | currentSlot * 0x0100;
                            }
                            System.out.println("add slot:" + currentSlot);
                            listSlots.add(Integer.valueOf(currentSlot));
                        }
                    } else if (line.contains("ABS_MT_SLOT")) {
                        String[] splited = line.split("ABS_MT_SLOT");
                        String slot = splited[splited.length - 1].trim();
                        System.out.println("slot:" + slot);
                        currentSlot = Integer.parseInt(slot);
                    } else if (line.contains("ABS_MT_POSITION_X")) {
                        String[] splited = line.split("ABS_MT_POSITION_X");
                        String x = splited[splited.length - 1].trim();
                        floatXs[currentSlot] = Integer.parseInt(x, 16);
                    } else if (line.contains("ABS_MT_POSITION_Y")) {
                        String[] splited = line.split("ABS_MT_POSITION_Y");
                        String y = splited[splited.length - 1].trim();
                        floatYs[currentSlot] = Integer.parseInt(y, 16);
                    } else if (line.contains("SYN_REPORT")) {
                        inject();
                        currentAction = MotionEvent.ACTION_MOVE;
                    } else if (line.startsWith("DEVICE_INFO")) {
                        // DEVICE_INFO ADD_DEVICE:/dev/input/event2;NAME:himax-touchscreen;ABS_X:12000;ABS_Y:19200;WIDTH:1920;HEIGTH:1200;DISPLAY_ID:0
                        remoteDeviceInfo = new ScreenEventTracker.DeviceInfo();
                        String[] splited = line.substring("DEVICE_INFO".length() + 1).split(";");
                        for (String s : splited) {
                            String[] kv = s.split(":");
                            if (kv.length == 2) {
                                if (kv[0].equals("ADD_DEVICE")) {
                                    remoteDeviceInfo.add_device = kv[1];
                                } else if (kv[0].equals("NAME")) {
                                    remoteDeviceInfo.name = kv[1];
                                } else if (kv[0].equals("ABS_X")) {
                                    remoteDeviceInfo.abs_x = Integer.parseInt(kv[1]);
                                } else if (kv[0].equals("ABS_Y")) {
                                    remoteDeviceInfo.abs_y = Integer.parseInt(kv[1]);
                                } else if (kv[0].equals("WIDTH")) {
                                    remoteDeviceInfo.width = Integer.parseInt(kv[1]);
                                } else if (kv[0].equals("HEIGHT")) {
                                    remoteDeviceInfo.height = Integer.parseInt(kv[1]);
                                } else if (kv[0].equals("ORIENTATION")) {
                                    remoteDeviceInfo.orientation = Integer.parseInt(kv[1]);
                                } else if (kv[0].equals("DISPLAY_ID")) {
                                    remoteDeviceInfo.display_id = Integer.parseInt(kv[1]);
                                } else if (kv[0].equals("NAVIGATION_BAR_HEIGHT")) {
                                    remoteDeviceInfo.navigation_bar_height = Integer.parseInt(kv[1]);
                                }
                            }
                        }

                        System.out.println("deviceInfo:" + remoteDeviceInfo.toString());

                        xRate = (float) remoteDeviceInfo.width / remoteDeviceInfo.abs_x;
                        yRate = (float) remoteDeviceInfo.height / remoteDeviceInfo.abs_y;

                        System.out.println("xRate:" + xRate + ", yRate:" + yRate);
                    } else if (line.startsWith("WINDOWS_CHANGED")) {
                        remoteTargetFRI.valid = false;
                        remoteToolFRI.valid = false;
                        remoteNavigationFRI.valid = false;
                        remoteInputmethodFRI.valid = false;
                        selfInputmethodFRI.valid = false;

                        String[] outerSplited = line.substring("WINDOWS_CHANGED".length() + 1).split(";;");

                        FloatRectInfo floatRectInfo = null;
                        for (String outer : outerSplited) {
                            System.out.println("outer:" + outer);
                            String[] innerSplited = outer.split(";");
                            for (String inner : innerSplited) {
                                String[] kv = inner.split(":");
                                if (kv.length == 2) {
                                    if (kv[0].equals("TYPE")) {
                                        if ("target".equals(kv[1])) {
                                            floatRectInfo = remoteTargetFRI;
                                        } else if ("tool".equals(kv[1])) {
                                            floatRectInfo = remoteToolFRI;
                                        } else if ("navigation".equals(kv[1])) {
                                            floatRectInfo = remoteNavigationFRI;
                                        } else if ("inputmethod".equals(kv[1])) {
                                            floatRectInfo = remoteInputmethodFRI;
                                        } else {
                                            break;
                                        }
                                    } else if (kv[0].equals("LEFT")) {
                                        if (floatRectInfo != null) {
                                            floatRectInfo.left = Integer.parseInt(kv[1]);
                                            System.out.println("floatRectInfo.left:" + floatRectInfo.left);
                                        }
                                    } else if (kv[0].equals("TOP")) {
                                        if (floatRectInfo != null) {
                                            floatRectInfo.top = Integer.parseInt(kv[1]);
                                            System.out.println("floatRectInfo.top:" + floatRectInfo.top);
                                        }
                                    } else if (kv[0].equals("RIGHT")) {
                                        if (floatRectInfo != null) {
                                            floatRectInfo.right = Integer.parseInt(kv[1]);
                                            System.out.println("floatRectInfo.right:" + floatRectInfo.right);
                                        }
                                    } else if (kv[0].equals("BOTTOM")) {
                                        if (floatRectInfo != null) {
                                            floatRectInfo.bottom = Integer.parseInt(kv[1]);
                                            floatRectInfo.valid = true;
                                            System.out.println("floatRectInfo.bottom:" + floatRectInfo.bottom);
                                        }
                                    } else if (kv[0].equals("ROTATION")) {
                                        remoteDeviceInfo.orientation = Integer.parseInt(kv[1]);
                                        System.out.println("rotation:" + remoteDeviceInfo.orientation);
                                    }
                                }
                            }
                        }
                    } else if (!LOCAL_PLAYBACK && line.startsWith("EDITVIEW_CHANGED ")) {
                        // TODO 需要确保输入法已经被调起了
                        String[] splited = line.substring("EDITVIEW_CHANGED ".length()).split(";");
                        for (String s : splited) {
                            String[] kv = s.split(":");
                            if (kv.length == 2) {
                                if (kv[0].equals("TEXT")) {
                                    if (editViewText != null) {
                                        for (int i = 0; i < editViewText.length(); i++) {
                                            sendKeyEvent(KeyEvent.KEYCODE_DEL);
                                        }
                                    }

                                    editViewText = kv[1];
                                    if (editViewText != null && editViewText.length() > 2) {
                                        editViewText = editViewText.substring(1, editViewText.length() - 1);
                                        System.out.println("text:" + editViewText);
                                        sendTextEvent(editViewText);
                                    }
                                }
                            }
                        }
                    } else if (line.startsWith("IDENTIFICATIONCODE ")) {
                        String targetAndroidId = line.substring("IDENTIFICATIONCODE ANDROIDID:".length());
                        System.out.println("ANDROID_ID:" + ANDROID_ID + ", targetAndroidId:" + targetAndroidId);
                        if (targetAndroidId.equals(ANDROID_ID)) {
                            LOCAL_PLAYBACK = true;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
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

        private static void recv(InputStream inputStream, byte[] buffer, int sum) throws Exception {
            int read = 0;
            while (sum - read > 0) {
                int len = inputStream.read(buffer, read, sum - read);
                if (len == -1) {
                    throw new RuntimeException("socket closed");
                }
                read += len;
            }
        }

        public static int byte4ToInt(byte[] bytes) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            int b2 = bytes[2] & 0xFF;
            int b3 = bytes[3] & 0xFF;
            return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
        }

        public void inject() {
            System.out.println("currentAction:" + currentAction);
            int pointerCount = 0;
            for (Integer integer : listSlots) {
                int slot = integer.intValue();
                System.out.println("slot:" + slot);
                float x = (float) floatXs[slot] * xRate;
                float y = (float) floatYs[slot] * yRate;

                if (remoteDeviceInfo.orientation == 1) {
                    float tmp = y;
                    y = remoteDeviceInfo.width - x;
                    x = tmp;
                }
                System.out.println("x0:" + x + ", y0:" + y);

                // 必须在target内
                if (remoteTargetFRI.valid
                        && (remoteTargetFRI.left > x
                        || x > remoteTargetFRI.right
                        || remoteTargetFRI.top > y
                        || y > remoteTargetFRI.bottom)) {
                    System.out.println("not in target rect");
                    return;
                }

                if (LOCAL_PLAYBACK) {
                    // 单机模式时不能在tool内
                    if (remoteToolFRI.valid
                            && remoteToolFRI.left <= x
                            && x <= remoteToolFRI.right
                            && remoteToolFRI.top <= y
                            && y <= remoteToolFRI.bottom) {
                        System.out.println("in tool rect:" + remoteToolFRI.toString());
                        return;
                    }
                } else {
                    // 主从模式时不能在target inputmethod内
                    if (remoteInputmethodFRI.valid
                            && remoteInputmethodFRI.left <= x
                            && x <= remoteInputmethodFRI.right
                            && remoteInputmethodFRI.top <= y
                            && y <= remoteInputmethodFRI.bottom) {
                        System.out.println("in target inputmethod rect:" + remoteInputmethodFRI.toString());
                        return;
                    }

                    // 主从模式时不能在导航栏内
                    if (remoteNavigationFRI.valid
                            && remoteNavigationFRI.left <= x
                            && x <= remoteNavigationFRI.right
                            && remoteNavigationFRI.top <= y
                            && y <= remoteNavigationFRI.bottom) {
                        System.out.println("in navigation rect:" + remoteNavigationFRI.toString());
                        return;
                    }

                    if (remoteNavigationFRI.valid
                            && (remoteNavigationFRI.right - remoteNavigationFRI.left) == remoteDeviceInfo.width
                            && (remoteNavigationFRI.bottom - remoteNavigationFRI.top) == remoteDeviceInfo.navigation_bar_height) {
                        x = (x*selfWidth)/remoteDeviceInfo.width;
                        y = (y*(selfHeight - NAVIGATION_BAR_HEIGHT))/(remoteDeviceInfo.height - remoteDeviceInfo.navigation_bar_height);
                    } else {
                        x = (x*(selfWidth - NAVIGATION_BAR_HEIGHT))/(remoteDeviceInfo.width - remoteDeviceInfo.navigation_bar_height);
                        y = (y*selfHeight)/remoteDeviceInfo.height;
                    }

                    // 主从模式时不能在自己输入法内
                    if (selfInputmethodFRI.valid
                            && selfInputmethodFRI.left <= x
                            && x <= selfInputmethodFRI.right
                            && selfInputmethodFRI.top <= y
                            && y <= selfInputmethodFRI.bottom) {
                        System.out.println("in self inputmethod rect:" + selfInputmethodFRI.toString());
                        return;
                    }
                }

                System.out.println("x:" + x + ", y:" + y);

                pointerCoords[pointerCount].x = x;
                pointerCoords[pointerCount].y = y;
                pointerProperties[pointerCount].id = slot;
                pointerCoords[pointerCount].pressure = currentAction == MotionEvent.ACTION_UP ? 0.0f : 1.0f;
                ++pointerCount;
            }

            if (pointerCount > 0) {
                boolean needRecurse = false;
                long now = SystemClock.uptimeMillis();
                if (currentAction == MotionEvent.ACTION_DOWN) {
                    lastTouchDown = now;
                    needSendDown = false;
                } else if (currentAction == MotionEvent.ACTION_UP) {
                    int slot = queueSlots.remove().intValue();
                    if (pointerCount > 1) {
                        needRecurse = true;
                        System.out.println("currentSlot:" + currentSlot);

                        currentAction = MotionEvent.ACTION_POINTER_UP | currentSlot * 0x0100;
                    }
                    System.out.println("ACTION_UP remove slot:" + slot);
                    listSlots.remove(Integer.valueOf(slot));
                } else if ((currentAction & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_DOWN) {
                    if (needSendDown) {
                        needSendDown = false;
                        lastTouchDown = now;
                        SendMotionEvent(MotionEvent.ACTION_DOWN, 1, now);
                        System.out.println("need send ACTION_DOWN first before send ACTION_POINTER_DOWN");
                    }
                } else if ((currentAction & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_UP) {
                    int slot = queueSlots.remove().intValue();
                    System.out.println("ACTION_POINTER_UP remove slot:" + slot);
                    listSlots.remove(Integer.valueOf(slot));
                }

                SendMotionEvent(currentAction, pointerCount, now);

                if (needRecurse) {
                    currentAction = MotionEvent.ACTION_UP;
                    inject();
                }
            }
        }

        private void SendMotionEvent(int action, int pointerCount, long now) {
            System.out.println("pointerCount:" + pointerCount);
            MotionEvent event = MotionEvent.obtain(lastTouchDown, now, action, pointerCount, pointerProperties, pointerCoords, 0, 0, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
            if (remoteDeviceInfo.display_id > 0) {
                InputManager.setDisplayId(event, remoteDeviceInfo.display_id);
            }

            InputManager.getInputManager().injectInputEvent(event);
            System.out.println("injectInputEvent:" + event);
        }

        private void sendTextEvent(final String text) {
            final StringBuilder buff = new StringBuilder(text);
            boolean escapeFlag = false;
            for (int i = 0; i < buff.length(); i++) {
                if (escapeFlag) {
                    escapeFlag = false;
                    if (buff.charAt(i) == 's') {
                        buff.setCharAt(i, ' ');
                        buff.deleteCharAt(--i);
                    }
                }
                if (buff.charAt(i) == '%') {
                    escapeFlag = true;
                }
            }

            final char[] chars = buff.toString().toCharArray();
            final KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
            final KeyEvent[] events = kcm.getEvents(chars);
            for (int i = 0; i < events.length; i++) {
                KeyEvent e = events[i];
                if (InputDevice.SOURCE_KEYBOARD != e.getSource()) {
                    e.setSource(InputDevice.SOURCE_KEYBOARD);
                }

                if (remoteDeviceInfo.display_id > 0) {
                    InputManager.setDisplayId(e, remoteDeviceInfo.display_id);
                }

                InputManager.getInputManager().injectInputEvent(e);
                System.out.println("injectInputEvent:" + e);
            }
        }

        private void sendKeyEvent(int keyCode) {
            final long now = SystemClock.uptimeMillis();

            KeyEvent event = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0 /* repeatCount */,
                    0 /*metaState*/, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /*scancode*/, 0 /*flags*/,
                    InputDevice.SOURCE_KEYBOARD);

            if (remoteDeviceInfo.display_id > 0) {
                InputManager.setDisplayId(event, remoteDeviceInfo.display_id);
            }

            InputManager.getInputManager().injectInputEvent(event);
            System.out.println("injectInputEvent:" + event);
            InputManager.getInputManager().injectInputEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_UP));
        }
    }
}