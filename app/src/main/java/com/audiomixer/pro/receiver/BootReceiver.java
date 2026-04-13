package com.audiomixer.pro.receiver;

import android.content.*;
import android.os.Build;
import com.audiomixer.pro.service.OverlayService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent i = new Intent(ctx, OverlayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(i);
            else
                ctx.startService(i);
        }
    }
}
