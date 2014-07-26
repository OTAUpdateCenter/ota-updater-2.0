package com.otaupdater.utils;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.otaupdater.utils.ShellCommand.CommandResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropUtils {
    public static final String GEN_OTA_PROP = "/system/ota.prop";
    public static final String ROM_OTA_PROP = "/system/rom.ota.prop";
    public static final String KERNEL_OTA_PROP = "/system/kernel.ota.prop";

    private static final boolean ROM_OTA_ENABLED;
    private static String cachedRomID = null;
    private static Date cachedRomDate = null;
    private static String cachedRomVer = null;

    private static /*final*/ boolean KERNEL_OTA_ENABLED;
    private static String cachedKernelID = null;
    private static Date cachedKernelDate = null;
    private static String cachedKernelVer = null;
    private static String cachedFullKernelVer = null;
    private static String cachedKernelUname = null;

    private static String cachedSystemSdPath = null;
    private static String cachedRecoverySdPath = null;

    private static Boolean cachedNoFlash = null;
    private static String cachedRebootCmd = null;

    static {
        ROM_OTA_ENABLED = new File("/system/rom.ota.prop").exists() || LegacyCompat.isRomOtaEnabled();

        KERNEL_OTA_ENABLED = new File("/system/kernel.ota.prop").exists();
        if (KERNEL_OTA_ENABLED) {
            String fullVer = PropUtils.getFullKernelVersion();
            String fullOtaVer = PropUtils.getFullKernelOtaVersion();
            if (!fullVer.equals(fullOtaVer)) {
                KERNEL_OTA_ENABLED = false;
                //TODO maybe try to delete the file?
            }
        }
    }

    // from AOSP source: packages/apps/Settings/src/com/android/settings/DeviceInfoSettings.java
    private static final String PROC_VERSION_REGEX =
            "\\w+\\s+" + /* ignore: Linux */
            "\\w+\\s+" + /* ignore: version */
            "([^\\s]+)\\s+" + /* group 1: 2.6.22-omap1 */
            "\\(([^\\s@]+@[^\\s@]+)\\)+\\s+" + /* group 2: (xxxxxx@xxxxx.constant) */
            // "(gcc" followed by anything up to two consecutive ")"
            // separated by only white space (which seems to be the norm)
            "\\(gcc.*\\)\\s+" +
            "([^\\s]+)\\s+" + /* group 3: #26 */
            "(?:SMP\\s+)?" + /* ignore: SMP (optional) */
            "(?:PREEMPT\\s+)?" + /* ignore: PREEMPT (optional) */
            "(.+)"; /* group 4: date */

    public static boolean isRomOtaEnabled() {
        return ROM_OTA_ENABLED;
    }

    public static boolean isKernelOtaEnabled() {
        return KERNEL_OTA_ENABLED;
    }

    public static String getRomOtaID() {
        if (!ROM_OTA_ENABLED) return null;
        if (cachedRomID == null) {
            readRomOtaProp();
        }
        if (cachedRomID == null) {
            cachedRomID = LegacyCompat.getRomOtaID();
        }
        return cachedRomID;
    }

    public static Date getRomOtaDate() {
        if (!ROM_OTA_ENABLED) return null;
        if (cachedRomDate == null) {
            readRomOtaProp();
        }
        if (cachedRomDate == null) {
            cachedRomDate = LegacyCompat.getRomOtaDate();
        }
        return cachedRomDate;
    }

    public static String getRomOtaVersion() {
        if (!ROM_OTA_ENABLED) return null;
        if (cachedRomVer == null) {
            readRomOtaProp();
        }
        if (cachedRomVer == null) {
            cachedRomVer = LegacyCompat.getRomOtaVersion();
        }
        return cachedRomVer;
    }

    public static String getRomVersion() {
        ShellCommand cmd = new ShellCommand();
        CommandResult modVer = cmd.sh.runWaitFor("getprop ro.modversion");
        if (modVer.stdout.length() != 0) return modVer.stdout;

        CommandResult cmVer = cmd.sh.runWaitFor("getprop ro.cm.version");
        if (cmVer.stdout.length() != 0) return cmVer.stdout;

        CommandResult aokpVer = cmd.sh.runWaitFor("getprop ro.aokp.version");
        if (aokpVer.stdout.length() != 0) return aokpVer.stdout;

        return Build.DISPLAY;
    }

    public static String getKernelOtaID() {
        if (!KERNEL_OTA_ENABLED) return null;
        if (cachedKernelID == null) {
            readKernelOtaProp();
        }
        return cachedKernelID;
    }

    public static Date getKernelOtaDate() {
        if (!KERNEL_OTA_ENABLED) return null;
        if (cachedKernelDate == null) {
            readKernelOtaProp();
        }
        return cachedKernelDate;
    }

    public static String getKernelOtaVersion() {
        if (!KERNEL_OTA_ENABLED) return null;
        if (cachedKernelVer == null) {
            readKernelOtaProp();
        }
        return cachedKernelVer;
    }

    public static String getKernelVersion() {
        if (cachedKernelUname == null) {
            ShellCommand cmd = new ShellCommand();
            CommandResult procVerResult = cmd.sh.runWaitFor("cat /proc/version");
            if (procVerResult.stdout.length() == 0) return null;

            Pattern p = Pattern.compile(PROC_VERSION_REGEX);
            Matcher m = p.matcher(procVerResult.stdout);

            if (!m.matches() || m.groupCount() < 4) {
                return null;
            } else {
                cachedKernelUname = (new StringBuilder(m.group(1)).append("\n").append(
                        m.group(2)).append(" ").append(m.group(3)).append("\n")
                        .append(m.group(4))).toString();
            }
        }
        return cachedKernelUname;
    }

    public static String getFullKernelOtaVersion() {
        if (!KERNEL_OTA_ENABLED) return null;
        if (cachedFullKernelVer == null) {
            readKernelOtaProp();
        }
        return cachedFullKernelVer;
    }

    public static String getFullKernelVersion() {
        ShellCommand cmd = new ShellCommand();
        CommandResult procVerResult = cmd.sh.runWaitFor("cat /proc/version");
        if (procVerResult.stdout.length() == 0) return null;
        return procVerResult.stdout;
    }

    public static String getSystemSdPath() {
        if (cachedSystemSdPath == null) {
            readGenOtaProp();
        }
        return cachedSystemSdPath;
    }

    public static String getRecoverySdPath() {
        if (cachedRecoverySdPath == null) {
            readGenOtaProp();
        }
        return cachedRecoverySdPath;
    }

    public static boolean getNoFlash() {
        if (cachedNoFlash == null) {
            readGenOtaProp();
        }
        return cachedNoFlash;
    }

    public static String getRebootCmd() {
        if (cachedRebootCmd == null) {
            readGenOtaProp();
        }
        return cachedRebootCmd;
    }

    @SuppressLint("SdCardPath")
    public static String getDefaultRecoverySdPath() {
        String userPath = "";
        if (Environment.isExternalStorageEmulated() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            userPath = "/0";
        }

        return "/sdcard" + userPath;
    }
    private static void readGenOtaProp() {
        if (!new File(GEN_OTA_PROP).exists()) {
            cachedNoFlash = LegacyCompat.getNoflash();
            cachedRebootCmd = LegacyCompat.getRebootCmd();
            cachedSystemSdPath = LegacyCompat.getSystemSdPath();
            cachedRecoverySdPath = LegacyCompat.getRecoverySdPath();

            if (cachedNoFlash == null) cachedNoFlash = false;
            if (cachedRebootCmd == null) cachedRebootCmd = "reboot recovery";
            if (cachedSystemSdPath == null) cachedSystemSdPath = Environment.getExternalStorageDirectory().getAbsolutePath();
            if (cachedRecoverySdPath == null) cachedRecoverySdPath = getDefaultRecoverySdPath();
            return;
        }

        ShellCommand cmd = new ShellCommand();
        CommandResult catResult = cmd.sh.runWaitFor("cat " + GEN_OTA_PROP);
        if (catResult.stdout.length() == 0) return;

        try {
            JSONObject genOtaProp = new JSONObject(catResult.stdout);
            cachedNoFlash = genOtaProp.optBoolean("noflash", false);
            cachedRebootCmd = genOtaProp.optString("rebootcmd", "reboot recovery");
            cachedSystemSdPath = genOtaProp.optString("system_sdpath", Environment.getExternalStorageDirectory().getAbsolutePath());
            cachedRecoverySdPath = genOtaProp.optString("recovery_sdpath", getDefaultRecoverySdPath());
        } catch (JSONException e) {
            Log.e(Config.LOG_TAG + "ReadOTAProp", "Error in ota.prop file!");
        }
    }

    private static void readRomOtaProp() {
        if (!ROM_OTA_ENABLED) return;

        ShellCommand cmd = new ShellCommand();
        CommandResult catResult = cmd.sh.runWaitFor("cat " + ROM_OTA_PROP);
        if (catResult.stdout.length() == 0) return;

        try {
            JSONObject romOtaProp = new JSONObject(catResult.stdout);
            cachedRomID = romOtaProp.getString("otaid");
            cachedRomVer = romOtaProp.getString("otaver");
            cachedRomDate = Utils.parseDate(romOtaProp.getString("otatime"));
        } catch (JSONException e) {
            Log.e(Config.LOG_TAG + "ReadOTAProp", "Error in rom.ota.prop file!");
        }
    }

    private static void readKernelOtaProp() {
        if (!KERNEL_OTA_ENABLED) return;

        ShellCommand cmd = new ShellCommand();
        CommandResult catResult = cmd.sh.runWaitFor("cat " + KERNEL_OTA_PROP);
        if (catResult.stdout.length() == 0) return;

        try {
            JSONObject kernelOtaProp = new JSONObject(catResult.stdout);
            cachedKernelID = kernelOtaProp.getString("otaid");
            cachedKernelVer = kernelOtaProp.getString("otaver");
            cachedKernelDate = Utils.parseDate(kernelOtaProp.getString("otatime"));
            cachedFullKernelVer = kernelOtaProp.getString("fullver");
        } catch (JSONException e) {
            Log.e(Config.LOG_TAG + "ReadOTAProp", "Error in kernel.ota.prop file!");
        }
    }

    private static class LegacyCompat {
        public static final String OTA_ID_PROP = "otaupdater.otaid";
        public static final String OTA_VER_PROP = "otaupdater.otaver";
        public static final String OTA_DATE_PROP = "otaupdater.otatime";
        public static final String OTA_REBOOT_CMD_PROP = "otaupdater.rebootcmd";
        public static final String OTA_NOFLASH_PROP = "otaupdater.noflash";
        public static final String OTA_SYSTEM_SD_PATH_PROP = "otaupdater.sdcard.os";
        public static final String OTA_RECOVERY_SD_PATH_PROP = "otaupdater.sdcard.recovery";

        // cache the legacy ROM id because it's used in isRomOtaEnabled in order to avoid unnecessary forks to getprop
        private static String cachedRomID = null;

        public static boolean isRomOtaEnabled() {
            String romID = getRomOtaID();
            return romID != null && romID.isEmpty();
        }

        public static String getRomOtaID() {
            if (cachedRomID == null) {
                ShellCommand cmd = new ShellCommand();
                CommandResult propResult = cmd.sh.runWaitFor("getprop " + OTA_ID_PROP);
                if (propResult.stdout.length() == 0) return null;
                cachedRomID = propResult.stdout;
            }
            return cachedRomID;
        }

        public static Date getRomOtaDate() {
            ShellCommand cmd = new ShellCommand();
            CommandResult propResult = cmd.sh.runWaitFor("getprop " + OTA_DATE_PROP);
            if (propResult.stdout.length() == 0) return null;
            return Utils.parseDate(propResult.stdout);
        }

        public static String getRomOtaVersion() {
            ShellCommand cmd = new ShellCommand();
            CommandResult propResult = cmd.sh.runWaitFor("getprop " + OTA_VER_PROP);
            if (propResult.stdout.length() == 0) return null;
            return propResult.stdout;
        }

        public static String getRebootCmd() {
            ShellCommand cmd = new ShellCommand();
            CommandResult propResult = cmd.sh.runWaitFor("getprop " + OTA_REBOOT_CMD_PROP);
            if (propResult.stdout.length() == 0) return null;
            return propResult.stdout;
        }

        public static Boolean getNoflash() {
            ShellCommand cmd = new ShellCommand();
            CommandResult propResult = cmd.sh.runWaitFor("getprop " + OTA_NOFLASH_PROP);
            if (propResult.stdout.length() == 0) return null;
            return propResult.stdout.equals("1") || propResult.stdout.equalsIgnoreCase("true");
        }

        @SuppressLint("SdCardPath")
        public static String getSystemSdPath() {
            ShellCommand cmd = new ShellCommand();
            CommandResult propResult = cmd.sh.runWaitFor("getprop " + OTA_SYSTEM_SD_PATH_PROP);
            if (propResult.stdout.length() == 0) return null;
            return propResult.stdout;
        }

        @SuppressLint("SdCardPath")
        public static String getRecoverySdPath() {
            ShellCommand cmd = new ShellCommand();
            CommandResult propResult = cmd.sh.runWaitFor("getprop " + OTA_RECOVERY_SD_PATH_PROP);
            if (propResult.stdout.length() == 0) return null;
            return propResult.stdout;
        }
    }
}
