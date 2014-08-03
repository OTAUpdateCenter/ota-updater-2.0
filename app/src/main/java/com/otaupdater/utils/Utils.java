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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.otaupdater.R;
import com.otaupdater.SettingsActivity;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Utils {
//    protected static final int REQUEST_CODE_RECOVER_PLAY_SERVICES = 1001;
    private static final Random random = new SecureRandom();
    private static final SimpleDateFormat OTA_DATE = new SimpleDateFormat("yyyyMMdd-kkmm", Locale.US);

    private Utils() { }

    public static int getAppVersion(Context ctx) {
        try {
            PackageInfo packageInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (Exception e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    public static String md5(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());

            return byteArrToStr(digest.digest());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private static final HashMap<File, String> MD5_FILE_CACHE = new HashMap<File, String>();
    public static String md5(File f) {
        if (!f.exists()) return "";
        if (MD5_FILE_CACHE.containsKey(f)) {
            String cachedMD5 = MD5_FILE_CACHE.get(f);
            int cachedMD5Split = cachedMD5.indexOf(':');
            long lastModified = Long.parseLong(cachedMD5.substring(cachedMD5Split + 1));
            if (lastModified == f.lastModified()) {
                return cachedMD5.substring(0, cachedMD5Split);
            } else {
                MD5_FILE_CACHE.remove(f);
            }
        }

        InputStream in = null;
        try {
            in = new FileInputStream(f);

            MessageDigest digest = MessageDigest.getInstance("MD5");

            byte[] buf = new byte[4096];
            int nRead;
            while ((nRead = in.read(buf)) != -1) {
                digest.update(buf, 0, nRead);
            }

            String md5 = byteArrToStr(digest.digest());
            MD5_FILE_CACHE.put(f, md5 + ":" + Long.toString(f.lastModified()));
            return md5;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try { in.close(); }
                catch (IOException ignored) { }
            }
        }
        return "";
    }

    public static String hmac(String str, String key) {
        try {
            Mac mac = Mac.getInstance(Config.HMAC_ALGORITHM);
            String salt = randomSaltString(mac.getMacLength());
            mac.init(new SecretKeySpec(key.getBytes(), mac.getAlgorithm()));
            return byteArrToStr(mac.doFinal((salt + str + salt).getBytes("UTF-8"))) + salt;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void toastWrapper(final Activity activity, final CharSequence text, final int duration) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                Toast.makeText(activity, text, duration).show();
            }
        });
    }

    public static void toastWrapper(final Activity activity, final int resId, final int duration) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                Toast.makeText(activity, resId, duration).show();
            }
        });
    }

    public static void toastWrapper(final View view, final CharSequence text, final int duration) {
        view.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(view.getContext(), text, duration).show();
            }
        });
    }

    public static void toastWrapper(final View view, final int resId, final int duration) {
        view.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(view.getContext(), resId, duration).show();
            }
        });
    }

    public static boolean checkPlayServices(Context ctx) {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(ctx);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                Log.v(Config.LOG_TAG + "checkPlayServices", "Play Services error: " + GooglePlayServicesUtil.getErrorString(resultCode));
//                if (ctx instanceof Activity) {
//                    GooglePlayServicesUtil.getErrorDialog(resultCode, (Activity) ctx, REQUEST_CODE_RECOVER_PLAY_SERVICES).show();
//                }
            } else {
                Log.v(Config.LOG_TAG + "checkPlayServices", "Device not supported");
            }
            return false;
        }
        return true;
    }

    public static boolean dataAvailable(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    public static boolean wifiConnected(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected() && ni.getType() == ConnectivityManager.TYPE_WIFI;
    }

    public static Date parseDate(String date) {
        if (date == null) return null;
        try {
            return OTA_DATE.parse(date);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String formatDate(Date date) {
        if (date == null) return null;
        return OTA_DATE.format(date);
    }

    public static boolean registerForUpdates(Context ctx) {
        if (!checkPlayServices(ctx)) return false;
        gcmRegister(ctx);
        return true;
    }

    public static void unregisterForUpdates(final Context ctx) {
        if (!checkPlayServices(ctx)) return;

        Log.v(Config.LOG_TAG + "GCMRegister", "updating GCM reg infos (unregister)");
        APIUtils.unregisterGCM(ctx, null);
    }

    public static void gcmRegister(final Context ctx) {
        final Config cfg = Config.getInstance(ctx);
        String regId = cfg.getGcmRegistrationId();

        if (regId == null) {
            Log.v(Config.LOG_TAG + "GCMRegister", "Not registered, registering...");
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(ctx);
                        String regID = gcm.register(Config.GCM_SENDER_ID);

                        cfg.setGcmRegistrationId(regID);
                        Log.v(Config.LOG_TAG + "GCMRegister", "GCM registered");

                        updateGCMRegistration(ctx);
                    } catch (Exception ex) {
                        Log.e(Config.LOG_TAG + "GCMRegister", "Error registering GCM: " + ex.getMessage());
                    }
                    return null;
                }
            }.execute();
        } else if (!cfg.upToDate()) {
            Log.v(Config.LOG_TAG + "GCMRegister", "Already registered, out-of-date");
            cfg.setValuesToCurrent();
            updateGCMRegistration(ctx);
        } else if (cfg.needPing()) {
            Log.v(Config.LOG_TAG + "GCMRegister", "Already registered, need to ping");
            APIUtils.doPing(ctx, new APIUtils.APIAdapter() {
                @Override
                public void onSuccess(String message, JSONObject respObj) {
                    cfg.setPingedCurrent();
                }
            });
        } else {
            Log.v(Config.LOG_TAG + "GCMRegister", "Already registered, no ping necessary");
        }
    }

    protected static void updateGCMRegistration(final Context ctx) {
        final Config cfg = Config.getInstance(ctx);

        APIUtils.updateGCMRegistration(ctx, new APIUtils.APIAdapter() {
            @Override
            public void onSuccess(String message, JSONObject respObj) {
                cfg.setPingedCurrent();

                if (PropUtils.isRomOtaEnabled()) {
                    RomInfo info = RomInfo.FACTORY.fromJSON(respObj.optJSONObject(RomInfo.KEY_NAME));
                    if (info != null && info.isUpdate()) {
                        cfg.storeRomUpdate(info);
                        if (cfg.getShowNotif()) {
                            info.showUpdateNotif(ctx);
                        } else {
                            Log.v(Config.LOG_TAG + "GCMRegister", "got rom update response, notif not shown");
                        }
                    } else {
                        cfg.clearStoredRomUpdate();
                        RomInfo.FACTORY.clearUpdateNotif(ctx);
                    }
                }

                if (PropUtils.isKernelOtaEnabled()) {
                    KernelInfo info = KernelInfo.FACTORY.fromJSON(respObj.optJSONObject(KernelInfo.KEY_NAME));
                    if (info != null && info.isUpdate()) {
                        cfg.storeKernelUpdate(info);
                        if (cfg.getShowNotif()) {
                            info.showUpdateNotif(ctx);
                        } else {
                            Log.v(Config.LOG_TAG + "GCMRegister", "got kernel update response, notif not shown");
                        }
                    } else {
                        cfg.clearStoredKernelUpdate();
                        KernelInfo.FACTORY.clearUpdateNotif(ctx);
                    }
                }
            }

            @Override
            public void onError(String message, JSONObject respObj) {
                cfg.setGcmRegistrationId(null); //TODO maybe do something better?
                Log.w(Config.LOG_TAG + "GCMRegister", "error registering with server: " + message);
            }
        });
    }

    private static String device = null;
    public static String getDevice() {
        if (device != null) return device;

        device = Build.DEVICE.toLowerCase(Locale.US);

        return device;
    }

    private static String deviceID = null;
    public static String getDeviceID(Context ctx) {
        if (deviceID != null) return deviceID;

        deviceID = ((TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
        if (deviceID == null) {
            WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            if (wm.isWifiEnabled()) {
                deviceID = wm.getConnectionInfo().getMacAddress();
            } else {
                //fallback to ANDROID_ID - gets reset on data wipe, but it's better than nothing
                deviceID = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            }
        }
        deviceID = md5(deviceID);

        return deviceID;
    }

    private static String deviceName = null;
    public static String getDeviceName(Context ctx) {
        if (deviceName != null) return deviceName;

        deviceName = ((TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE)).getNetworkOperatorName();
        if (deviceName == null || deviceName.isEmpty()) deviceName = "Wi-Fi";

        deviceName += " " + Build.MODEL;

        return deviceName.trim();
    }

    public static String sanitizeName(String name) {
        if (name == null) return "";

        name = Normalizer.normalize(name, Normalizer.Form.NFD);
        name = name.trim();
        name = name.replaceAll("[^\\p{ASCII}]","");
        name = name.replaceAll("[ _-]+", "_");
        name = name.replaceAll("(^_|_$)", "");
        name = name.toLowerCase(Locale.US);

        return name;
    }

    public static void showProKeyOnlyFeatureDialog(final Context ctx, final DialogCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(R.string.prokey_only_feature_title);
        builder.setMessage(R.string.prokey_only_feature_message);
        builder.setPositiveButton(R.string.prokey_only_get, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Intent i = new Intent(ctx, SettingsActivity.class);
                i.setAction(SettingsActivity.EXTRA_SHOW_GET_PROKEY_DLG);
                i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                ctx.startActivity(i);
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
                if (callback != null) callback.onDialogShown(dlg);
            }
        });
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (callback != null) callback.onDialogClosed(dlg);
            }
        });
        dlg.show();
    }

    private static final char[] HEX_DIGITS = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
    public static String byteArrToStr(byte[] bytes) {
        StringBuilder str = new StringBuilder();
        for (byte b : bytes) {
            str.append(HEX_DIGITS[(0xF0 & b) >>> 4]);
            str.append(HEX_DIGITS[0xF & b]);
        }
        return str.toString();
    }

    public static byte[] randomSalt(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return b;
    }

    public static String randomSaltString(int bytes) {
        return byteArrToStr(randomSalt(bytes));
    }
}
