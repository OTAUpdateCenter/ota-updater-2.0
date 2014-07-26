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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.otaupdater.utils.Config;
import com.otaupdater.utils.KernelInfo;
import com.otaupdater.utils.PropUtils;
import com.otaupdater.utils.RomInfo;

public class GCMIntentService extends IntentService {

    public GCMIntentService() {
        super("GCMIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        String messageType = gcm.getMessageType(intent);

        final Context context = getApplicationContext();
        final Config cfg = Config.getInstance(context);

        if (extras != null && !extras.isEmpty() && GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
            if (extras.containsKey(RomInfo.KEY_NAME)) {
                if (!PropUtils.isRomOtaEnabled()) return;

                RomInfo info = RomInfo.FACTORY.fromBundle(extras);

                if (!info.isUpdate()) {
                    Log.v(Config.LOG_TAG + "GCM", "got rom GCM message, not update");
                    cfg.clearStoredRomUpdate();
                    RomInfo.FACTORY.clearUpdateNotif(context);
                    return;
                }

                cfg.storeRomUpdate(info);
                if (cfg.getShowNotif()) {
                    Log.v(Config.LOG_TAG + "GCM", "got rom GCM message");
                    info.showUpdateNotif(context);
                } else {
                    Log.v(Config.LOG_TAG + "GCM", "got rom GCM message, notif not shown");
                }

                if (cfg.hasProKey() && cfg.getAutoDlState()) info.startDownload(context);
            } else if (extras.containsKey(KernelInfo.KEY_NAME)) {
                if (!PropUtils.isKernelOtaEnabled()) return;

                KernelInfo info = KernelInfo.FACTORY.fromBundle(extras);

                if (!info.isUpdate()) {
                    Log.v(Config.LOG_TAG + "GCM", "got kernel GCM message, not update");
                    cfg.clearStoredKernelUpdate();
                    KernelInfo.FACTORY.clearUpdateNotif(context);
                    return;
                }

                cfg.storeKernelUpdate(info);
                if (cfg.getShowNotif()) {
                    Log.v(Config.LOG_TAG + "GCM", "got kernel GCM message");
                    info.showUpdateNotif(context);
                } else {
                    Log.v(Config.LOG_TAG + "GCM", "got kernel GCM message, notif not shown");
                }

                if (cfg.hasProKey() && cfg.getAutoDlState()) info.startDownload(context);
            } else {
                Log.v(Config.LOG_TAG + "GCM", "got malformed GCM message");
            }
        }

        GCMBroadcastReceiver.completeWakefulIntent(intent);
    }
}
