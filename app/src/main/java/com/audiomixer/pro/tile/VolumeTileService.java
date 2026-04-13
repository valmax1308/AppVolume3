package com.audiomixer.pro.tile;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.annotation.RequiresApi;
import com.audiomixer.pro.service.OverlayService;

@RequiresApi(api = Build.VERSION_CODES.N)
public class VolumeTileService extends TileService {

    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        // Mostrar / ocultar el panel desde el tile
        sendBroadcast(new Intent(OverlayService.ACTION_SHOW_PANEL));
        // Colapsar el panel de notificaciones
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+
        } else {
            try {
                Object statusBarService = getSystemService("statusbar");
                Class<?> statusBarManager = Class.forName("android.app.StatusBarManager");
                statusBarManager.getMethod("collapsePanels").invoke(statusBarService);
            } catch (Exception ignored) {}
        }
    }
}
