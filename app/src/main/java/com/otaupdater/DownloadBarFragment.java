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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.otaupdater.utils.BaseInfo;
import com.otaupdater.utils.Config;
import com.otaupdater.utils.DownloadDialogCallback;
import com.otaupdater.utils.DownloadStatus;

import java.lang.ref.WeakReference;

public class DownloadBarFragment extends Fragment {

    private static DownloadBarFragment activeFragment = null;

    private Config cfg;
    private DownloadManager dm;

    private View romContainer;
    private ProgressBar romProgressBar;
    private TextView romProgressText;
    private TextView romStatusText;

    private View kernelContainer;
    private ProgressBar kernelProgressBar;
    private TextView kernelProgressText;
    private TextView kernelStatusText;

    private View romKernelSeperator;
    private View bottomBorder;

    protected static final int REFRESH_DELAY = 1000;
    private final Handler REFRESH_HANDLER = new RefreshHandler(this);

    private static class RefreshHandler extends Handler {
        private final WeakReference<DownloadBarFragment> fragment;

        public RefreshHandler(DownloadBarFragment fragment) {
            this.fragment = new WeakReference<DownloadBarFragment>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            fragment.get().updateStatus();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        cfg = Config.getInstance(activity);
        dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.download_bar, container, false);

        romContainer = view.findViewById(R.id.download_rom_container);
        romProgressBar = (ProgressBar) view.findViewById(R.id.download_rom_progress_bar);
        romProgressText = (TextView) view.findViewById(R.id.download_rom_progress_text);
        romStatusText = (TextView) view.findViewById(R.id.download_rom_status);
        View romCancelButton = view.findViewById(R.id.download_rom_cancel);

        romContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDownloadingDialog(cfg.getRomDownloadID());
            }
        });

        romCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!cfg.isDownloadingRom()) return;
                dm.remove(cfg.getRomDownloadID());
                cfg.clearDownloadingRom();
                updateStatus();
            }
        });

        kernelContainer = view.findViewById(R.id.download_kernel_container);
        kernelProgressBar = (ProgressBar) view.findViewById(R.id.download_kernel_progress_bar);
        kernelProgressText = (TextView) view.findViewById(R.id.download_kernel_progress_text);
        kernelStatusText = (TextView) view.findViewById(R.id.download_kernel_status);
        View kernelCancelButton = view.findViewById(R.id.download_kernel_cancel);

        kernelContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDownloadingDialog(cfg.getKernelDownloadID());
            }
        });

        kernelCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!cfg.isDownloadingKernel()) return;
                dm.remove(cfg.getKernelDownloadID());
                cfg.clearDownloadingKernel();
                updateStatus();
            }
        });

        romKernelSeperator = view.findViewById(R.id.download_rom_kernel_separator);
        bottomBorder = view.findViewById(R.id.download_bottom_border);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatus();
        activeFragment = this;
    }

    @Override
    public void onPause() {
        activeFragment = null;
        REFRESH_HANDLER.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private void updateStatus() {
        DownloadStatus romDlStatus = DownloadStatus.forDownloadID(getActivity(), dm, cfg.getRomDownloadID());
        DownloadStatus kernelDlStatus = DownloadStatus.forDownloadID(getActivity(), dm, cfg.getKernelDownloadID());

        if (romDlStatus == null) cfg.clearDownloadingRom();
        if (kernelDlStatus == null) cfg.clearDownloadingKernel();

        updateViews(romDlStatus, romContainer, romProgressBar, romProgressText, romStatusText);
        updateViews(kernelDlStatus, kernelContainer, kernelProgressBar, kernelProgressText, kernelStatusText);

        if (isActive(romDlStatus) || isActive(kernelDlStatus)) {
            REFRESH_HANDLER.sendMessageDelayed(REFRESH_HANDLER.obtainMessage(), REFRESH_DELAY);
        }

        boolean romVisible = romContainer.getVisibility() == View.VISIBLE;
        boolean kernelVisible = kernelContainer.getVisibility() == View.VISIBLE;

        if (romVisible || kernelVisible) {
            bottomBorder.setVisibility(View.VISIBLE);
        } else {
            bottomBorder.setVisibility(View.GONE);
        }

        if (romVisible && kernelVisible) {
            romKernelSeperator.setVisibility(View.VISIBLE);
        } else {
            romKernelSeperator.setVisibility(View.GONE);
        }
    }

    private void updateViews(DownloadStatus status, View container, ProgressBar progressBar, TextView progressText, TextView statusText) {
        if (status == null) {
            container.setVisibility(View.GONE);
        } else {
            container.setVisibility(View.VISIBLE);

            if (isActive(status)) {
                progressBar.setVisibility(View.VISIBLE);

                if (status.getStatus() == DownloadManager.STATUS_PENDING) {
                    progressText.setVisibility(View.GONE);

                    progressBar.setIndeterminate(true);

                    statusText.setVisibility(View.VISIBLE);
                    statusText.setText(R.string.downloads_starting);
                } else {
                    if (status.getStatus() == DownloadManager.STATUS_PAUSED) {
                        progressText.setVisibility(progressText.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);

                        int pausedStatus = -1;
                        switch (status.getReason()) {
                        case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                            pausedStatus = R.string.downloads_paused_wifi;
                            break;
                        case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                            pausedStatus = R.string.downloads_paused_network;
                            break;
                        case DownloadManager.PAUSED_WAITING_TO_RETRY:
                            pausedStatus = R.string.downloads_paused_retry;
                            break;
                        case DownloadManager.PAUSED_UNKNOWN:
                            pausedStatus = R.string.downloads_paused_unknown;
                            break;
                        }

                        if (pausedStatus == -1) {
                            statusText.setVisibility(View.GONE);
                        } else {
                            statusText.setVisibility(View.VISIBLE);
                            statusText.setText(pausedStatus);
                        }
                    } else {
                        progressText.setVisibility(View.VISIBLE);

                        statusText.setVisibility(View.GONE);
                    }

                    progressText.setText(status.getProgressStr(getActivity()));

                    progressBar.setIndeterminate(false);
                    progressBar.setMax(status.getTotalBytes());
                    progressBar.setProgress(status.getDownloadedBytes());
                }
            } else {
                progressText.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                statusText.setVisibility(View.VISIBLE);

                if (status.getStatus() == DownloadManager.STATUS_SUCCESSFUL) {
                    statusText.setText(R.string.downloads_complete);
                } else {
                    statusText.setText(status.getErrorString(getActivity()));
                }
            }
        }
    }

    private static boolean isActive(DownloadStatus state) {
        return state != null && (
                state.getStatus() == DownloadManager.STATUS_PAUSED ||
                state.getStatus() == DownloadManager.STATUS_RUNNING ||
                state.getStatus() == DownloadManager.STATUS_PENDING
        );
    }

    public static void notifyActiveFragment() {
        if (activeFragment != null) activeFragment.updateStatus();
    }

    private Dialog showDownloadingDialog(long downloadID) {
        return showDownloadingDialog(getActivity(), downloadID,
                getActivity() instanceof DownloadDialogCallback ? (DownloadDialogCallback) getActivity() : null);
    }

    public static Dialog showDownloadingDialog(final Context ctx, final long downloadID, final DownloadDialogCallback callback) {
        final DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);

        DownloadStatus initStatus = DownloadStatus.forDownloadID(ctx, dm, downloadID);
        if (initStatus == null) return null;

        LayoutInflater inflater = LayoutInflater.from(ctx);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.download_dialog, null);

        TextView titleView = (TextView) view.findViewById(R.id.download_dlg_title);
        TextView changelogView = (TextView) view.findViewById(R.id.download_dlg_changelog);

        final BaseInfo info = initStatus.getInfo();
        titleView.setText(ctx.getString(info.getDownloadingTitle(), info.name, info.version));
        changelogView.setText(info.changelog);

        final TextView progressText = (TextView) view.findViewById(R.id.download_dlg_progress_text);
        final TextView statusText = (TextView) view.findViewById(R.id.download_dlg_status);
        final ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.download_dlg_progress_bar);

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(R.string.alert_downloading);
        builder.setView(view);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.hide, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DownloadStatus status = DownloadStatus.forDownloadID(ctx, dm, downloadID);
                if (status == null) return;

                if (status.getStatus() == DownloadManager.STATUS_RUNNING) {
                    Toast.makeText(ctx, R.string.toast_downloading, Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        final AlertDialog dlg = builder.create();

        final Handler REFRESH_HANDLER = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                DownloadStatus status = DownloadStatus.forDownloadID(ctx, dm, downloadID);

                if (status == null) {
                    dlg.dismiss();
                    return;
                }

                if (isActive(status)) {
                    dlg.getButton(DialogInterface.BUTTON_NEGATIVE).setText(android.R.string.cancel);
                    progressBar.setVisibility(View.VISIBLE);

                    if (status.getStatus() == DownloadManager.STATUS_PENDING) {
                        progressText.setVisibility(View.GONE);

                        progressBar.setIndeterminate(true);

                        statusText.setVisibility(View.VISIBLE);
                        statusText.setText(R.string.downloads_starting);
                    } else {
                        if (status.getStatus() == DownloadManager.STATUS_PAUSED) {
                            progressText.setVisibility(progressText.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);

                            int pausedStatus = -1;
                            switch (status.getReason()) {
                            case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                                pausedStatus = R.string.downloads_paused_wifi;
                                break;
                            case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                                pausedStatus = R.string.downloads_paused_network;
                                break;
                            case DownloadManager.PAUSED_WAITING_TO_RETRY:
                                pausedStatus = R.string.downloads_paused_retry;
                                break;
                            case DownloadManager.PAUSED_UNKNOWN:
                                pausedStatus = R.string.downloads_paused_unknown;
                                break;
                            }

                            if (pausedStatus == -1) {
                                statusText.setVisibility(View.GONE);
                            } else {
                                statusText.setVisibility(View.VISIBLE);
                                statusText.setText(pausedStatus);
                            }
                        } else {
                            progressText.setVisibility(View.VISIBLE);

                            statusText.setVisibility(View.GONE);
                        }

                        progressText.setText(status.getProgressStr(ctx));

                        progressBar.setIndeterminate(false);
                        progressBar.setMax(status.getTotalBytes());
                        progressBar.setProgress(status.getDownloadedBytes());
                    }
                } else {
                    progressText.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    statusText.setVisibility(View.VISIBLE);

                    if (status.isSuccessful()) {
                        dlg.getButton(DialogInterface.BUTTON_NEGATIVE).setText(R.string.flash);
                        statusText.setText(R.string.downloads_complete);
                    } else {
                        dlg.getButton(DialogInterface.BUTTON_NEGATIVE).setText(R.string.retry);
                        statusText.setText(status.getErrorString(ctx));
                    }
                }

                this.sendMessageDelayed(this.obtainMessage(), DownloadBarFragment.REFRESH_DELAY);
            }
        };

        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                dlg.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DownloadStatus status = DownloadStatus.forDownloadID(ctx, dm, downloadID);

                        if (isActive(status)) {
                            dlg.dismiss();
                            dm.remove(downloadID);
                        } else if (status.getStatus() == DownloadManager.STATUS_SUCCESSFUL) {
                            Intent i = new Intent(ctx, DownloadsActivity.class);
                            i.setAction(info.getFlashAction());
                            info.addToIntent(i);
                            ctx.startActivity(i);
                        } else if (status.getStatus() == DownloadManager.STATUS_FAILED) {
                            dlg.dismiss();
                            info.downloadFileDialog(ctx, callback);
                        }
                    }
                });

                REFRESH_HANDLER.sendMessage(REFRESH_HANDLER.obtainMessage());
                if (callback != null) {
                    callback.onDialogShown(dlg);
                    callback.onDownloadDialogShown(downloadID, dlg);
                }
            }
        });
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                REFRESH_HANDLER.removeCallbacksAndMessages(null);
                if (callback != null) {
                    callback.onDialogClosed(dlg);
                    callback.onDownloadDialogClosed(downloadID, dlg);
                }
            }
        });
        dlg.show();

        return dlg;
    }
}
