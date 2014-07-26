/*
 * Copyright (C) 2014 OTA Update Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.otaupdater;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.otaupdater.utils.BaseDownloadDialogActivity;
import com.otaupdater.utils.BaseInfo;
import com.otaupdater.utils.Config;
import com.otaupdater.utils.KernelInfo;
import com.otaupdater.utils.PropUtils;
import com.otaupdater.utils.RomInfo;

import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DownloadsActivity extends BaseDownloadDialogActivity implements ActionBar.OnNavigationListener {
    public static final String FLASH_ROM_ACTION = "com.otaupdater.action.FLASH_ROM_ACTION";
    public static final String FLASH_KERNEL_ACTION = "com.otaupdater.action.FLASH_KERNEL_ACTION";

    public static final String EXTRA_FLASH_INFO = "flash_info";

    public static final String EXTRA_GOTO_TYPE = "goto_type";
    public static final int GOTO_TYPE_ROM = 0;
    public static final int GOTO_TYPE_KERNEL = 1;

    private final ArrayList<Dialog> dlgs = new ArrayList<Dialog>();

    private DownloadListFragment dlFragment = null;

    private final Handler adsHandler = new AdsHandler(this);
    private ActionBar bar;

    private static class AdsHandler extends Handler {
        private final WeakReference<DownloadsActivity> act;

        public AdsHandler(DownloadsActivity act) {
            this.act = new WeakReference<DownloadsActivity>(act);
        }

        @Override
        public void handleMessage(Message msg) {
            Fragment adFragment = act.get().getFragmentManager().findFragmentById(R.id.ads);
            if (adFragment != null) act.get().getFragmentManager().beginTransaction().show(adFragment).commit();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String extState = Environment.getExternalStorageState();
        if (!extState.equals(Environment.MEDIA_MOUNTED) && !extState.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            Toast.makeText(this, extState.equals(Environment.MEDIA_SHARED) ? R.string.toast_nosd_shared : R.string.toast_nosd_error, Toast.LENGTH_LONG).show();
            finish();
        }

        setContentView(R.layout.downloads);

        dlFragment = (DownloadListFragment) getFragmentManager().findFragmentById(R.id.download_list);

        Fragment adFragment = getFragmentManager().findFragmentById(R.id.ads);
        if (adFragment != null) getFragmentManager().beginTransaction().hide(adFragment).commit();

        bar = getActionBar();
        assert bar != null;

        bar.setDisplayHomeAsUpEnabled(true);
        bar.setDisplayShowTitleEnabled(false);
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        bar.setListNavigationCallbacks(ArrayAdapter.createFromResource(this, R.array.download_types, android.R.layout.simple_spinner_dropdown_item), this);

        int state = -1;
        String action = getIntent().getAction();
        if (action != null) {
            if (action.equals(FLASH_ROM_ACTION)) {
                state = GOTO_TYPE_ROM;
                showFlashDialog(RomInfo.FACTORY.fromIntent(getIntent()));
            } else if (action.equals(FLASH_KERNEL_ACTION)) {
                state = GOTO_TYPE_KERNEL;
                showFlashDialog(KernelInfo.FACTORY.fromIntent(getIntent()));
            } else {
                state = getIntent().getIntExtra(EXTRA_GOTO_TYPE, state);
            }
        }

        if (savedInstanceState != null) {
            if (state == -1) state = savedInstanceState.getInt("state", dlFragment.getState());
        }
        bar.setSelectedNavigationItem(state);
    }

    @Override
    protected void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("state", bar.getSelectedNavigationIndex());
    }

    @Override
    protected void onResume() {
        super.onResume();
        adsHandler.sendMessageDelayed(adsHandler.obtainMessage(), Config.AD_SHOW_DELAY);
    }

    @Override
    protected void onPause() {
        for (Dialog dlg : dlgs) {
            if (dlg.isShowing()) dlg.dismiss();
        }
        dlgs.clear();
        super.onPause();

    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        dlFragment.setState(itemPosition);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return false;
    }

    protected void showFlashDialog(final BaseInfo info) {
        if (PropUtils.getNoFlash()) { //can't flash programmatically, must flash manually
            showNoFlashDialog(info.getDownloadFileName());
        }

        String[] installOpts = getResources().getStringArray(R.array.install_options);
        final boolean[] selectedOpts = new boolean[installOpts.length];
        selectedOpts[selectedOpts.length - 1] = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.alert_install_title);
        builder.setMultiChoiceItems(installOpts, selectedOpts, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                selectedOpts[which] = isChecked;
            }
        });
        builder.setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                AlertDialog.Builder builder = new AlertDialog.Builder(DownloadsActivity.this);
                builder.setTitle(R.string.alert_install_title);
                builder.setMessage(R.string.alert_install_message);
                builder.setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        flashFiles(new String[] { info.getRecoveryFilePath() }, selectedOpts[0], selectedOpts[2], selectedOpts[1]);
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                builder.show();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        final AlertDialog dlg = builder.create();

        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                onDialogShown(dlg);
            }
        });
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                onDialogClosed(dlg);
            }
        });
        dlg.show();
    }

    private void showNoFlashDialog(String file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.alert_install_title);
        builder.setMessage(getString(R.string.alert_noinstall_message, file));
        builder.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        final AlertDialog dlg = builder.create();

        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                onDialogShown(dlg);
            }
        });
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                onDialogClosed(dlg);
            }
        });
        dlg.show();
    }

    private void flashFiles(String[] files, boolean backup, boolean wipeCache, boolean wipeData) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("mkdir -p /cache/recovery/\n");
            os.writeBytes("rm -f /cache/recovery/command\n");
            os.writeBytes("rm -f /cache/recovery/extendedcommand\n");
            os.writeBytes("echo 'boot-recovery' >> /cache/recovery/command\n");

            //no official cwm for sony, so use extendedcommand. sony devices cannot use regular command file
            if (Build.MANUFACTURER.toLowerCase(Locale.US).contains("sony")) {
                if (backup) {
                    os.writeBytes("echo 'backup_rom /sdcard/clockworkmod/backup/ota_" +
                        new SimpleDateFormat("yyyy-MM-dd_HH.mm", Locale.US).format(new Date()) +
                        "' >> /cache/recovery/extendedcommand\n");
                }
                if (wipeData) {
                    os.writeBytes("echo 'format(\"/data\");' >> /cache/recovery/extendedcommand\n");
                }
                if (wipeCache) {
                    os.writeBytes("echo 'format(\"/cache\");' >> /cache/recovery/extendedcommand\n");
                }

                for (String file : files) {
                    os.writeBytes("echo 'install_zip(\"" + file + "\");' >> /cache/recovery/extendedcommand\n");
                }
            } else {
                if (backup) {
                    os.writeBytes("echo '--nandroid' >> /cache/recovery/command\n");
                }
                if (wipeData) {
                    os.writeBytes("echo '--wipe_data' >> /cache/recovery/command\n");
                }
                if (wipeCache) {
                    os.writeBytes("echo '--wipe_cache' >> /cache/recovery/command\n");
                }

                for (String file: files) {
                    os.writeBytes("echo '--update_package=" + file + "' >> /cache/recovery/command\n");
                }
            }

            String rebootCmd = PropUtils.getRebootCmd();
            if (!rebootCmd.equals("$$NULL$$")) {
                os.writeBytes("sync\n");
                if (rebootCmd.endsWith(".sh")) {
                    os.writeBytes("sh " + rebootCmd + "\n");
                } else {
                    os.writeBytes(rebootCmd + "\n");
                }
            }

            os.writeBytes("sync\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
            ((PowerManager) getSystemService(POWER_SERVICE)).reboot("recovery");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
