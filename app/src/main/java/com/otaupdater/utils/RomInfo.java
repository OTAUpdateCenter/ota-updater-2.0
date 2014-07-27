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

import com.otaupdater.DownloadReceiver;
import com.otaupdater.DownloadsActivity;
import com.otaupdater.OTAUpdaterActivity;
import com.otaupdater.R;

import java.io.File;
import java.util.Date;

public class RomInfo extends BaseInfo {
    public static final String KEY_NAME = "rom";

    public static final InfoFactory<RomInfo> FACTORY = new InfoFactory<RomInfo>(RomInfo.class);
    public static final Creator<RomInfo> CREATOR = FACTORY.getParcelableCreator();

    @Override
    public String getFlashAction() {
        return DownloadsActivity.FLASH_ROM_ACTION;
    }

    @Override
    public int getDownloadingTitle() {
        return R.string.rom_download_alert_title;
    }

    @Override
    public int getDownloadFailedTitle() {
        return R.string.rom_download_failed;
    }

    @Override
    public int getDownloadDoneTitle() {
        return R.string.rom_download_done;
    }

    @Override
    public int getFailedNotifID() {
        return Config.ROM_FAILED_NOTIF_ID;
    }

    @Override
    public int getFlashNotifID() {
        return Config.ROM_FLASH_NOTIF_ID;
    }

    @Override
    protected String getNameKey() {
        return KEY_NAME;
    }

    @Override
    public String getNotifAction() {
        return OTAUpdaterActivity.ROM_NOTIF_ACTION;
    }

    @Override
    public String getDownloadAction() {
        return DownloadReceiver.DL_ROM_ACTION;
    }

    @Override
    protected File getDownloadPathFile() {
        return Config.ROM_DL_PATH_FILE;
    }

    @Override
    protected String getDownloadSdPath() {
        return Config.ROM_SD_PATH;
    }

    @Override
    protected int getNotifTickerStr() {
        return R.string.rom_download_ticker;
    }

    @Override
    protected int getNotifTextStr() {
        return R.string.rom_download_title;
    }

    @Override
    protected int getNotifDetailsStr() {
        return R.string.rom_download_details;
    }

    @Override
    protected int getNotifID() {
        return Config.ROM_NOTIF_ID;
    }

    @Override
    protected int getDownloadingNotifTitle() {
        return R.string.rom_download_progress;
    }

    @Override
    protected int getDownloadDialogMessageStr() {
        return R.string.rom_update_to;
    }

    @Override
    protected boolean isDownloading(Context ctx) {
        return Config.getInstance(ctx).isDownloadingRom();
    }

    @Override
    protected Date getPropDate() {
        return PropUtils.getRomOtaDate();
    }

    @Override
    protected String getPropVersion() {
        return PropUtils.getRomVersion();
    }
}
