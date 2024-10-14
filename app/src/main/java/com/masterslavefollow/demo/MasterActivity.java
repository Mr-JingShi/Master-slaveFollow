package com.masterslavefollow.demo;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MasterActivity extends AppCompatActivity {
    private static String TAG = "MasterActivity";
    private boolean mIsSingleMachineMode;
    private DrawerLayout mDrawerLayout;
    private View mAppListContainer;
    private View mCaseListContainer;
    private AppAdapter mAppAdapter;
    private CaseAdapter mCaseAdapter;
    private ApplicationInfo mCurrentApp;
    private ImageView mAppIcon;
    private TextView mAppLabel;
    private TextView mAppPkgName;
    private List<ApplicationInfo> mListPack;
    private List<String> mListCases;
    private String mExternalCaseDir;
    private boolean mSupportKeyboard;
    private boolean mSupportNavigation;
    private ScreenEventTracker mScreenEventTracker;
    private RecordingAndPlayBack mRecordingAndPlayBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "MasterActivity onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_master);

        Intent intent = getIntent();
        if (intent != null) {
            mIsSingleMachineMode = intent.getBooleanExtra("isSingleMachineMode", false);

            Log.i(TAG, "isSingleMachineMode:" + mIsSingleMachineMode);
        }

        mDrawerLayout = findViewById(R.id.drawer_layout);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        mDrawerLayout.setScrimColor(Color.TRANSPARENT);
        if (mIsSingleMachineMode) {
            mDrawerLayout.setDrawerListener(new DrawerLayout.DrawerListener() {
                @Override
                public void onDrawerSlide(View drawerView, float slideOffset) {
                }

                @Override
                public void onDrawerOpened(View drawerView) {
                }

                @Override
                public void onDrawerClosed(View drawerView) {
                    Log.i(TAG, "onDrawerClosed");
                    updateCasesView();
                }

                @Override
                public void onDrawerStateChanged(int newState) {
                }
            });
        }
        mAppListContainer = findViewById(R.id.app_list_container);
        mCaseListContainer = findViewById(R.id.case_list_container);

        ListView appListView = findViewById(R.id.app_list);
        appListView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            Log.i(TAG, "setOnItemClickListener position:" + position);

            mDrawerLayout.closeDrawer(mAppListContainer);

            ApplicationInfo app = (ApplicationInfo) mAppAdapter.getItem(position);
            if (!app.packageName.equals(mCurrentApp.packageName)) {
                mCurrentApp = app;

                updateHeadView();
                if (mIsSingleMachineMode) {
                    updateCaseView();
                }

                PrivatePreferences.putString("float_app", mCurrentApp.loadLabel(getPackageManager()) + "##" + mCurrentApp.packageName);
            }
        });

        mListPack = Utils.loadApplicationList();

        String appPackage = "com.autonavi.amapauto";
        String[] appNames = PrivatePreferences.getString("float_app", "").split("##");
        if (appNames.length > 1) {
            appPackage = appNames[1];
        }

        int position = 0;
        if (appPackage != null && !appPackage.isEmpty()) {
            for (int i = 0; i < mListPack.size(); i++) {
                if (appPackage.equals(mListPack.get(i).packageName)) {
                    position = i;
                    break;
                }
            }
        }

        mCurrentApp = mListPack.get(position);

        mAppAdapter = new AppAdapter();
        appListView.setAdapter(mAppAdapter);

        if (mIsSingleMachineMode) {
            updateCaseView();
            mCaseAdapter = new CaseAdapter();
            ListView caseListVIew = findViewById(R.id.case_list);
            caseListVIew.setAdapter(mCaseAdapter);
        }

        mAppIcon = findViewById(R.id.target_app_icon);
        mAppIcon.setOnClickListener((View v) -> {
            mDrawerLayout.openDrawer(mAppListContainer);
        });
        mAppLabel = findViewById(R.id.target_app_label);
        mAppPkgName = findViewById(R.id.test_app_pkgname);
        updateHeadView();

        mSupportKeyboard = PrivatePreferences.getBoolean("support_keyboard", true);
        mSupportNavigation = PrivatePreferences.getBoolean("support_navigation", true);

        RadioButton supportKeyboardButton = findViewById(R.id.support_keyboard);
        RadioButton supportNavigationButton = findViewById(R.id.support_navigation);
        Button lockButton = findViewById(R.id.lock_button);
        Button startButton = findViewById(R.id.start_button);
        TextView wlanAddress = findViewById(R.id.wlan_address);
        TextView slaveStatus = findViewById(R.id.slave_status);
        EditText caseName = findViewById(R.id.case_name);
        TextView caseNames = findViewById(R.id.case_names);
        View recordingPlaybackGroup = findViewById(R.id.recording_playback);

        caseName.setVisibility(View.GONE);
        caseNames.setVisibility(View.GONE);
        if (mIsSingleMachineMode) {
            supportKeyboardButton.setVisibility(View.GONE);
            supportNavigationButton.setVisibility(View.GONE);
            lockButton.setVisibility(View.GONE);
            wlanAddress.setVisibility(View.GONE);
            slaveStatus.setVisibility(View.GONE);
        } else {
            startButton.setEnabled(false);
            recordingPlaybackGroup.setVisibility(View.GONE);
        }

        findViewById(R.id.case_list_confirm).setOnClickListener((View v) -> {
            mDrawerLayout.closeDrawer(mCaseListContainer);
        });

        String ip = Utils.getHostAddress();
        wlanAddress.setText("主机IP:"+ ip);

        slaveStatus.setText("从设备连接数：0-0");

        supportKeyboardButton.setChecked(mSupportKeyboard);
        supportKeyboardButton.setOnClickListener((View v) -> {
            mSupportKeyboard = !mSupportKeyboard;
            supportKeyboardButton.setChecked(mSupportKeyboard);
        });

        supportNavigationButton.setChecked(mSupportNavigation);
        supportNavigationButton.setOnClickListener((View v) -> {
            mSupportNavigation = !mSupportNavigation;
            supportNavigationButton.setChecked(mSupportNavigation);
        });

        RadioButton recordingButton = findViewById(R.id.recording_radio);
        recordingButton.setOnClickListener((View v) -> {
            caseNames.setVisibility(View.GONE);
            caseName.setVisibility(View.VISIBLE);
            caseName.setText("");
        });
        RadioButton playbackButton = findViewById(R.id.playback_radio);
        playbackButton.setOnClickListener((View v) -> {
            caseName.setVisibility(View.GONE);
            mDrawerLayout.openDrawer(mCaseListContainer);
        });

        lockButton.setOnClickListener((View v) -> {
            v.setEnabled(false);
            supportKeyboardButton.setEnabled(false);
            supportNavigationButton.setEnabled(false);
            mAppIcon.setEnabled(false);
            mAppIcon.setBackgroundResource(R.drawable.bg_round_corner_disable);
            startButton.setEnabled(true);

            PrivatePreferences.putBoolean("support_keyboard", mSupportKeyboard);
            PrivatePreferences.putBoolean("support_navigation", mSupportNavigation);

            StringBuilder sb = new StringBuilder();
            sb.append("MASTER_CONFIG ");
            sb.append("KEYBOARD:");
            sb.append(mSupportKeyboard);
            sb.append(";NAVIGATION:");
            sb.append(mSupportNavigation);
            sb.append(";APP:");
            sb.append(mCurrentApp.packageName);

            String config = sb.toString();

            Log.i(TAG, "config:" + config);

            if (mScreenEventTracker == null) {
                mScreenEventTracker = new ScreenEventTracker(() -> {
                    int successCount = mScreenEventTracker.getSuccessCount();
                    int failCount = mScreenEventTracker.getFailCount();
                    slaveStatus.setText("从设备连接数：" + successCount + "-" + failCount);
                });
            }
            mScreenEventTracker.createChannel(config);
        });

        startButton.setOnClickListener((View v) -> {
            if (mIsSingleMachineMode) {
                if (mRecordingAndPlayBack == null) {
                    mRecordingAndPlayBack = new RecordingAndPlayBack(MasterActivity.this);
                }

                boolean isRecordingChecked = recordingButton.isChecked();
                boolean isPlaybackChecked = playbackButton.isChecked();
                Log.i(TAG, "isRecordingChecked:" + isRecordingChecked + " isPlaybackChecked:" + isPlaybackChecked);
                if (isRecordingChecked) {
                    String name = caseName.getText().toString();
                    if (name == null || name.isEmpty()) {
                        Utils.toast(MasterActivity.this, "请先填写用例名称");
                        return;
                    } else {
                        String fileName = mExternalCaseDir + name;
                        Log.d(TAG, "fileName:" + fileName);
                        File caseFile = new File(fileName);
                        if (caseFile.exists()) {
                            Utils.toast(MasterActivity.this, "用例已存在，请重新填写");
                            return;
                        } else {
                            Utils.getBlockingQueue().clear();
                            MyAccessibilityService.open(false);

                            mRecordingAndPlayBack.setRecordingFileName(fileName);
                        }
                    }
                } else if (isPlaybackChecked) {
                    List<String> selectedCases = mCaseAdapter.getSelectedCases();
                    if (selectedCases.isEmpty()) {
                        Utils.toast(MasterActivity.this, "请先选择用例");
                        return;
                    }
                    try {
                        String fileName = mExternalCaseDir + "../playback.txt";
                        File playbackFile = new File(fileName);
                        if (playbackFile.exists()) {
                            playbackFile.delete();
                        }
                        playbackFile.createNewFile();
                        FileOutputStream outputStream = new FileOutputStream(playbackFile);

                        for (String selectedCase : selectedCases) {
                            outputStream.write(selectedCase.getBytes());
                            outputStream.write("\n".getBytes());
                        }
                        outputStream.flush();
                        outputStream.close();

                        mRecordingAndPlayBack.setPlaybackFiles(fileName);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Utils.toast(MasterActivity.this, "请选择录制或回放");
                    return;
                }

                mRecordingAndPlayBack.show();

                mAppIcon.setEnabled(false);
                mAppIcon.setBackgroundResource(R.drawable.bg_round_corner_disable);

                v.setEnabled(false);
            } else {
                if (mScreenEventTracker.getSuccessCount() > 0) {
                    Utils.getBlockingQueue().clear();
                    MyAccessibilityService.open(false);

                    mScreenEventTracker.syncScreenEvent();
                } else {
                    Utils.toast(MasterActivity.this, "请至少一个副设备连接成功后再启动");
                    return;
                }
            }

            Utils.startTargetApp(mCurrentApp.packageName);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            String msg = intent.getStringExtra("from");
            if (msg != null && msg.equals("RecordingAndPlayBack") && mIsSingleMachineMode) {
                updateCaseView();

                mAppIcon.setEnabled(true);
                mAppIcon.setBackgroundResource(0);

                findViewById(R.id.start_button).setEnabled(true);

                findViewById(R.id.case_name).setVisibility(View.GONE);
                findViewById(R.id.case_names).setVisibility(View.GONE);

                RadioGroup radioGroup = findViewById(R.id.recording_playback);
                if (radioGroup.getCheckedRadioButtonId() == R.id.recording_radio) {
                    MyAccessibilityService.close();
                }
                radioGroup.clearCheck();
            }
        }
    }

    @Override
    public void onBackPressed() {
        Log.i(TAG, "onBackPressed");
        if (mDrawerLayout.isDrawerOpen(mAppListContainer)) {
            mDrawerLayout.closeDrawer(mAppListContainer);
        } else if (mDrawerLayout.isDrawerOpen(mCaseListContainer)) {
            mDrawerLayout.closeDrawer(mCaseListContainer);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void updateHeadView() {
        mAppIcon.setImageDrawable(mCurrentApp.loadIcon(getPackageManager()));
        mAppLabel.setText(mCurrentApp.loadLabel(getPackageManager()));
        mAppPkgName.setText(mCurrentApp.packageName);

        Utils.setTargetAppPackageName(mCurrentApp.packageName);

        if (mCaseAdapter != null) {
            mCaseAdapter.getSelectedCases().clear();
        }
    }
    private void updateCaseView() {
        StringBuilder sb = new StringBuilder();
        sb.append(getExternalFilesDir(null).getAbsolutePath());
        sb.append("/cases/");
        sb.append(mCurrentApp.packageName);
        sb.append("/");
        mExternalCaseDir = sb.toString();
        Log.d(TAG, "mExternalCaseDir:" + mExternalCaseDir);
        File caseDirFile = new File(mExternalCaseDir);
        if (!caseDirFile.exists()) {
            caseDirFile.mkdirs();
        }

        mListCases = Utils.getAllFiles(mExternalCaseDir);
        Log.d(TAG, "mListCases:" + mListCases.size());
        if (mCaseAdapter != null) {
            mCaseAdapter.notifyDataSetChanged();
        }
    }

    private void updateCasesView() {
        RadioButton playbackButton = findViewById(R.id.playback_radio);
        if (playbackButton.isChecked()) {
            List<String> selectedCases = mCaseAdapter.getSelectedCases();
            TextView caseNames = findViewById(R.id.case_names);
            if (!selectedCases.isEmpty()) {
                StringBuilder steps = new StringBuilder();
                steps.append("回放步骤：");

                if (caseNames.getVisibility() != View.VISIBLE) {
                    // caseNames.setSelected(true);
                    caseNames.setVisibility(View.VISIBLE);
                }

                for (int i = 0; i < selectedCases.size(); i++) {
                    String name = selectedCases.get(i);
                    steps.append(name.substring(name.lastIndexOf("/") + 1));
                    if (i < selectedCases.size() - 1) {
                        steps.append(" -> ");
                    }
                }

                caseNames.setText(steps);
            } else {
                caseNames.setVisibility(View.GONE);
                RadioGroup radioGroup = findViewById(R.id.recording_playback);
                radioGroup.clearCheck();
            }
        }
    }

    private class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mListPack.size();
        }

        @Override
        public Object getItem(int position) {
            return mListPack.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(MasterActivity.this).inflate(R.layout.item_app_list, parent, false);
                holder = new ViewHolder();
                holder.icon = convertView.findViewById(R.id.app_icon);
                holder.name = convertView.findViewById(R.id.app_name);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ApplicationInfo info = (ApplicationInfo) getItem(position);
            holder.icon.setBackground(info.loadIcon(getPackageManager()));
            holder.name.setText(info.loadLabel(getPackageManager()));

            return convertView;
        }

        class ViewHolder {
            ImageView icon;
            TextView name;
        }
    }

    private class CaseAdapter extends BaseAdapter {
        private List<String> mSelectedCases = new ArrayList<>();
        public List<String> getSelectedCases() {
            return mSelectedCases;
        }
        @Override
        public int getCount() {
            return mListCases.size();
        }

        @Override
        public Object getItem(int position) {
            return mListCases.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(MasterActivity.this).inflate(R.layout.item_case_list, parent, false);
                holder = new ViewHolder();
                holder.radioButton = convertView.findViewById(R.id.case_name);
                holder.radioButton.setSelected(true);
                holder.radioButton.setOnClickListener((View v) -> {
                    holder.isChecked = !holder.isChecked;
                    holder.radioButton.setChecked(holder.isChecked);
                    if (holder.isChecked) {
                        mSelectedCases.add(holder.caseName);
                    } else {
                        mSelectedCases.remove(holder.caseName);
                    }
                });
                holder.radioButton.setOnLongClickListener((View v) -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MasterActivity.this);

                    builder.setTitle("删除用例");
                    builder.setMessage("请选择是否删除用例");

                    builder.setNegativeButton("取消", null);
                    builder.setPositiveButton("删除", (DialogInterface dialog, int which) -> {
                        Log.d(TAG, "delete file name:" + holder.caseName);
                        mListCases.remove(holder.caseName);

                        File file = new File(holder.caseName);
                        file.delete();

                        mSelectedCases.remove(holder.radioButton.getText().toString());
                        notifyDataSetChanged();

                        Utils.toast(MasterActivity.this, "删除用例成功");
                    });
                    builder.show();

                    return true;
                });
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            String caseName = (String)getItem(position);
            holder.caseName = caseName;
            holder.radioButton.setText(caseName.substring(caseName.lastIndexOf("/") + 1));
            if (mSelectedCases.contains(caseName)) {
                holder.isChecked = true;
                holder.radioButton.setChecked(true);
            } else {
                holder.isChecked = false;
                holder.radioButton.setChecked(false);
            }

            return convertView;
        }

        class ViewHolder {
            RadioButton radioButton;
            boolean isChecked = false;
            String caseName;
        }
    }
}
