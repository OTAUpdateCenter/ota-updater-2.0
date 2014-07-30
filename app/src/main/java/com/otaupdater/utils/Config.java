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

package com.otaupdater.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.Date;
import java.util.Locale;

public class Config {
    public static final String LOG_TAG = "OTA::";

    public static final String HTTPC_UA = "OTA Updater App";

    public static final String GPLUS_URL = "https://plus.google.com/102074511541445644953/posts";

    public static final String SITE_BASE_URL = "https://www.otaupdatecenter.pro/";
    public static final String WEB_FEEDBACK_URL = "contact-us";
    public static final String DONATE_URL = "donate";
    public static final String LOGIN_URL = "device/user/login";
    public static final String LOGOUT_URL = "device/user/logout";
    public static final String CODE_REDEEM_URL = "device/user/redeem_code";
    public static final String GCM_REGISTER_URL = "device/register";
    public static final String PING_URL = "device/ping";
    public static final String ROM_PULL_URL = "device/info/rom";
    public static final String KERNEL_PULL_URL = "device/info/kernel";

    public static final String ADMOB_UNIT_ID = "ca-app-pub-0361534644858126/7580389175";

    public static final String HMAC_ALGORITHM = "HmacSHA256";
    public static final String GCM_SENDER_ID = "1068482628480";
    public static final String OAUTH_CLIENT_ID = "1068482628480-jsufug7klk4b4ab2v6f83dtp5q38k74t.apps.googleusercontent.com";

    public static final String PROKEY_SKU = "prokey";

    public static final long MIN_PING_TIME = 604800000; // 1 week in ms

    public static final int ROM_NOTIF_ID = 100;
    public static final int ROM_FAILED_NOTIF_ID = 101;
    public static final int ROM_FLASH_NOTIF_ID = 102;

    public static final int KERNEL_NOTIF_ID = 200;
    public static final int KERNEL_FAILED_NOTIF_ID = 201;
    public static final int KERNEL_FLASH_NOTIF_ID = 202;

    public static final int AD_SHOW_DELAY = 3000;

    public static final String BASE_SD_PATH = "/OTA-Updater/download/";
    public static final String BASE_DL_PATH = PropUtils.getSystemSdPath() + BASE_SD_PATH;
    public static final String ROM_DL_PATH = BASE_DL_PATH + "ROM/";
    public static final String ROM_SD_PATH = BASE_SD_PATH + "ROM/";
    public static final String KERNEL_DL_PATH = BASE_DL_PATH + "kernel/";
    public static final String KERNEL_SD_PATH = BASE_SD_PATH + "kernel/";

    public static final File DL_PATH_FILE = new File(BASE_DL_PATH);
    public static final File ROM_DL_PATH_FILE = new File(ROM_DL_PATH);
    public static final File KERNEL_DL_PATH_FILE = new File(KERNEL_DL_PATH);

    static {
        //noinspection ResultOfMethodCallIgnored
        DL_PATH_FILE.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        ROM_DL_PATH_FILE.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        KERNEL_DL_PATH_FILE.mkdirs();
    }

    private String gcmRegistrationId = null;
    private boolean gcmRegVersionOverride = false;
    private Date lastPingDate = null;

    private String keyPurchaseToken = null;
    private String redeemCode = null;

    private boolean showNotif = true;
    private boolean wifiOnlyDl = true;
    private boolean autoDl = false;

    private boolean ignoredUnsupportedWarn = false;
    private boolean ignoredDataWarn = false;
    private boolean ignoredWifiWarn = false;

    private int lastVersion = -1;
    private String lastDevice = null;
    private String lastRomID = null;
    private String lastKernelID = null;

    private int curVersion = -1;
    private String curDevice = null;
    private String curRomID = null;
    private String curKernelID = null;

    private RomInfo storedRomUpdate = null;
    private KernelInfo storedKernelUpdate = null;

    private long romDownloadID = -1;
    private long kernelDownloadID = -1;

    private String username = null;
    private String hmacKey = null;

    private static final String PREFS_NAME = "prefs";
    private final SharedPreferences PREFS;

