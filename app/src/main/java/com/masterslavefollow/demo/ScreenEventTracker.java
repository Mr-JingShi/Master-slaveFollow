package com.masterslavefollow.demo;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

public class ScreenEventTracker {
    private static String TAG = "ScreenEventTracker";
    private List<Socket> mJarChannelList = new ArrayList<>();
    private List<SocketChannel> mAppChannelList = new ArrayList<>();
    private AppChannelThread mAppChannelThread;
    private JarChannelThread mJarChannelThread;
    private SyncThread mSyncThread;
    private Runnable mRunnable;
    public ScreenEventTracker(Runnable runnable) {
        mRunnable = runnable;
    }

    public void createChannel(String masterConfig) {
        mAppChannelThread = new AppChannelThread(masterConfig);
        mAppChannelThread.start();

        mJarChannelThread = new JarChannelThread();
        mJarChannelThread.start();
    }

    public void syncScreenEvent() {
        mSyncThread = new SyncThread();
        mSyncThread.start();
    }

    public int getSuccessCount() {
        return mJarChannelList.size();
    }
    public int getFailCount() {
        return mAppChannelThread.getFailCount();
    }

    public static class DeviceInfo {
        public String add_device;
        public String name;
        public int abs_x = -1;
        public int abs_y = -1;
        public int width = -1;
        public int height = -1;
        public int orientation = -1;
        public int display_id = -1;

        @Override
        public String toString() {
            return "DEVICE_INFO "
                + "ADD_DEVICE:" + add_device
                + ";NAME:" + name
                + ";ABS_X:" + abs_x
                + ";ABS_Y:" + abs_y
                + ";WIDTH:" + width
                + ";HEIGTH:" + height
                + ";ORIENTATION:" + orientation
                + ";DISPLAY_ID:" + display_id;
        }
    }

