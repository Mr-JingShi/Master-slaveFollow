package com.masterslavefollow.demo;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbInputStream;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.LocalServices;
import io.github.muntashirakon.adb.android.AdbMdns;
import io.github.muntashirakon.adb.android.AndroidUtils;

// 部分逻辑参考自：
// https://github.com/MuntashirAkon/libadb-android/blob/master/app/src/main/java/io/github/muntashirakon/adb/testapp/MainViewModel.java

public class AdbShell {
    private static String TAG = "AdbDebug";
    private final ExecutorService mExecutors;
    private boolean mConnectSatus = false;
    private int mPort = 0;
    private String mHostAddress;
    private static AdbShell INSTANCE;

    private AdbShell() {
        mExecutors = Executors.newFixedThreadPool(3);
    }

    public static AdbShell getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AdbShell();
        }
        return INSTANCE;
    }

    public boolean getConnectStatus() {
        return mConnectSatus;
    }

    public int getPort() {
        return mPort;
    }

    public boolean connect(int port) {
        Future<?> future = mExecutors.submit(() -> {
            try {
                AbsAdbConnectionManager manager = AdbManager.getInstance(Utils.getContext());
                mConnectSatus = manager.connect(AndroidUtils.getHostIpAddress(Utils.getContext()), port);
            } catch (Throwable th) {
                mConnectSatus = false;
                th.printStackTrace();
            }

            Log.i(TAG, "connect:" + mConnectSatus);
        });

        try {
            future.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mConnectSatus;
    }

    public void disconnect() {
        mExecutors.submit(() -> {
            try {
                AbsAdbConnectionManager manager = AdbManager.getInstance(Utils.getContext());
                manager.disconnect();
                manager.close();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        });

        mExecutors.shutdown();
    }

    public void getPairingPort(Runnable runnable) {
        mExecutors.submit(() -> {
            AtomicInteger atomicPort = new AtomicInteger(-1);
            CountDownLatch resolveHostAndPort = new CountDownLatch(1);

            AdbMdns adbMdns = new AdbMdns(Utils.getContext(), AdbMdns.SERVICE_TYPE_TLS_PAIRING, (hostAddress, port) -> {
                mHostAddress = hostAddress.getHostAddress();

                Log.i(TAG, "hostAddress:" + mHostAddress + " port:" + port);
                atomicPort.set(port);
                resolveHostAndPort.countDown();
            });
            adbMdns.start();

            try {
                if (!resolveHostAndPort.await(1, TimeUnit.MINUTES)) {
                    return;
                }
            } catch (InterruptedException ignore) {
                return;
            } finally {
                adbMdns.stop();
            }

            mPort = atomicPort.get();
            Utils.runOnUiThread(runnable);
        });
    }

    public boolean pair(int port, String pairingCode) {
        Future<?> future = mExecutors.submit(() -> {
            try {
                AbsAdbConnectionManager manager = AdbManager.getInstance(Utils.getContext());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    mConnectSatus = manager.pair(AndroidUtils.getHostIpAddress(Utils.getContext()), port, pairingCode);

                    if (mConnectSatus) {
                        mConnectSatus = manager.autoConnect(Utils.getContext(), 5000);
                    }
                }
            } catch (Throwable th) {
                mConnectSatus = false;
                th.printStackTrace();
            }

            Log.i(TAG, "pair:" + mConnectSatus);
        });

        try {
            future.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mConnectSatus;
    }

    public void execute(String command) {
        execute(command, null);
    }

    public void execute(String command, List<String> lines) {
        Future<?> future = mExecutors.submit(() -> {
            try {
                AbsAdbConnectionManager manager = AdbManager.getInstance(Utils.getContext());
                try (AdbStream adbStream = manager.openStream(LocalServices.SHELL)) {
                    try (OutputStream os = adbStream.openOutputStream()) {
                        os.write(String.format("%1$s\n", command).getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        os.write("\n".getBytes(StandardCharsets.UTF_8));
                    }

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(adbStream.openInputStream()))) {
                        String line = null;
                        String PS1 = null;
                        boolean foundLastEmptyLine = false;
                        boolean foundEndPS1 = false;
                        while ((line = reader.readLine()) != null) {
                            Log.i(TAG, "line:" + line);
                            if (!foundLastEmptyLine || PS1 == null) {
                                if (PS1 == null) {
                                    String cmdStart = command.length() > 10 ? command.substring(0, 10) : command;
                                    int index = line.indexOf(cmdStart);
                                    if (index > 0) {
                                        PS1 = line.substring(0, line.indexOf(cmdStart));
                                        Log.i(TAG, "PS1:" + PS1);
                                        assert !PS1.isEmpty();
                                    }
                                } else if (!foundLastEmptyLine && line.isEmpty()) {
                                    foundLastEmptyLine = true;
                                }
                            } else if (!foundEndPS1) {
                                if (!(foundEndPS1 = line.contains(PS1))) {
                                    if (lines != null) {
                                        lines.add(line);
                                    }
                                }
                            } else {
                                assert line.isEmpty();
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        try {
            future.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public AdbStream executeWithBlockingQueue(String command, BlockingQueue<String> blockingQueue) {
        Future<AdbStream> future = mExecutors.submit(() -> {
            AdbStream adbStream = null;
            try {
                AbsAdbConnectionManager manager = AdbManager.getInstance(Utils.getContext());
                adbStream = manager.openStream(LocalServices.SHELL);
                AdbInputStream adbInputStream = adbStream.openInputStream();
                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(adbInputStream))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            Log.i(TAG, "line:" + line);
                            if (blockingQueue != null) {
                                blockingQueue.offer(line);
                            }
                        }
                        if (!adbInputStream.mAdbStream.isClosed()) {
                            adbInputStream.mAdbStream.close();
                        }
                        Log.i(TAG, "executeWithBlockingQueue reav thread end");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start(); // 启动一个新线程
                try (OutputStream os = adbStream.openOutputStream()) {
                    os.write(String.format("%1$s\n", command).getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.write("\n".getBytes(StandardCharsets.UTF_8));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            return adbStream;
        });

        AdbStream adbStream = null;
        try {
            adbStream = future.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return adbStream;
    }
}