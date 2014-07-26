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

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import com.otaupdater.utils.BaseInfo;
import com.otaupdater.utils.DownloadStatus;
import com.otaupdater.utils.KernelInfo;
import com.otaupdater.utils.RomInfo;

public class DownloadReceiver extends BroadcastReceiver {
    public static final String DL_ROM_ACTION = "com.otaupdater.action.DL_ROM_ACTION";
    public static final String DL_KERNEL_ACTION = "com.otaupdater.action.DL_KERNEL_ACTION";

    public static final String CLEAR_DL_ACTION = "com.otaupdater.action.CLEAR_DL_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (action.equals(DL_ROM_ACTION)) {
            RomInfo.FACTORY.clearUpdateNotif(context);
            RomInfo.FACTORY.fromIntent(intent).startDownload(context);
        } else if (action.equals(DL_KERNEL_ACTION)) {
            KernelInfo.FACTORY.clearUpdateNotif(context);
            KernelInfo.FACTORY.fromIntent(intent).startDownload(context);
        } else if (action.equals(CLEAR_DL_ACTION)) {
            if (intent.hasExtra(DownloadManager.EXTRA_DOWNLOAD_ID)) {
                DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                dm.remove(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1));
                DownloadBarFragment.notifyActiveFragment();
            }
        } else if (action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
            DownloadStatus status = DownloadStatus.forDownloadID(context, intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1));
            if (status == null) return;

            BaseInfo info = status.getInfo();
            if (info == null) return;

            int error = status.getStatus() == DownloadManager.STATUS_SUCCESSFUL ? info.checkDownloadedFile() : status.getReason();

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (error == 0) {
                Intent mainIntent = new Intent(context, OTAUpdaterActivity.class);
                mainIntent.setAction(info.getNotifAction());
                mainIntent.putExtra(OTAUpdaterActivity.EXTRA_FLAG_DOWNLOAD_DIALOG, true);
                PendingIntent mainPIntent = PendingIntent.getActivity(context, 0, mainIntent, PendingIntent.FLAG_CANCEL_CURRENT);

                Intent flashIntent = new Intent(context, DownloadsActivity.class);
                flashIntent.setAction(info.getFlashAction());
                info.addToIntent(flashIntent);
                PendingIntent flashPIntent = PendingIntent.getActivity(context, 0, flashIntent, PendingIntent.FLAG_CANCEL_CURRENT);

                Notification notif = new NotificationCompat.Builder(context)
                        .setTicker(context.getString(info.getDownloadDoneTitle()))
                        .setContentTitle(context.getString(info.getDownloadDoneTitle()))
                        .setSmallIcon(R.drawable.ic_stat_av_download)
                        .setContentText(context.getString(R.string.notif_completed))
                        .setContentIntent(mainPIntent)
                        .addAction(R.drawable.ic_action_system_update, context.getString(R.string.install), flashPIntent)
                        .build();
                nm.notify(info.getFlashNotifID(), notif);
            } else {
                Intent mainIntent = new Intent(context, OTAUpdaterActivity.class);
                mainIntent.setAction(info.getNotifAction());
                info.addToIntent(mainIntent);
                PendingIntent mainPIntent = PendingIntent.getActivity(context, 0, mainIntent, PendingIntent.FLAG_CANCEL_CURRENT);

                Intent dlIntent = new Intent(context, DownloadReceiver.class);
                dlIntent.setAction(info.getDownloadAction());
                info.addToIntent(dlIntent);
                PendingIntent dlPIntent = PendingIntent.getBroadcast(context, 1, dlIntent, PendingIntent.FLAG_CANCEL_CURRENT);

                Intent clearIntent = new Intent(context, DownloadReceiver.class);
                clearIntent.setAction(CLEAR_DL_ACTION);
                clearIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, status.getId());
                PendingIntent clearPIntent = PendingIntent.getBroadcast(context, 2, clearIntent, PendingIntent.FLAG_CANCEL_CURRENT);

                Notification notif = new NotificationCompat.Builder(context)
                        .setTicker(context.getString(info.getDownloadFailedTitle()))
                        .setContentTitle(context.getString(info.getDownloadFailedTitle()))
                        .setContentText(status.getErrorString(context))
                        .setSmallIcon(R.drawable.ic_stat_warning)
                        .setContentIntent(mainPIntent)
                        .setDeleteIntent(clearPIntent)
                        .addAction(R.drawable.ic_action_refresh, context.getString(R.string.retry), dlPIntent)
                        .build();
                nm.notify(info.getFailedNotifID(), notif);
            }
        } else if (action.equals(DownloadManager.ACTION_NOTIFICATION_CLICKED)) {
            long[] ids = intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS);
            if (ids.length == 0) return;

            DownloadStatus status = DownloadStatus.forDownloadID(context, ids[0]);
            if (status == null) return;

            BaseInfo info = status.getInfo();
            if (info == null) return;

            Intent i = new Intent(context, OTAUpdaterActivity.class);
            i.setAction(info.getNotifAction());
            i.putExtra(OTAUpdaterActivity.EXTRA_FLAG_DOWNLOAD_DIALOG, true);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }
}