    private Config(Context ctx) {
        ctx = ctx.getApplicationContext();
        assert ctx != null;
        PREFS = ctx.getSharedPreferences(PREFS_NAME, 0);

        gcmRegistrationId = PREFS.getString("gcmRegistrationId", gcmRegistrationId);
        lastPingDate = PREFS.contains("lastPingDate") ? new Date(PREFS.getLong("lastPingDate", 0)) : null;

        keyPurchaseToken = PREFS.getString("keyState", keyPurchaseToken);
        redeemCode = PREFS.getString("redeemCode", redeemCode);

        username = PREFS.getString("username", username);
        hmacKey = PREFS.getString("hmacKey", hmacKey);

        showNotif = PREFS.getBoolean("showNotif", showNotif);
        wifiOnlyDl = PREFS.getBoolean("wifiOnlyDl", wifiOnlyDl);
        autoDl = PREFS.getBoolean("autoDl", autoDl);

        ignoredUnsupportedWarn = PREFS.getBoolean("ignoredUnsupportedWarn", ignoredUnsupportedWarn);
        ignoredDataWarn = PREFS.getBoolean("ignoredDataWarn", ignoredDataWarn);
        ignoredWifiWarn = PREFS.getBoolean("ignoredWifiWarn", ignoredWifiWarn);

        if (PREFS.contains("rom_info_name")) {
            if (PropUtils.isRomOtaEnabled()) {
                storedRomUpdate = RomInfo.FACTORY.fromSharedPrefs(PREFS);
            } else {
                clearStoredRomUpdate();
            }
        }

        if (PREFS.contains("kernel_info_name")) {
            if (PropUtils.isKernelOtaEnabled()) {
                storedKernelUpdate = KernelInfo.FACTORY.fromSharedPrefs(PREFS);
            } else {
                clearStoredKernelUpdate();
            }
        }

        lastVersion  = PREFS.getInt("version", lastVersion);
        lastDevice   = PREFS.getString("device", lastDevice);
        lastRomID    = PREFS.getString("rom_id", lastRomID);
        lastKernelID = PREFS.getString("kernel_id", lastKernelID);

        curVersion  = Utils.getAppVersion(ctx);
        curDevice   = android.os.Build.DEVICE.toLowerCase(Locale.US);
        curRomID    = PropUtils.isRomOtaEnabled() ? PropUtils.getRomOtaID() : null;
        curKernelID = PropUtils.isKernelOtaEnabled() ? PropUtils.getKernelOtaID() : null;

        romDownloadID = PREFS.getLong("romDownloadID", romDownloadID);
        kernelDownloadID = PREFS.getLong("kernelDownloadID", kernelDownloadID);
    }
    private static Config instance = null;
    public static synchronized Config getInstance(Context ctx) {
        if (instance == null) instance = new Config(ctx);
        return instance;
    }

    public boolean hasProKey() {
        return keyPurchaseToken != null || redeemCode != null;
    }

    public boolean isKeyRedeemCode() {
        return redeemCode != null;
    }

    public String getGcmRegistrationId() {
        if (gcmRegistrationId == null) return null;
        if (lastVersion != curVersion && !gcmRegVersionOverride) return null;
        return gcmRegistrationId;
    }

    public boolean isGcmRegistered() {
        return gcmRegistrationId != null && (lastVersion == curVersion || gcmRegVersionOverride);
    }

