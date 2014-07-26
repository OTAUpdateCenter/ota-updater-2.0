package com.otaupdater.utils;

import android.app.Activity;
import android.app.Dialog;

import java.util.ArrayList;

public abstract class BaseDialogActivity extends Activity implements DialogCallback {
    private final ArrayList<Dialog> dlgs = new ArrayList<Dialog>();

    @Override
    protected void onPause() {
        super.onPause();

        for (Dialog dlg : dlgs) {
            if (dlg.isShowing()) dlg.dismiss();
        }
        dlgs.clear();
    }

    @Override
    public void onDialogShown(Dialog dlg) {
        dlgs.add(dlg);
    }

    @Override
    public void onDialogClosed(Dialog dlg) {
        dlgs.remove(dlg);
    }
}
