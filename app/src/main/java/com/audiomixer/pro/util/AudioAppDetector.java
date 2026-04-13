package com.audiomixer.pro.util;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import com.audiomixer.pro.db.AppDatabase;
import com.audiomixer.pro.db.AppVolumePref;
import java.util.*;

public class AudioAppDetector {

    public static class AppAudioInfo {
        public String packageName;
        public String appName;
        public Drawable icon;
        public int volumePercent;
        public boolean muted;

        public AppAudioInfo(String pkg, String name, Drawable icon, int vol, boolean muted) {
            this.packageName = pkg;
            this.appName = name;
            this.icon = icon;
            this.volumePercent = vol;
            this.muted = muted;
        }
    }

    private static final Set<String> SYSTEM_SKIP = new HashSet<>(Arrays.asList(
            "com.audiomixer.pro",
            "com.android.systemui",
            "com.huawei.android.launcher",
            "com.android.launcher3",
            "com.android.launcher",
            "com.huawei.systemmanager",
            "com.android.settings"
    ));

    public static List<AppAudioInfo> getActiveApps(Context ctx) {
        List<AppAudioInfo> result = new ArrayList<>();
        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager pm = ctx.getPackageManager();
        AppDatabase db = AppDatabase.getInstance(ctx);
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        long now = System.currentTimeMillis();
        List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, now - 3_600_000L, now);

        if (stats == null || stats.isEmpty()) return result;

        stats.sort((a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));

        int count = 0;
        for (UsageStats s : stats) {
            if (count >= 4) break;
            String pkg = s.getPackageName();
            if (SYSTEM_SKIP.contains(pkg)) continue;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                // Solo apps de usuario
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                String name = pm.getApplicationLabel(ai).toString();
                Drawable icon = pm.getApplicationIcon(ai);

                // Obtener volumen guardado
                AppVolumePref pref = db.appVolumeDao().getByPackage(pkg);
                int vol = (pref != null) ? pref.volumePercent : 100;
                boolean muted = (pref != null) && pref.muted;

                result.add(new AppAudioInfo(pkg, name, icon, vol, muted));
                count++;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return result;
    }

    /** Aplica el volumen guardado al stream de música */
    public static void applyVolumeForApp(Context ctx, String packageName) {
        AppDatabase db = AppDatabase.getInstance(ctx);
        AppVolumePref pref = db.appVolumeDao().getByPackage(packageName);
        if (pref == null) return;

        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int target = pref.muted ? 0 : Math.round(pref.volumePercent / 100f * max);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
    }

    /** Convierte porcentaje 0-100 a valor de stream */
    public static int percentToStream(Context ctx, int percent, int stream) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(stream);
        return Math.round(percent / 100f * max);
    }

    /** Convierte valor de stream a porcentaje 0-100 */
    public static int streamToPercent(Context ctx, int value, int stream) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(stream);
        if (max == 0) return 0;
        return Math.round(value * 100f / max);
    }
}
