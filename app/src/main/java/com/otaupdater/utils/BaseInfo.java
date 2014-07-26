package com.otaupdater.utils;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.otaupdater.DownloadBarFragment;
import com.otaupdater.DownloadReceiver;
import com.otaupdater.OTAUpdaterActivity;
import com.otaupdater.R;

import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;
import java.util.Date;

public abstract class BaseInfo implements Parcelable, Serializable {
    private static final long serialVersionUID = 7138464743643950748L;

    public static final String KEY_NAME = "name";
    public static final String KEY_VERSION = "version";
    public static final String KEY_CHANGELOG = "changelog";
    public static final String KEY_URL = "url";
    public static final String KEY_MD5 = "md5";
    public static final String KEY_DATE = "date";

    public String name;
    public String version;
    public String changelog;
    public String url;
    public String md5;
    public Date date;

    protected BaseInfo() {
    }

    public BaseInfo(String name, String version, String changelog, String downurl, String md5, Date date) {
        this.name = name;
        this.version = version;
        this.changelog = changelog;
        this.url = downurl;
        this.md5 = md5;
        this.date = date;
    }

    public void addToIntent(Intent i) {
        i.putExtra(getNameKey(), name);
        i.putExtra(KEY_VERSION, version);
        i.putExtra(KEY_CHANGELOG, changelog);
        i.putExtra(KEY_URL, url);
        i.putExtra(KEY_MD5, md5);
        i.putExtra(KEY_DATE, Utils.formatDate(date));
    }

    public void putToSharedPrefs(SharedPreferences.Editor editor) {
        editor.putString(getNameKey() + "_info_" + KEY_NAME, name);
        editor.putString(getNameKey() + "_info_" + KEY_VERSION, version);
        editor.putString(getNameKey() + "_info_" + KEY_CHANGELOG, changelog);
        editor.putString(getNameKey() + "_info_" + KEY_URL, url);
        editor.putString(getNameKey() + "_info_" + KEY_MD5, md5);
        editor.putString(getNameKey() + "_info_" + KEY_DATE, Utils.formatDate(date));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(version);
        dest.writeString(changelog);
        dest.writeString(url);
        dest.writeString(md5);
        dest.writeLong(date.getTime());
    }

    public boolean isUpdate() {
        if (date != null) {
            Date propDate = getPropDate();
            if (propDate == null) return true;
            if (date.after(propDate)) return true;
        } else if (version != null) {
            String propVersion = getPropVersion();
            if (propVersion == null) return true;
            if (!version.equalsIgnoreCase(propVersion)) return true;
        }
        return false;
    }

    public void showUpdateNotif(Context ctx) {
        if (isDownloading(ctx)) return;

        Intent mainIntent = new Intent(ctx, OTAUpdaterActivity.class);
        mainIntent.setAction(getNotifAction());
        this.addToIntent(mainIntent);
        PendingIntent mainPIntent = PendingIntent.getActivity(ctx, 0, mainIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        Intent dlInent = new Intent(ctx, DownloadReceiver.class);
        dlInent.setAction(getDownloadAction());
        this.addToIntent(dlInent);
        PendingIntent dlPIntent = PendingIntent.getBroadcast(ctx, 0, dlInent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx);
        builder.setContentIntent(mainPIntent);
        builder.setContentTitle(ctx.getString(R.string.notif_source));
        builder.setContentText(ctx.getString(getNotifTickerStr()));
        builder.setTicker(ctx.getString(getNotifTextStr(), version));
        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.drawable.ic_stat_system_update);
        builder.setStyle(new NotificationCompat.BigTextStyle().bigText(ctx.getString(getNotifDetailsStr(), version, changelog)));
        builder.setPriority(NotificationCompat.PRIORITY_LOW);
        builder.addAction(R.drawable.ic_action_av_download, ctx.getString(R.string.download), dlPIntent);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(getNotifID(), builder.build());
    }

    public void clearUpdateNotif(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(getNotifID());
    }

