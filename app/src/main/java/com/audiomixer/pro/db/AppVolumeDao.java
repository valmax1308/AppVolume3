package com.audiomixer.pro.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface AppVolumeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(AppVolumePref pref);

    @Query("SELECT * FROM app_volume_prefs WHERE packageName = :pkg LIMIT 1")
    AppVolumePref getByPackage(String pkg);

    @Query("SELECT * FROM app_volume_prefs ORDER BY appName ASC")
    List<AppVolumePref> getAll();

    @Query("DELETE FROM app_volume_prefs WHERE packageName = :pkg")
    void delete(String pkg);
}
