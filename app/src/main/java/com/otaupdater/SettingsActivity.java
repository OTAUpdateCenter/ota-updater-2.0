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
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;
import com.otaupdater.utils.APIUtils;
import com.otaupdater.utils.Config;
import com.otaupdater.utils.DialogCallback;
import com.otaupdater.utils.UserUtils;
import com.otaupdater.utils.UserUtils.LoginCallback;
import com.otaupdater.utils.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class SettingsActivity extends PreferenceActivity implements DialogCallback {
    public static final String EXTRA_SHOW_GET_PROKEY_DLG = "show_get_prokey";
    public static final String EXTRA_SHOW_ACCOUNT_DLG = "show_account_dlg";

    private static final int PROKEY_REQ_CODE = 1111;

    private final ArrayList<Dialog> dlgs = new ArrayList<Dialog>();

    private Config cfg;

    private Preference accountPref;
    private CheckBoxPreference notifPref;
    private CheckBoxPreference wifidlPref;
    private CheckBoxPreference autodlPref;
    private Preference resetWarnPref;
    private Preference prokeyPref;
    private Preference donatePref;

    private IInAppBillingService service;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IInAppBillingService.Stub.asInterface(binder);

            try {
                Bundle owned = service.getPurchases(3, getPackageName(), "inapp", null);
                ArrayList<String> ownedItems = owned.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
                ArrayList<String> ownedItemData = owned.getStringArrayList("INAPP_PURCHASE_DATA_LIST");

                if (ownedItems != null && ownedItemData != null) {
                    if (cfg.hasProKey()) {
                        if (!cfg.isKeyRedeemCode()) {
                            for (int q = 0; q < ownedItems.size(); q++) {
                                if (ownedItems.get(q).equals(Config.PROKEY_SKU)) {
                                    JSONObject itemData = new JSONObject(ownedItemData.get(q));
                                    if (cfg.getKeyPurchaseToken().equals(itemData.getString("purchaseToken"))) {
                                        cfg.setKeyPurchaseToken(null);
                                        updateProKeySummary();
                                    }
                                    break;
                                }
                            }
                        }
                    } else {
                        for (int q = 0; q < ownedItems.size(); q++) {
                            if (ownedItems.get(q).equals(Config.PROKEY_SKU)) {
                                JSONObject itemData = new JSONObject(ownedItemData.get(q));
                                cfg.setKeyPurchaseToken(itemData.getString("purchaseToken"));
                                updateProKeySummary();
                                break;
                            }
                        }
                    }
                }
            } catch (RemoteException ignored) {
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    @SuppressWarnings("deprecation")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        cfg = Config.getInstance(getApplicationContext());

        addPreferencesFromResource(R.xml.settings);

        accountPref = findPreference("account_pref");
        if (cfg.isUserLoggedIn()) {
            accountPref.setSummary(getString(R.string.settings_account_summary_loggedin, cfg.getUsername()));
        } else {
            accountPref.setSummary(R.string.settings_account_summary);
        }

        notifPref = (CheckBoxPreference) findPreference("notif_pref");
        notifPref.setChecked(cfg.getShowNotif());

        wifidlPref = (CheckBoxPreference) findPreference("wifidl_pref");
        wifidlPref.setChecked(cfg.getWifiOnlyDl());

        autodlPref = (CheckBoxPreference) findPreference("autodl_pref");
        autodlPref.setChecked(cfg.getAutoDlState());

        prokeyPref = findPreference("prokey_pref");
        if (cfg.hasProKey()) {
            prokeyPref.setTitle(R.string.settings_prokey_title_pro);
        }

        updateProKeySummary();

        resetWarnPref = findPreference("resetwarn_pref");
        donatePref = findPreference("donate_pref");

        bindService(new Intent("com.android.vending.billing.InAppBillingService.BIND"), serviceConn, Context.BIND_AUTO_CREATE);

        if (EXTRA_SHOW_GET_PROKEY_DLG.equals(getIntent().getAction())) {
            showGetProKeyDialog();
        }

        if (EXTRA_SHOW_ACCOUNT_DLG.equals(getIntent().getAction())) {
            showAccountDialog();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_SHOW_GET_PROKEY_DLG, false)) {
            showGetProKeyDialog();
        }
    }

    @Override
    public void onDestroy() {
        unbindService(serviceConn);
        super.onDestroy();
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

    @Override
    @SuppressWarnings("deprecation")
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == accountPref) {
            showAccountDialog();
        } else if (preference == notifPref) {
            cfg.setShowNotif(notifPref.isChecked());
        } else if (preference == wifidlPref) {
            cfg.setWifiOnlyDl(wifidlPref.isChecked());
        } else if (preference == autodlPref) {
            if (cfg.hasProKey()) {
                cfg.setAutoDlState(autodlPref.isChecked());
            } else {
                Utils.showProKeyOnlyFeatureDialog(this, this);
                cfg.setAutoDlState(false);
                autodlPref.setChecked(false);
            }
        } else if (preference == resetWarnPref) {
            cfg.clearIgnored();
        } else if (preference == prokeyPref) {
            if (cfg.hasProKey()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                if (cfg.isKeyRedeemCode()) {
                    builder.setMessage(R.string.prokey_redeemed_thanks);
                } else {
                    builder.setMessage(R.string.prokey_thanks);
                }

                builder.setNeutralButton(R.string.close, new DialogInterface.OnClickListener() {
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
            } else {
                showGetProKeyDialog();
            }
        } else if (preference == donatePref) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Config.SITE_BASE_URL + Config.DONATE_URL)));
        } else {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        return true;
    }

    private void showAccountDialog() {
        if (cfg.isUserLoggedIn()) {
            UserUtils.showAccountDialog(this, this);
        } else {
            UserUtils.showLoginDialog(this, null, this, null);
        }
    }

    private void showGetProKeyDialog() {
        if (cfg.hasProKey()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.settings_prokey_title);
        final boolean playServices = Utils.checkPlayServices(this);
        builder.setItems(playServices ? R.array.prokey_ops : R.array.prokey_ops_nomarket, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                which -= playServices ? 1 : 0;
                switch (which) {
                case -1:
                    try {
                        Bundle buyIntentBundle = service.getBuyIntent(3, getPackageName(), Config.PROKEY_SKU, "inapp", null);
                        PendingIntent buyIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                        if (buyIntent != null) startIntentSenderForResult(buyIntent.getIntentSender(), PROKEY_REQ_CODE, new Intent(), 0, 0, 0);
                    } catch (Exception e) {
                        Toast.makeText(SettingsActivity.this, R.string.prokey_error_init, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case 0:
                    redeemProKey();
                    break;
//                case 1:
//                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Config.SITE_BASE_URL + Config.DONATE_URL)));
//                    break;
                }
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PROKEY_REQ_CODE) {
            int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
            String purchaseDataStr = data.getStringExtra("INAPP_PURCHASE_DATA");

            //TODO need moar verification!

            if (resultCode == RESULT_OK) {
                if (responseCode == 0) {
                    try {
                        JSONObject purchaseData = new JSONObject(purchaseDataStr);
                        if (purchaseData.getString("productId").equals(Config.PROKEY_SKU)) {
                            cfg.setKeyPurchaseToken(purchaseData.getString("purchaseToken"));
                            updateProKeySummary();
                            Toast.makeText(this, R.string.prokey_success, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, R.string.prokey_error_other, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, R.string.prokey_error_other, Toast.LENGTH_SHORT).show();
                    }
                } else if (responseCode == 1) {
                    Toast.makeText(this, R.string.prokey_error_cancelled, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.prokey_error_other, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, R.string.prokey_error_other, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void redeemProKey() {
        if (cfg.isUserLoggedIn()) {
            APIUtils.redeemCode(this, new APIUtils.ProgressDialogAPICallback(this, getString(R.string.settings_prokey_redeeming), this) {
                @Override
                public void onSuccess(String message, JSONObject respObj) {
                    try {
                        String proKey = respObj.getString("prokey");
                        cfg.setRedeemCode(proKey);
                        Log.d(Config.LOG_TAG + "Redeem", "redeemed, code=" + proKey);
                        SettingsActivity.this.updateProKeySummary();
                    } catch (JSONException ignored) { }
                }

                @Override
                public void onError(String message, JSONObject respObj) {
                    Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            UserUtils.showLoginDialog(this, null, this, new LoginCallback() {
                @Override
                public void onLoggedIn(String username) {
                    SettingsActivity.this.redeemProKey();
                }

                @Override
                public void onError(String error) {
                    new AlertDialog.Builder(SettingsActivity.this)
                            .setMessage(error)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dialogInterface, int i) { }
                            })
                            .show();
                }

                @Override
                public void onCancel() {
                }
            });
        }
    }

    private void updateProKeySummary() {
        if (prokeyPref == null) return;

        if (cfg.hasProKey()) {
            if (cfg.isKeyRedeemCode()) {
                prokeyPref.setSummary(getString(R.string.settings_prokey_summary_redeemed, cfg.getRedeemCode()));
            } else {
                prokeyPref.setSummary(R.string.settings_prokey_summary_pro);
            }
        } else if (!Utils.checkPlayServices(getApplicationContext())) {
            prokeyPref.setSummary(R.string.settings_prokey_summary_nomarket);
        }
    }

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