    public long startDownload(Context ctx) {
        Config cfg = Config.getInstance(ctx);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.addRequestHeader("User-Agent", Config.HTTPC_UA);
        request.setTitle(ctx.getString(getDownloadingNotifTitle()));
        request.setDestinationUri(getDownloadFileUri());
        request.setAllowedOverRoaming(false);
        request.setVisibleInDownloadsUi(false);

        int allowedNetworks = DownloadManager.Request.NETWORK_WIFI;
        if (!cfg.getWifiOnlyDl()) allowedNetworks |= DownloadManager.Request.NETWORK_MOBILE;
        request.setAllowedNetworkTypes(allowedNetworks);

//        if (Build.VERSION.SDK_INT >= 16) {
//            request.setAllowedOverMetered(false);
//        }

        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadID = dm.enqueue(request);
        cfg.storeDownloadID(this, downloadID);
        DownloadBarFragment.notifyActiveFragment();
        clearUpdateNotif(ctx);

        return downloadID;
    }

    public void downloadFileDialog(final Context ctx, final DownloadDialogCallback callback) {
        DownloadBarFragment.showDownloadingDialog(ctx, startDownload(ctx), callback);
    }

    public String getDownloadFileName() {
        return Utils.sanitizeName(name) + "__" + Utils.sanitizeName(version) + ".zip";
    }

    public File getDownloadFile() {
        return new File(getDownloadPathFile(), getDownloadFileName());
    }

    public int checkDownloadedFile() {
        File file = getDownloadFile();
        if (!file.exists()) return DownloadManager.ERROR_FILE_ERROR;
        if (!Utils.md5(file).equalsIgnoreCase(md5)) return DownloadStatus.ERROR_MD5_MISMATCH;
        return 0;
    }

    public Uri getDownloadFileUri() {
        return Uri.parse("file://" + getDownloadFile().getAbsolutePath());
    }

    public String getRecoveryFilePath() {
        return PropUtils.getRecoverySdPath() + getDownloadSdPath() + getDownloadFileName();
    }

