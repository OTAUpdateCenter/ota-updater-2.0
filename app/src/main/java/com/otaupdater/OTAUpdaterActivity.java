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
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.vending.billing.IInAppBillingService;
import com.otaupdater.utils.BaseDownloadDialogActivity;
import com.otaupdater.utils.Config;
import com.otaupdater.utils.KernelInfo;
import com.otaupdater.utils.PropUtils;
import com.otaupdater.utils.RomInfo;
import com.otaupdater.utils.Utils;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class OTAUpdaterActivity extends BaseDownloadDialogActivity {
    public static final String ROM_NOTIF_ACTION = "com.otaupdater.action.ROM_NOTIF_ACTION";
    public static final String KERNEL_NOTIF_ACTION = "com.otaupdater.action.KERNEL_NOTIF_ACTION";

    public static final String EXTRA_FLAG_DOWNLOAD_DIALOG = "SHOW_DOWNLOAD_DIALOG";

    public static final String KEY_TAB = "tab";
    private int romTabIdx = 0;
    private int kernelTabIdx = 0;

    private Config cfg;

    private ActionBar bar;

    private ServiceConnection billingSrvConn = null;

    private final Handler adsHandler = new AdsHandler(this);
    private static class AdsHandler extends Handler {
        private final WeakReference<OTAUpdaterActivity> act;

        public AdsHandler(OTAUpdaterActivity act) {
            this.act = new WeakReference<OTAUpdaterActivity>(act);
        }

        @Override
        public void handleMessage(Message msg) {
            OTAUpdaterActivity act = this.act.get();
            if (act == null) return;
            Fragment adFragment = act.getFragmentManager().findFragmentById(R.id.ads);
            if (adFragment != null) act.getFragmentManager().beginTransaction().show(adFragment).commit();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Context context = getApplicationContext();
        cfg = Config.getInstance(context);

        if (!cfg.hasProKey()) {
            bindService(new Intent("com.android.vending.billing.InAppBillingService.BIND"),
                    billingSrvConn = new ServiceConnection() {
                        @Override
                        public void onServiceDisconnected(ComponentName name) {
                            billingSrvConn = null;
                        }

                        @Override
                        public void onServiceConnected(ComponentName name, IBinder binder) {
                            IInAppBillingService service = IInAppBillingService.Stub.asInterface(binder);

                            try {
                                Bundle owned = service.getPurchases(3, getPackageName(), "inapp", null);
                                ArrayList<String> ownedItems = owned.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
                                ArrayList<String> ownedItemData = owned.getStringArrayList("INAPP_PURCHASE_DATA_LIST");

                                if (ownedItems != null && ownedItemData != null) {
                                    for (int q = 0; q < ownedItems.size(); q++) {
                                        if (ownedItems.get(q).equals(Config.PROKEY_SKU)) {
                                            JSONObject itemData = new JSONObject(ownedItemData.get(q));
                                            cfg.setKeyPurchaseToken(itemData.getString("purchaseToken"));
                                            break;
                                        }
                                    }
                                }
                            } catch (RemoteException ignored) {
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            unbindService(this);
                            billingSrvConn = null;
                        }
                    }, Context.BIND_AUTO_CREATE);
        }

        boolean data = Utils.dataAvailable(this);
        boolean wifi = Utils.wifiConnected(this);

        if (!data || !wifi) {
            final boolean nodata = !data && !wifi;

            if ((nodata && !cfg.getIgnoredDataWarn()) || (!nodata && !cfg.getIgnoredWifiWarn())) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(nodata ? R.string.alert_nodata_title : R.string.alert_nowifi_title);
                builder.setMessage(nodata ? R.string.alert_nodata_message : R.string.alert_nowifi_message);
                builder.setCancelable(false);
                builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                });
                builder.setNeutralButton(R.string.alert_wifi_settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
                        startActivity(i);
                    }
                });
                builder.setPositiveButton(R.string.ignore, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (nodata) {
                            cfg.setIgnoredDataWarn(true);
                        } else {
                            cfg.setIgnoredWifiWarn(true);
                        }
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
        }

        if (PropUtils.isRomOtaEnabled() || PropUtils.isKernelOtaEnabled()) {
            Utils.registerForUpdates(this);
        } else {
            Utils.unregisterForUpdates(this);

            if (!cfg.getIgnoredUnsupportedWarn()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.alert_unsupported_title);
                builder.setMessage(R.string.alert_unsupported_message);
                builder.setCancelable(false);
                builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                });
                builder.setPositiveButton(R.string.ignore, new DialogInterface.OnClickListener() {
    				@Override
    				public void onClick(DialogInterface dialog, int which) {
    				    cfg.setIgnoredUnsupportedWarn(true);
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
        }

        CheckinReceiver.setDailyAlarm(this);

        setContentView(R.layout.main);

        Fragment adFragment = getFragmentManager().findFragmentById(R.id.ads);
        if (adFragment != null) getFragmentManager().beginTransaction().hide(adFragment).commit();

        ViewPager mViewPager = (ViewPager) findViewById(R.id.pager);

        bar = getActionBar();
        assert bar != null;

        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE, ActionBar.DISPLAY_SHOW_TITLE);
        bar.setTitle(R.string.app_name);

        TabsAdapter mTabsAdapter = new TabsAdapter(this, mViewPager);
        mTabsAdapter.addTab(bar.newTab().setText(R.string.main_about), AboutTab.class);

        ActionBar.Tab romTab = bar.newTab().setText(R.string.main_rom);
        if (cfg.hasStoredRomUpdate()) romTab.setIcon(R.drawable.ic_action_warning);
        romTabIdx = mTabsAdapter.addTab(romTab, ROMTab.class);

        ActionBar.Tab kernelTab = bar.newTab().setText(R.string.main_kernel);
        if (cfg.hasStoredKernelUpdate()) kernelTab.setIcon(R.drawable.ic_action_warning);
        kernelTabIdx = mTabsAdapter.addTab(kernelTab, KernelTab.class);

        if (!handleNotifAction(getIntent())) {
            if (cfg.hasStoredRomUpdate() && !cfg.isDownloadingRom()) {
                cfg.getStoredRomUpdate().showUpdateNotif(this);
            }

            if (cfg.hasStoredKernelUpdate() && !cfg.isDownloadingKernel()) {
                cfg.getStoredKernelUpdate().showUpdateNotif(this);
            }

            if (savedInstanceState != null) {
                bar.setSelectedNavigationItem(savedInstanceState.getInt(KEY_TAB, 0));
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNotifAction(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        adsHandler.sendMessageDelayed(adsHandler.obtainMessage(), Config.AD_SHOW_DELAY);
    }

    @Override
    protected void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TAB, bar.getSelectedNavigationIndex());
    }

    @Override
    protected void onPause() {
        super.onPause();
        adsHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        if (billingSrvConn != null) unbindService(billingSrvConn);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.actionbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i;
        switch (item.getItemId()) {
        case R.id.settings:
            i = new Intent(this, SettingsActivity.class);
            startActivity(i);
            break;
        case R.id.downloads:
            i = new Intent(this, DownloadsActivity.class);
            startActivity(i);
            break;
        case R.id.accounts:
            i = new Intent(this, SettingsActivity.class);
            i.setAction(SettingsActivity.EXTRA_SHOW_ACCOUNT_DLG);
            startActivity(i);
            break;
        default:
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    public void updateRomTabIcon(boolean update) {
        if (update) {
            bar.getTabAt(romTabIdx).setIcon(R.drawable.ic_action_warning);
        } else {
            bar.getTabAt(romTabIdx).setIcon(null);
        }
    }

    public void updateKernelTabIcon(boolean update) {
        if (update) {
            bar.getTabAt(kernelTabIdx).setIcon(R.drawable.ic_action_warning);
        } else {
            bar.getTabAt(kernelTabIdx).setIcon(null);
        }
    }

    private boolean handleNotifAction(Intent intent) {
        String action = intent.getAction();
        if (ROM_NOTIF_ACTION.equals(action)) {
            RomInfo.FACTORY.clearUpdateNotif(this);
            bar.setSelectedNavigationItem(romTabIdx);

            if (intent.getBooleanExtra(EXTRA_FLAG_DOWNLOAD_DIALOG, false)) {
                DownloadBarFragment.showDownloadingDialog(this, cfg.getRomDownloadID(), this);
            } else {
                RomInfo info = RomInfo.FACTORY.fromIntent(intent);
                if (info == null) info = cfg.getStoredRomUpdate();
                if (info != null) info.showUpdateDialog(this, this);
            }
        } else if (KERNEL_NOTIF_ACTION.equals(action)) {
            KernelInfo.FACTORY.clearUpdateNotif(this);
            bar.setSelectedNavigationItem(kernelTabIdx);

            if (intent.getBooleanExtra(EXTRA_FLAG_DOWNLOAD_DIALOG, false)) {
                DownloadBarFragment.showDownloadingDialog(this, cfg.getKernelDownloadID(), this);
            } else {
                KernelInfo info = KernelInfo.FACTORY.fromIntent(intent);
                if (info == null) info = cfg.getStoredKernelUpdate();
                if (info != null) info.showUpdateDialog(this, this);
            }
        } else {
            return false;
        }
        return true;
    }

    public static class TabsAdapter extends FragmentPagerAdapter
            implements ActionBar.TabListener, ViewPager.OnPageChangeListener {

        private final Context ctx;
        private final ActionBar mActionBar;
        private final ViewPager mViewPager;
        private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

        static final class TabInfo {
            private final Class<?> clss;

            TabInfo(Class<?> _class) {
                clss = _class;
            }
        }

        public TabsAdapter(Activity activity, ViewPager pager) {
            super(activity.getFragmentManager());
            ctx = activity;
            mActionBar = activity.getActionBar();
            mViewPager = pager;
            mViewPager.setAdapter(this);
            mViewPager.setOnPageChangeListener(this);
        }

        public int addTab(ActionBar.Tab tab, Class<?> clss) {
            TabInfo info = new TabInfo(clss);
            tab.setTag(info);
            tab.setTabListener(this);
            mTabs.add(info);
            mActionBar.addTab(tab);
            notifyDataSetChanged();

            return mTabs.size() - 1;
        }

        @Override
        public int getCount() {
            return mTabs.size();
        }

        @Override
        public Fragment getItem(int position) {
            TabInfo info = mTabs.get(position);
            return Fragment.instantiate(ctx, info.clss.getName());
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            mActionBar.setSelectedNavigationItem(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }

        @Override
        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
            Object tag = tab.getTag();
            for (int i=0; i<mTabs.size(); i++) {
                if (mTabs.get(i) == tag) {
                    mViewPager.setCurrentItem(i);
                }
            }
        }

        @Override
        public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
        }

        @Override
        public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
        }
    }
}