    public void setGcmRegistrationId(String id) {
        this.gcmRegistrationId = id;
        this.gcmRegVersionOverride = true;
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putString("gcmRegistrationId", gcmRegistrationId);
            editor.apply();
        }
    }

    public String getKeyPurchaseToken() {
        return keyPurchaseToken;
    }

    public void setKeyPurchaseToken(String keyPurchaseToken) {
        this.keyPurchaseToken = keyPurchaseToken;
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putString("keyState", keyPurchaseToken);
            editor.apply();
        }
    }

    public String getRedeemCode() {
        return redeemCode;
    }

    public void setRedeemCode(String redeemCode) {
        this.redeemCode = redeemCode;
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putString("redeemCode", redeemCode);
            editor.apply();
        }
    }

    public boolean getShowNotif() {
        return showNotif;
    }

    public void setShowNotif(boolean showNotif) {
        this.showNotif = showNotif;
        putBoolean("showNotif", showNotif);
    }

    public boolean getWifiOnlyDl() {
        return wifiOnlyDl;
    }

    public void setWifiOnlyDl(boolean wifiOnlyDl) {
        this.wifiOnlyDl = wifiOnlyDl;
        putBoolean("wifiOnlyDl", wifiOnlyDl);
    }

    public boolean getAutoDlState() {
        return autoDl;
    }

    public void setAutoDlState(boolean autoDl) {
        this.autoDl = autoDl;
        putBoolean("autoDl", autoDl);
    }

    public void clearIgnored() {
        ignoredUnsupportedWarn = false;
        ignoredDataWarn = false;
        ignoredWifiWarn = false;

        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putBoolean("ignoredUnsupportedWarn", ignoredUnsupportedWarn);
            editor.putBoolean("ignoredDataWarn", ignoredDataWarn);
            editor.putBoolean("ignoredWifiWarn", ignoredWifiWarn);
            editor.apply();
        }
    }

    public boolean getIgnoredUnsupportedWarn() {
        return ignoredUnsupportedWarn;
    }

    public void setIgnoredUnsupportedWarn(boolean ignored) {
        this.ignoredUnsupportedWarn = ignored;
        putBoolean("ignoredUnsupportedWarn", ignored);
    }

    public boolean getIgnoredDataWarn() {
        return ignoredDataWarn;
    }

    public void setIgnoredDataWarn(boolean ignored) {
        this.ignoredDataWarn = ignored;
        putBoolean("ignoredDataWarn", ignored);
    }

    public boolean getIgnoredWifiWarn() {
        return ignoredWifiWarn;
    }

    public void setIgnoredWifiWarn(boolean ignored) {
        this.ignoredWifiWarn = ignored;
        putBoolean("ignoredWifiWarn", ignored);
    }

    public int getLastVersion() {
        return lastVersion;
    }

    public String getLastDevice() {
        return lastDevice;
    }

    public String getLastRomID() {
        return lastRomID;
    }

    public void setValuesToCurrent() {
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putInt("version", curVersion);
            editor.putString("device", curDevice);
            editor.putString("rom_id", curRomID);
            editor.putString("kernel_id", curKernelID);
            editor.apply();
        }
    }

    public boolean upToDate() {
        if (lastDevice == null) return false;

        boolean romIdUpToDate = true;
        if (PropUtils.isRomOtaEnabled()) {
            romIdUpToDate = lastRomID != null && curRomID.equals(lastRomID);
        } else if (lastRomID != null) {
            romIdUpToDate = false;
        }

        boolean kernelIdUpToDate = true;
        if (PropUtils.isKernelOtaEnabled()) {
            kernelIdUpToDate = lastKernelID != null && curKernelID.equals(lastKernelID);
        } else if (lastKernelID != null) {
            kernelIdUpToDate = false;
        }

        return curVersion == lastVersion && curDevice.equals(lastDevice) && romIdUpToDate && kernelIdUpToDate;
    }

    public boolean needPing() {
        return isGcmRegistered() && (lastPingDate == null || (new Date().getTime() - lastPingDate.getTime()) > MIN_PING_TIME);
    }

    public void setPingedCurrent() {
        lastPingDate = new Date();
        putLong("lastPingDate", lastPingDate.getTime());
    }

    public boolean hasStoredRomUpdate() {
        return storedRomUpdate != null;
    }

    public RomInfo getStoredRomUpdate() {
        return storedRomUpdate;
    }

    public void storeRomUpdate(RomInfo info) {
        this.storedRomUpdate = info;
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            info.putToSharedPrefs(editor);
            editor.apply();
        }
    }

    public void clearStoredRomUpdate() {
        storedRomUpdate = null;
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            RomInfo.FACTORY.clearFromSharedPrefs(editor);
            editor.apply();
        }
    }

    public boolean hasStoredKernelUpdate() {
        return storedKernelUpdate != null;
    }

    public KernelInfo getStoredKernelUpdate() {
        return storedKernelUpdate;
    }

    public void storeKernelUpdate(KernelInfo info) {
        this.storedKernelUpdate = info;
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            info.putToSharedPrefs(editor);
            editor.apply();
        }
    }

    public void clearStoredKernelUpdate() {
        storedKernelUpdate = null;
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            KernelInfo.FACTORY.clearFromSharedPrefs(editor);
            editor.apply();
        }
    }

    public boolean isUserLoggedIn() {
        return username != null && hmacKey != null;
    }

    public void storeLogin(String username, String hmacKey) {
        this.username = username;
        this.hmacKey = hmacKey;

        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putString("username", username);
            editor.putString("hmacKey", hmacKey);
            editor.apply();
        }
    }

    public void clearLogin() {
        username = null;
        hmacKey = null;

        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.remove("username");
            editor.remove("hmacKey");
            editor.apply();
        }
    }

    public String getUsername() {
        return username;
    }

    public String getHmacKey() {
        return hmacKey;
    }

    public void storeRomDownloadID(long downloadID) {
        romDownloadID = downloadID;
        putLong("romDownloadID", romDownloadID);
    }

    public long getRomDownloadID() {
        return romDownloadID;
    }

    public boolean isDownloadingRom() {
        return romDownloadID != -1;
    }

    public void clearDownloadingRom() {
        if (romDownloadID != -1) storeRomDownloadID(-1);
    }

    public void storeKernelDownloadID(long downloadID) {
        kernelDownloadID = downloadID;
        putLong("kernelDownloadID", kernelDownloadID);
    }

    public long getKernelDownloadID() {
        return kernelDownloadID;
    }

    public boolean isDownloadingKernel() {
        return kernelDownloadID != -1;
    }

    public void clearDownloadingKernel() {
        if (romDownloadID != -1) storeKernelDownloadID(-1);
    }

    public void storeDownloadID(BaseInfo info, long downloadID) {
        if (info instanceof RomInfo) {
            storeRomDownloadID(downloadID);
        } else if (info instanceof KernelInfo) {
            storeKernelDownloadID(downloadID);
        }
    }

    public void storeUpdate(BaseInfo info) {
        if (info instanceof RomInfo) {
            storeRomUpdate((RomInfo) info);
        } else if (info instanceof KernelInfo) {
            storeKernelUpdate((KernelInfo) info);
        }
    }

    public void clearStoredUpdate(Class<? extends BaseInfo> cls) {
        if (cls.equals(RomInfo.class)) {
            clearStoredRomUpdate();
        } else if (cls.equals(KernelInfo.class)) {
            clearStoredKernelUpdate();
        }
    }

    private void putBoolean(String name, boolean value) {
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putBoolean(name, value);
            editor.apply();
        }
    }

    private void putLong(String name, long value) {
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putLong(name, value);
            editor.apply();
        }
    }
}