    private class SyncThread extends Thread {
        SyncThread() {
            super("SyncThread");
            Log.d(TAG, "SyncThread");
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            String appStart = "APP_START";
            try {
                byteBuffer.putInt(appStart.length());
                byteBuffer.put(appStart.getBytes("UTF-8"));
                byteBuffer.flip();
                for (int i = mAppChannelList.size() - 1; i >= 0; i--) {
                    try {
                        mAppChannelList.get(i).write(byteBuffer);
                        Log.i(TAG, "syncScreenEvent");
                    } catch (Exception e) {
                        e.printStackTrace();
                        mAppChannelList.remove(i);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            BlockingQueue<String> blockingQueue = Utils.getBlockingQueue();
            AdbShell.getInstance().executeWithBlockingQueue("getevent -l", blockingQueue);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String event = blockingQueue.take();
                    Log.i(TAG, "event:" + event);

                    for (int i = mJarChannelList.size() - 1; i >= 0; i--) {
                        try {
                            byteBuffer.clear();
                            byteBuffer.putInt(event.length());
                            byteBuffer.put(event.getBytes("UTF-8"));
                            byteBuffer.flip();

                            Log.i(TAG, "byteBuffer:" + byteBuffer);
                            mJarChannelList.get(i).getOutputStream().write(byteBuffer.array(), 0, byteBuffer.limit());
                            mJarChannelList.get(i).getOutputStream().flush();
                        } catch (Exception e) {
                            e.printStackTrace();

                            mJarChannelList.remove(i);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Catch java.lang.InterruptedException:" + e);
            }
        }
    }

    private class JarChannelThread extends Thread {
        JarChannelThread() {
            super("JarChannelThread");
            Log.d(TAG, "JarChannelThread");
        }
        @Override
        public void run() {
            try {
                List<String> selfMessage = Utils.calculateDeviceSize();
                Log.i(TAG, "selfMessage:" + selfMessage);

                try (ServerSocket serverSocket = new ServerSocket()) {
                    serverSocket.setReuseAddress(true);

                    serverSocket.bind(new InetSocketAddress(Utils.JAR_CHANNEL_PORT));
                    Log.i(TAG, "port:" + Utils.JAR_CHANNEL_PORT);

                    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                    while (!Thread.currentThread().isInterrupted()) {
                        Socket socket = serverSocket.accept();
                        Log.d(TAG, "ScreenEventServerThread accept");

                        for (String message : selfMessage) {
                            byteBuffer.clear();
                            byteBuffer.putInt(message.length());
                            byteBuffer.put(message.getBytes("UTF-8"));
                            byteBuffer.flip();
                            socket.getOutputStream().write(byteBuffer.array(), 0, byteBuffer.limit());
                            socket.getOutputStream().flush();
                        }

                        mJarChannelList.add(socket);

                        if (mRunnable != null) {
                            mRunnable.run();
                        }
                    }
                }
            } catch (IOException e) {
                Log.i(TAG, "VideoServerThread IOException:" + e);
            }
        }
    }

    private class AppChannelThread extends Thread {
        private final String mMasterConfig;
        private int mFailCount = 0;
        AppChannelThread(String masterConfig) {
            super("AppChannelThread");
            Log.d(TAG, "AppChannelThread");

            mMasterConfig = masterConfig;
        }
        @Override
        public void run() {
            try {
                Selector selector = Selector.open();
                ServerSocketChannel serverSocket = ServerSocketChannel.open();

                serverSocket.socket().bind(new InetSocketAddress(Utils.APP_CHANNEL_PORT));
                serverSocket.socket().setReuseAddress(true);
                serverSocket.configureBlocking(false);
                serverSocket.register(selector, SelectionKey.OP_ACCEPT);

                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

                while (!Thread.currentThread().isInterrupted()) {
                    if (selector.select() != 0) {
                        Set keys = selector.selectedKeys();
                        Iterator iterator = keys.iterator();

                        while (iterator.hasNext()) {
                            SelectionKey key = (SelectionKey) iterator.next();
                            iterator.remove();

                            if (key.isAcceptable()) {
                                ServerSocketChannel server = (ServerSocketChannel) key.channel();
                                SocketChannel socketChannel = server.accept();
                                socketChannel.configureBlocking(false);
                                socketChannel.register(selector, SelectionKey.OP_READ);

                                Log.i(TAG, "accept loacal:" + socketChannel.socket().getLocalSocketAddress() + " remote:" + socketChannel.socket().getRemoteSocketAddress());

                                byteBuffer.clear();
                                byteBuffer.putInt(mMasterConfig.length());
                                byteBuffer.put(mMasterConfig.getBytes("UTF-8"));
                                byteBuffer.flip();
                                socketChannel.write(byteBuffer);
                            } else if (key.isReadable()) {
                                Log.i(TAG, "read");
                                SocketChannel socketChannel = (SocketChannel) key.channel();

                                byteBuffer.clear();
                                byteBuffer.limit(4);
                                int recv = socketChannel.read(byteBuffer);
                                if (recv > 0) {
                                    byteBuffer.flip();
                                    int length = byteBuffer.getInt();

                                    byteBuffer.limit(4 + length);
                                    byteBuffer.position(4);
                                    recv = socketChannel.read(byteBuffer);
                                    if (recv == length) {
                                        byteBuffer.flip();
                                        String message = new String(byteBuffer.array(), 4, length, "UTF-8");
                                        Log.i(TAG, "message:" + message);

                                        if (message.equals("OK")) {
                                            mAppChannelList.add(socketChannel);
                                        } else {
                                            mFailCount++;

                                            if (mRunnable != null) {
                                                mRunnable.run();
                                            }
                                        }
                                    } else {
                                        Log.e(TAG, "read error");
                                        key.cancel();
                                        socketChannel.close();
                                    }
                                } else {
                                    Log.e(TAG, "read error");
                                    key.cancel();
                                    socketChannel.close();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public int getFailCount() {
            return mFailCount;
        }
    }
}


