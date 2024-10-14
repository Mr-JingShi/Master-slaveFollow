package com.masterslavefollow.demo;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.LockSupport;

import io.github.muntashirakon.adb.AdbStream;

// 部分逻辑参考自：
// https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/display/OverlayDisplayWindow.java

final class RecordingAndPlayBack {
    private static final String TAG = "FloatInputDialog";
    private static final String RECORDING_EXIT = "RECORDING_EXIT";
    private final MasterActivity mMasterActivity;
    private View mFloatContent;
    private float x0 = 0.0f;
    private float y0 = 0.0f;
    private boolean mIsRecording = true;
    private boolean mJarStarted = false;
    private String mRecordingFileName;
    private String mPlaybackFileName;
    private Thread mRecordingThread;
    private boolean mRecordingWorking = false;
    private Thread mPlaybackThread;
    private boolean mPlaybackWorking = false;

    public RecordingAndPlayBack(MasterActivity activity) {
        mMasterActivity = activity;
        LayoutInflater inflater = LayoutInflater.from(activity);
        mFloatContent = inflater.inflate(R.layout.float_recording_playback, null);

        WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams();
        windowParams.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        windowParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        windowParams.format = PixelFormat.RGBA_8888;
        windowParams.alpha = 1.0f;
        windowParams.gravity = Gravity.TOP | Gravity.RIGHT;

        windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

        WindowManager windowManager = (WindowManager)Utils.getContext().getSystemService(Context.WINDOW_SERVICE);
        windowManager.addView(mFloatContent, windowParams);

        mFloatContent.findViewById(R.id.recording_playback).setOnClickListener((View v) -> {
            if (mIsRecording) {
                mRecordingWorking = !mRecordingWorking;
                if (mRecordingWorking) {
                    LockSupport.unpark(mRecordingThread);

                    setName("停止", R.drawable.ic_recording_stop);
                    setText("正在录制：" + mRecordingFileName.substring(mRecordingFileName.lastIndexOf("/") + 1));
                } else {
                    Utils.getBlockingQueue().offer(RECORDING_EXIT);

                    setName("录制", R.drawable.ic_recording);
                    setText("空闲");
                }
            } else {
                mPlaybackWorking = !mPlaybackWorking;
                if (mPlaybackWorking) {
                    LockSupport.unpark(mPlaybackThread);

                    setName("停止", R.drawable.ic_playback_stop);
                    startJar();
                } else {
                    setName("回放", R.drawable.ic_playback);
                    setText("空闲");
                }
            }
        });

        mFloatContent.findViewById(R.id.recording_playback_back).setOnClickListener((View v) -> {
            hide();
        });

        final int widthPixels = Utils.getContext().getResources().getDisplayMetrics().widthPixels;
        final int heightPixels = Utils.getContext().getResources().getDisplayMetrics().heightPixels;

        mFloatContent.findViewById(R.id.recording_playback_drag).setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                x0 = event.getRawX();
                y0 = event.getRawY();
            } else if (action == MotionEvent.ACTION_MOVE) {
                float x = windowParams.x - (event.getRawX() - x0);
                float y = windowParams.y + (event.getRawY() - y0);

                x0 = event.getRawX();
                y0 = event.getRawY();

                x = Math.max(0, Math.min(x, widthPixels - mFloatContent.getWidth()));
                y = Math.max(0, Math.min(y, heightPixels - mFloatContent.getHeight()));

                windowParams.x = (int)x;
                windowParams.y = (int)y;

                // Log.i(TAG, "x: " + x + " y: " + y);

                windowManager.updateViewLayout(mFloatContent, windowParams);
            }
            return true;
        });

        windowParams.x = 20;
        windowParams.y = 100;

        windowManager.updateViewLayout(mFloatContent, windowParams);
        mFloatContent.setVisibility(View.GONE);
    }

    public void setRecordingFileName(String fileName) {
        mRecordingFileName = fileName;
        mIsRecording = true;

        if (mRecordingThread == null) {
            mRecordingThread = new RecordingThread();
            mRecordingThread.start();
        }

        setName("录制", R.drawable.ic_recording);
        setText("空闲");
    }

    public void setPlaybackFiles(String fileName) {
        mPlaybackFileName = fileName;
        mIsRecording = false;

        if (mPlaybackThread == null) {
            mPlaybackThread = new PlaybackThread(mPlaybackFileName);
            mPlaybackThread.start();
        }

        setName("回放", R.drawable.ic_playback);
        setText("空闲");
    }

    public void show() {
        mFloatContent.setVisibility(View.VISIBLE);
    }

    public void hide() {
        mFloatContent.setVisibility(View.GONE);

        Intent intent = new Intent(Utils.getContext(), MasterActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra("from", "RecordingAndPlayBack");
        mMasterActivity.startActivity(intent);
    }

    private void setName(String name, int id) {
        TextView textView = mFloatContent.findViewById(R.id.recording_playback_name);
        textView.setText(name);
        ImageView image = mFloatContent.findViewById(R.id.recording_playback_img);
        image.setImageResource(id);
    }
    private void setText(String text) {
        TextView textView = mFloatContent.findViewById(R.id.recording_playback_text);
        textView.setText(text);
    }

    private void startJar() {
        if (!mJarStarted) {
            mJarStarted = true;

            Utils.startJar("127.0.0.1", mMasterActivity.getExternalFilesDir(null).getAbsolutePath());
        }
    }

    private class RecordingThread extends Thread {
        RecordingThread() {
            super("RecordingThread");
            Log.d(TAG, "RecordingThread");
        }

        @Override
        public void run() {
            List<String> selfMessage = Utils.calculateDeviceSize();
            Log.i(TAG, "selfMessage:" + selfMessage);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Log.i(TAG, "RecordingThread begin park");
                    LockSupport.park();
                    Log.i(TAG, "RecordingThread after park");

                    BlockingQueue<String> blockingQueue = Utils.getBlockingQueue();
                    AdbStream adbStream = AdbShell.getInstance().executeWithBlockingQueue("getevent -lt", blockingQueue);
                    File file = new File(mRecordingFileName);
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    FileOutputStream outputStream = new FileOutputStream(file);

                    for (String message : selfMessage) {
                        outputStream.write(message.getBytes("UTF-8"));
                        outputStream.write("\n".getBytes());
                    }

                    while (mRecordingWorking) {
                        String event = blockingQueue.take();
                        if (event != null) {
                            Log.i(TAG, "event:" + event);
                            if (!event.startsWith(RECORDING_EXIT)) {
                                outputStream.write(event.getBytes("UTF-8"));
                                outputStream.write("\n".getBytes());
                            }
                        }
                    }

                    adbStream.close();

                    while (!blockingQueue.isEmpty()) {
                        String event = blockingQueue.take();
                        if (event != null) {
                            Log.i(TAG, "event:" + event);
                            if (!event.startsWith(RECORDING_EXIT)) {
                                outputStream.write(event.getBytes("UTF-8"));
                                outputStream.write("\n".getBytes());
                            }
                        }
                    }

                    Log.i(TAG, "Recording End");
                    outputStream.flush();
                    outputStream.close();
                } catch (Exception e) {
                    Log.e(TAG, "Catch Exception:" + e);
                }
            }
        }
    }

    private class PlaybackThread extends Thread {
        public PlaybackThread(String playback) {
            super("PlaybackThread");
            System.out.println("PlaybackThread playback:" + playback);
        }

        @Override
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket()) {
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(Utils.JAR_CHANNEL_PORT));
                Log.i(TAG, "port:" + Utils.JAR_CHANNEL_PORT);

                Socket socket = serverSocket.accept();
                Log.d(TAG, "PlaybackThread accept");

                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                while (!Thread.currentThread().isInterrupted()) {
                    Log.i(TAG, "PlaybackThread begin await");
                    LockSupport.park();
                    Log.i(TAG, "PlaybackThread after await");

                    File playbackFile = new File(mPlaybackFileName);
                    InputStream inputStream = new FileInputStream(playbackFile);
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                    String line;
                    while (mPlaybackWorking && (line = bufferedReader.readLine()) != null) {
                        final String shortName = line.substring(line.lastIndexOf("/") + 1);
                        Utils.runOnUiThread(() -> {
                            RecordingAndPlayBack.this.setText("正在回放：" + shortName);
                        });

                        File caseFile = new File(line);
                        if (!caseFile.exists() || !caseFile.isFile()) {
                            continue;
                        }

                        InputStream is = new FileInputStream(caseFile);
                        InputStreamReader isr = new InputStreamReader(is);
                        BufferedReader br = new BufferedReader(isr);

                        long lastTime = 0;
                        long currentTime = 0;
                        while (mPlaybackWorking && (line = br.readLine()) != null) {
                            if ((currentTime = getEventMicroSecond(line)) > 0) {
                                if (lastTime == 0) {
                                    lastTime = currentTime;
                                } else {
                                    long diff = currentTime - lastTime;
                                    if (diff > 1000) {
                                        Thread.sleep(diff / 1000);
                                        lastTime = currentTime;
                                    }
                                }
                            }

                            byteBuffer.clear();
                            byteBuffer.putInt(line.length());
                            byteBuffer.put(line.getBytes("UTF-8"));
                            byteBuffer.flip();
                            socket.getOutputStream().write(byteBuffer.array(), 0, byteBuffer.limit());
                            socket.getOutputStream().flush();
                        }

                        is.close();
                    }

                    inputStream.close();

                    if (mPlaybackWorking) {
                        mPlaybackWorking = false;
                        Utils.runOnUiThread(() -> {
                            RecordingAndPlayBack.this.setName("回放", R.drawable.ic_playback);
                            RecordingAndPlayBack.this.setText("空闲");
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private long getEventMicroSecond(String line) {
            try {
                if (line.startsWith("[") && line.contains("]")) {
                    String content = line.split("]")[0].trim();
                    content = content.substring(1).replace(".", "");
                    return Long.parseLong(content.trim());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }
    }
}