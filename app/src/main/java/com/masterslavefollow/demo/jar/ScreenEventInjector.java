package com.masterslavefollow.demo.jar;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.masterslavefollow.demo.ScreenEventTracker;
import com.masterslavefollow.demo.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

public class ScreenEventInjector {
    private static String HOST;
    private static int PORT;
    private static int NAVIGATION_BAR_HEIGHT;

    public static void main(String[] args) {
        try {
            HOST = args[0];
            PORT = Integer.parseInt(args[1]);
            NAVIGATION_BAR_HEIGHT = Integer.parseInt(args[2]);

            Thread screenEventInjectorThread = new ScreenEventInjectorThread();
            screenEventInjectorThread.start();

            screenEventInjectorThread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class FloatRectInfo {
        int left;
        int top;
        int right;
        int bottom;

        public FloatRectInfo() {
            clear();
        }

        @Override
        public String toString() {
            return "left:" + left + ", top:" + top + ", right:" + right + ", bottom:" + bottom;
        }

        public void clear() {
            left = Integer.MAX_VALUE;
            top = Integer.MAX_VALUE;
            right = Integer.MIN_VALUE;
            bottom = Integer.MIN_VALUE;
        }
    }

    private static class ScreenEventInjectorThread extends Thread {
        private int[] xy = new int[2];

        private boolean[] waitForXY = {true, true};

        private long lastTouchDown;

        private boolean recvDown = false;

        private ScreenEventTracker.DeviceInfo deviceInfo = null;
        private FloatRectInfo targetFloatRectInfo = new FloatRectInfo();
        private FloatRectInfo toolFloatRectInfo = new FloatRectInfo();
        private FloatRectInfo navigationFloatRectInfo = new FloatRectInfo();
        private FloatRectInfo inputmethodFloatRectInfo = new FloatRectInfo();
        private float xRate = 0.0f;
        private float yRate = 0.0f;
        private int selfWidth = 0;
        private int selfHeight = 0;

        private final int pointerCount = 1;
        private MotionEvent.PointerProperties[] pointerProperties;
        private MotionEvent.PointerCoords[] pointerCoords;

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
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            selfHeight -= NAVIGATION_BAR_HEIGHT;
            System.out.println("selfWidth:" + selfWidth + ", selfHeight:" + selfHeight);

            pointerProperties = new MotionEvent.PointerProperties[pointerCount];
            pointerCoords = new MotionEvent.PointerCoords[pointerCount];
            for (int i = 0; i < pointerCount; i++) {
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

                    if (line.startsWith("DEVICE_INFO")) {
                        // DEVICE_INFO ADD_DEVICE:/dev/input/event2;NAME:himax-touchscreen;ABS_X:12000;ABS_Y:19200;WIDTH:1920;HEIGTH:1200;DISPLAY_ID:0
                        deviceInfo = new ScreenEventTracker.DeviceInfo();
                        String[] splited = line.substring("DEVICE_INFO".length() + 1).split(";");
                        for (String s : splited) {
                            String[] kv = s.split(":");
                            if (kv.length == 2) {
                                if (kv[0].equals("ADD_DEVICE")) {
                                    deviceInfo.add_device = kv[1];
                                } else if (kv[0].equals("NAME")) {
                                    deviceInfo.name = kv[1];
                                } else if (kv[0].equals("ABS_X")) {
                                    deviceInfo.abs_x = Integer.parseInt(kv[1]);
                                } else if (kv[0].equals("ABS_Y")) {
                                    deviceInfo.abs_y = Integer.parseInt(kv[1]);
                                } else if (kv[0].equals("WIDTH")) {
                                    deviceInfo.width = Integer.parseInt(kv[1]);
                                } else if (kv[0].equals("HEIGTH")) {
                                    deviceInfo.height = Integer.parseInt(kv[1]);
                                } else if (kv[0].equals("ORIENTATION")) {
                                    deviceInfo.orientation = Integer.parseInt(kv[1]);
                                } else if (kv[0].equals("DISPLAY_ID")) {
                                    deviceInfo.display_id = Integer.parseInt(kv[1]);
                                }
                            }
                        }

                        System.out.println("deviceInfo:" + deviceInfo.toString());

                        xRate = (float) deviceInfo.width / deviceInfo.abs_x;
                        yRate = (float) deviceInfo.height / deviceInfo.abs_y;

                        System.out.println("xRate:" + xRate + ", yRate:" + yRate);
                    } else if (line.startsWith("WINDOWS_CHANGED")) {
                        targetFloatRectInfo.clear();
                        toolFloatRectInfo.clear();
                        navigationFloatRectInfo.clear();
                        inputmethodFloatRectInfo.clear();

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
                                            floatRectInfo = targetFloatRectInfo;
                                        } else if ("tool".equals(kv[1])) {
                                            floatRectInfo = toolFloatRectInfo;
                                        } else if ("navigation".equals(kv[1])) {
                                            floatRectInfo = navigationFloatRectInfo;
                                        } else if ("inputmethod".equals(kv[1])) {
                                            floatRectInfo = inputmethodFloatRectInfo;
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
                                            System.out.println("floatRectInfo.bottom:" + floatRectInfo.bottom);
                                        }
                                    }
                                }
                            }
                        }
                    } else if (line.contains("BTN_TOUCH")) {
                        if (line.contains("UP")) {
                            recvDown = false;
                            inject(MotionEvent.ACTION_UP, xy[0], xy[1]);
                        } else if (line.contains("DOWN")) {
                            recvDown = true;
                            inject(MotionEvent.ACTION_DOWN, xy[0], xy[1]);
                            move();
                        }
                    } else if (line.contains("ABS_MT_POSITION_X")) {
                        String[] splited = line.split("ABS_MT_POSITION_X");
                        String x = splited[splited.length - 1].trim();
                        xy[0] = (int) (Integer.parseInt(x, 16) * xRate);

                        waitForXY[0] = false;

                        if (recvDown) {
                            move();
                        }
                    } else if (line.contains("ABS_MT_POSITION_Y")) {
                        String[] splited = line.split("ABS_MT_POSITION_Y");
                        String y = splited[splited.length - 1].trim();
                        xy[1] = (int) (Integer.parseInt(y, 16) * yRate);

                        waitForXY[1] = false;

                        if (recvDown) {
                            move();
                        }
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

        private void move() {
            if (!waitForXY[0] && !waitForXY[1]) {
                inject(MotionEvent.ACTION_MOVE, xy[0], xy[1]);

                // 接收下一次事件
                waitForXY[0] = true;
                waitForXY[1] = true;
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

        public void inject(int action, int x, int y) {
            // src 1200x1920 dst 1080x2160 1200x2000
            if (deviceInfo.orientation == 1) {
                int tmp = y;
                y = deviceInfo.width - x;
                x = tmp;
            }

            System.out.println("x0:" + x + ", y0:" + y);
            x = (x*selfWidth)/targetFloatRectInfo.right;
            y = (y*selfHeight)/targetFloatRectInfo.bottom;
            System.out.println("x:" + x + ", y:" + y);

            if (targetFloatRectInfo.left > x
                || x > targetFloatRectInfo.right
                || targetFloatRectInfo.top > y
                || y > targetFloatRectInfo.bottom) {
                System.out.println("not in target rect");
                return;
            }

            if (toolFloatRectInfo.left <= x
                && x <= toolFloatRectInfo.right
                && toolFloatRectInfo.top <= y
                && y <= toolFloatRectInfo.bottom) {
                System.out.println("in tool rect:" + toolFloatRectInfo.toString());
                return;
            }

            if (navigationFloatRectInfo.left <= x
                && x <= navigationFloatRectInfo.right
                && navigationFloatRectInfo.top <= y
                && y <= navigationFloatRectInfo.bottom) {
                System.out.println("in navigation rect:" + navigationFloatRectInfo.toString());
                return;
            }

            if (inputmethodFloatRectInfo.left <= x
                && x <= inputmethodFloatRectInfo.right
                && inputmethodFloatRectInfo.top <= y
                && y <= inputmethodFloatRectInfo.bottom) {
                System.out.println("in inputmethod rect:" + inputmethodFloatRectInfo.toString());
                return;
            }

            long now = SystemClock.uptimeMillis();

            // Physical size: 1200x1920
            pointerCoords[0].x = x;
            pointerCoords[0].y = y;
            pointerCoords[0].pressure = action == MotionEvent.ACTION_UP ? 0.0f : 1.0f;
            if (action == MotionEvent.ACTION_DOWN) {
                lastTouchDown = now;
            }

            MotionEvent event = MotionEvent.obtain(lastTouchDown, now, action, pointerCount, pointerProperties, pointerCoords, 0, 0, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
            if (deviceInfo.display_id > 0) {
                InputManager.setDisplayId(event, deviceInfo.display_id);
            }

            InputManager.getInputManager().injectInputEvent(event);
            System.out.println("injectInputEvent:" + event);
        }
    }
}