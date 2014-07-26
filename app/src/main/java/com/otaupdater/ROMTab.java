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

import android.app.Activity;
import android.app.ListFragment;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.otaupdater.utils.APIUtils;
import com.otaupdater.utils.BaseInfo;
import com.otaupdater.utils.Config;
import com.otaupdater.utils.DownloadDialogCallback;
import com.otaupdater.utils.PropUtils;
import com.otaupdater.utils.RomInfo;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class ROMTab extends ListFragment {
    protected static final String KEY_TITLE = "title";
    protected static final String KEY_SUMMARY = "summary";
    protected static final String KEY_ICON = "icon";

    private final ArrayList<HashMap<String, Object>> DATA = new ArrayList<HashMap<String, Object>>();
    private /*final*/ int AVAIL_UPDATES_IDX = -1;
    private /*final*/ SimpleAdapter adapter;

    private Config cfg;
    private boolean fetching = false;

    private static ROMTab activeFragment = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cfg = Config.getInstance(getActivity().getApplicationContext());

        HashMap<String, Object> item;

        item = new HashMap<String, Object>();
        item.put(KEY_TITLE, getString(R.string.main_device));
        item.put(KEY_SUMMARY, android.os.Build.DEVICE.toLowerCase(Locale.US));
        item.put(KEY_ICON, R.drawable.ic_device);
        DATA.add(item);

        item = new HashMap<String, Object>();
        item.put(KEY_TITLE, getString(R.string.main_rom));
        item.put(KEY_SUMMARY, android.os.Build.DISPLAY);
        item.put(KEY_ICON, R.drawable.ic_info_outline);
        DATA.add(item);

        String romVersion = PropUtils.getRomOtaVersion();
        if (romVersion == null) romVersion = PropUtils.getRomVersion();

        if (PropUtils.isRomOtaEnabled()) {
            Date romDate = PropUtils.getRomOtaDate();
            if (romDate != null) {
                romVersion += " (" + DateFormat.getDateTimeInstance().format(romDate) + ")";
            }

            item = new HashMap<String, Object>();
            item.put(KEY_TITLE, getString(R.string.rom_version));
            item.put(KEY_SUMMARY, romVersion);
            item.put(KEY_ICON, R.drawable.ic_settings);
            DATA.add(item);

            item = new HashMap<String, Object>();
            item.put(KEY_TITLE, getString(R.string.main_otaid));
            item.put(KEY_SUMMARY, PropUtils.getRomOtaID());
            item.put(KEY_ICON, R.drawable.ic_key);
            DATA.add(item);

            item = new HashMap<String, Object>();
            item.put(KEY_TITLE, getString(R.string.updates_avail_title));
            if (cfg.hasStoredRomUpdate()) {
                RomInfo info = cfg.getStoredRomUpdate();
                if (info.isUpdate()) {
                    item.put(KEY_SUMMARY, getString(R.string.updates_new, info.name, info.version));
                } else {
                    item.put(KEY_SUMMARY, getString(R.string.updates_none));
                    cfg.clearStoredRomUpdate();
                }
            } else {
                item.put(KEY_SUMMARY, getString(R.string.updates_none));
            }
            item.put(KEY_ICON, R.drawable.ic_cloud_download);
            AVAIL_UPDATES_IDX = DATA.size();
            DATA.add(item);
        } else {
            if (cfg.hasStoredRomUpdate()) cfg.clearStoredRomUpdate();

            if (!romVersion.equals(Build.DISPLAY)) {
                item = new HashMap<String, Object>();
                item.put(KEY_TITLE, getString(R.string.rom_version));
                item.put(KEY_SUMMARY, romVersion);
                item.put(KEY_ICON, R.drawable.ic_settings);
                DATA.add(item);
            }

            item = new HashMap<String, Object>();
            item.put(KEY_TITLE, getString(R.string.rom_unsupported));
            item.put(KEY_SUMMARY, getString(R.string.rom_unsupported_summary));
            item.put(KEY_ICON, R.drawable.ic_cloud_off);
            DATA.add(item);
        }
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.list, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        adapter = new SimpleAdapter(getActivity(),
                DATA,
                R.layout.two_line_icon_list_item,
                new String[] { KEY_TITLE, KEY_SUMMARY, KEY_ICON },
                new int[] { android.R.id.text1, android.R.id.text2, android.R.id.icon });
        setListAdapter(adapter);
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
        super.onPause();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (position == AVAIL_UPDATES_IDX) {
            if (cfg.hasStoredRomUpdate()) {
                RomInfo info = cfg.getStoredRomUpdate();
                if (info.isUpdate()) {
                    Activity act = getActivity();
                    info.showUpdateDialog(act, act instanceof DownloadDialogCallback ? (DownloadDialogCallback) act : null);
                } else {
                    cfg.clearStoredRomUpdate();
                    setUpdateSummary(R.string.updates_none);

                    if (!fetching) {
                        checkForRomUpdates();
                    }
                }
            } else if (!fetching) {
                checkForRomUpdates();
            }
        }
    }

    public void updateStatus() {
        updateStatus(cfg.getStoredRomUpdate());
    }

    public void updateStatus(RomInfo info) {
        if (AVAIL_UPDATES_IDX == -1) return;

        Activity act = getActivity();
        if (info != null && info.isUpdate()) {
            setUpdateSummary(getString(R.string.updates_new, info.name, info.version));
            if (act instanceof OTAUpdaterActivity) ((OTAUpdaterActivity) act).updateRomTabIcon(true);
        } else {
            if (act instanceof OTAUpdaterActivity) ((OTAUpdaterActivity) act).updateRomTabIcon(false);
            setUpdateSummary(R.string.updates_none);
            Toast.makeText(act, R.string.rom_toast_no_update, Toast.LENGTH_SHORT).show();
        }
    }

    private void setUpdateSummary(int resId) {
        setUpdateSummary(getString(resId));
    }

    private void setUpdateSummary(String string) {
        if (AVAIL_UPDATES_IDX == -1) return;

        DATA.get(AVAIL_UPDATES_IDX).put(KEY_SUMMARY, string);
        adapter.notifyDataSetChanged();
    }

    private void checkForRomUpdates() {
        if (fetching) return;
        if (!PropUtils.isRomOtaEnabled()) return;

        APIUtils.fetchRomInfo(getActivity(), new BaseInfo.InfoLoadAdapter<RomInfo>(RomInfo.class, getActivity()) {
            @Override
            public void onStart(APIUtils.APITask task) {
                fetching = true;
                setUpdateSummary(R.string.updates_checking);
            }

            @Override
            public void onInfoLoaded(RomInfo info) {
                updateStatus(info);
                Activity act = getActivity();
                if (info.isUpdate()) info.showUpdateDialog(act, act instanceof DownloadDialogCallback ? (DownloadDialogCallback) act : null);
            }

            @Override
            public void onError(String message, JSONObject respObj) {
                setUpdateSummary(getString(R.string.update_fetch_error, message));
                Toast.makeText(getActivity(), message == null || message.isEmpty() ? getString(R.string.toast_update_fetch_error) : message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete(boolean success) {
                fetching = false;
            }
        });
    }

    public static void notifyActiveFragment() {
        if (activeFragment != null) activeFragment.updateStatus();
    }
}