    public void showUpdateDialog(final Context ctx, final DownloadDialogCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(R.string.alert_update_title);
        builder.setMessage(ctx.getString(getDownloadDialogMessageStr(), name, version));

        builder.setPositiveButton(R.string.download, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
                downloadFileDialog(ctx, callback);
            }
        });

        if (changelog.length() != 0) {
            builder.setNeutralButton(R.string.alert_changelog, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    showChangelogDialog(ctx, callback);
                }
            });
        }

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

    public void showChangelogDialog(final Context ctx, final DownloadDialogCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(ctx.getString(R.string.alert_changelog_title, version));
        builder.setMessage(changelog);

        builder.setPositiveButton(R.string.download, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
                downloadFileDialog(ctx, callback);
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

    public abstract String getFlashAction();

    public abstract String getNotifAction();

    public abstract int getDownloadingTitle();

    public abstract int getDownloadFailedTitle();

    public abstract int getDownloadDoneTitle();

    public abstract int getFailedNotifID();

    public abstract int getFlashNotifID();

    protected abstract String getNameKey();

    public abstract String getDownloadAction();

    protected abstract String getDownloadSdPath();

    protected abstract File getDownloadPathFile();

    protected abstract int getNotifTickerStr();

    protected abstract int getNotifTextStr();

    protected abstract int getNotifDetailsStr();

    protected abstract int getNotifID();

    protected abstract int getDownloadingNotifTitle();

    protected abstract int getDownloadDialogMessageStr();

    protected abstract boolean isDownloading(Context ctx);

    protected abstract Date getPropDate();

    protected abstract String getPropVersion();

    public static class InfoFactory<T extends BaseInfo> {
        private final Class<T> CLASS;

        public InfoFactory(Class<T> cls) {
            this.CLASS = cls;
        }

        public T fromJSON(JSONObject json) {
            if (json == null || json.length() == 0) return null;

            try {
                T info = CLASS.newInstance();

                info.name = json.getString(info.getNameKey());
                info.version = json.getString(KEY_VERSION);
                info.changelog = json.getString(KEY_CHANGELOG);
                info.url = json.getString(KEY_URL);
                info.md5 = json.getString(KEY_MD5);
                info.date = Utils.parseDate(json.getString(KEY_DATE));

                return info;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        public T fromBundle(Bundle bundle) {
            if (bundle == null || bundle.isEmpty()) return null;

            try {
                T info = CLASS.newInstance();

                info.name = bundle.getString(info.getNameKey());
                info.version = bundle.getString(KEY_VERSION);
                info.changelog = bundle.getString(KEY_CHANGELOG);
                info.url = bundle.getString(KEY_URL);
                info.md5 = bundle.getString(KEY_MD5);
                info.date = Utils.parseDate(bundle.getString(KEY_DATE));

                return info;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        public T fromIntent(Intent i) {
            return fromBundle(i.getExtras());
        }

        public T fromSharedPrefs(SharedPreferences prefs) {
            try {
                T info = CLASS.newInstance();

                info.name = prefs.getString(info.getNameKey() + "_info_" + KEY_NAME, null);
                info.version = prefs.getString(info.getNameKey() + "_info_" + KEY_VERSION, null);
                info.changelog = prefs.getString(info.getNameKey() + "_info_" + KEY_CHANGELOG, null);
                info.url = prefs.getString(info.getNameKey() + "_info_" + KEY_URL, null);
                info.md5 = prefs.getString(info.getNameKey() + "_info_" + KEY_MD5, null);
                info.date = Utils.parseDate(prefs.getString(info.getNameKey() + "_info_" + KEY_DATE, null));

                return info;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        public Creator<T> getParcelableCreator() {
            return new Creator<T>() {
                @Override
                @SuppressWarnings("unchecked")
                public T[] newArray(int size) {
                    return (T[]) new BaseInfo[size];
                }

                @Override
                public T createFromParcel(Parcel source) {
                    try {
                        T info = CLASS.newInstance();

                        info.name = source.readString();
                        info.version = source.readString();
                        info.changelog = source.readString();
                        info.url = source.readString();
                        info.md5 = source.readString();
                        info.date = new Date(source.readLong());

                        return info;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return null;
                }
            };
        }

        public void clearUpdateNotif(Context ctx) {
            try {
                CLASS.newInstance().clearUpdateNotif(ctx);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void clearFromSharedPrefs(SharedPreferences.Editor editor) {
            try {
                T info = CLASS.newInstance();

                editor.remove(info.getNameKey() + "_info_" + KEY_NAME);
                editor.remove(info.getNameKey() + "_info_" + KEY_VERSION);
                editor.remove(info.getNameKey() + "_info_" + KEY_CHANGELOG);
                editor.remove(info.getNameKey() + "_info_" + KEY_URL);
                editor.remove(info.getNameKey() + "_info_" + KEY_MD5);
                editor.remove(info.getNameKey() + "_info_" + KEY_DATE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static abstract class InfoLoadAdapter<T extends BaseInfo> extends APIUtils.APIAdapter {
        private final Class<T> CLASS;
        private final Context ctx;
        private final Config cfg;

        public InfoLoadAdapter(Class<T> cls, Context ctx) {
            this.CLASS = cls;
            this.ctx = ctx;
            this.cfg = Config.getInstance(ctx);
        }

        public abstract void onInfoLoaded(T info);

        @Override
        public void onSuccess(String message, JSONObject respObj) {
            InfoFactory<T> factory = new InfoFactory<T>(CLASS);
            T info = factory.fromJSON(respObj);

            if (info != null && info.isUpdate()) {
                cfg.storeUpdate(info);
                if (cfg.getShowNotif()) {
                    info.showUpdateNotif(ctx);
                } else {
                    Log.v(Config.LOG_TAG + "InfoLoad", "found " + info.getNameKey() + " update, notif not shown");
                }
            } else {
                cfg.clearStoredUpdate(CLASS);
                factory.clearUpdateNotif(ctx);
            }

            onInfoLoaded(info);
        }
    }
}
