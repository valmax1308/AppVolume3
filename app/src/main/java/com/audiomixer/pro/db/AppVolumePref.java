package com.audiomixer.pro.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "app_volume_prefs")
public class AppVolumePref {

    @PrimaryKey
    @NonNull
    public String packageName = "";

    public String appName = "";

    /** Volumen guardado 0-100 (porcentaje) */
    public int volumePercent = 100;

    /** Si está silenciada */
    public boolean muted = false;

    /** Última vez usada (timestamp) */
    public long lastUsed = 0;
}
