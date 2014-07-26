package com.otaupdater.utils;

import android.app.Dialog;
import android.os.Bundle;

import com.otaupdater.DownloadBarFragment;

import org.jetbrains.annotations.NotNull;

public abstract class BaseDownloadDialogActivity extends BaseDialogActivity implements DownloadDialogCallback {
    private static final String KEY_DOWNLOAD_ID = "downloadID";

    protected Long dialogDownloadID = null;

    @Override
    protected void onRestoreInstanceState(@NotNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState.containsKey(KEY_DOWNLOAD_ID)) {
            DownloadBarFragment.showDownloadingDialog(this, savedInstanceState.getLong(KEY_DOWNLOAD_ID, -1), this);
        }
    }

    @Override
    public void onDownloadDialogShown(long dlID, Dialog dlg) {
        dialogDownloadID = dlID;
    }

    @Override
    public void onDownloadDialogClosed(long dlID, Dialog dlg) {
        dialogDownloadID = null;
    }

    @Override
    protected void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (dialogDownloadID != null) outState.putLong(KEY_DOWNLOAD_ID, dialogDownloadID);
    }
}
