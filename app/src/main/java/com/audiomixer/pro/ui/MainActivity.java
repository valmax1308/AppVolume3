package com.audiomixer.pro.ui;

import android.app.AppOpsManager;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.audiomixer.pro.R;
import com.audiomixer.pro.db.AppDatabase;
import com.audiomixer.pro.db.AppVolumePref;
import com.audiomixer.pro.service.OverlayService;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private RecyclerView rvSaved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        rvSaved  = findViewById(R.id.rv_saved);
        rvSaved.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.btn_start).setOnClickListener(v -> checkAndStart());
        findViewById(R.id.btn_stop).setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            tvStatus.setText("Panel: DESACTIVADO");
        });
        findViewById(R.id.btn_accessibility).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.btn_usage).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        findViewById(R.id.btn_overlay).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))));
        findViewById(R.id.btn_battery).setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        loadSavedApps();
    }

    private void updateStatus() {
        boolean overlay      = Settings.canDrawOverlays(this);
        boolean usage        = hasUsageStats();
        boolean accessibility = isAccessibilityEnabled();

        StringBuilder sb = new StringBuilder();
        sb.append(overlay       ? "✓" : "✗").append(" Mostrar sobre otras apps\n");
        sb.append(usage         ? "✓" : "✗").append(" Estadísticas de uso\n");
        sb.append(accessibility ? "✓" : "✗").append(" Servicio de accesibilidad\n");

        if (overlay && usage && accessibility) {
            sb.append("\n✅ Todo listo. Toca ACTIVAR.");
        } else {
            sb.append("\nToca cada botón de permiso faltante (✗) y actívalo.");
        }
        tvStatus.setText(sb.toString());
    }

    private void checkAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Primero activa 'Mostrar sobre otras apps'");
            return;
        }
        if (!hasUsageStats()) {
            toast("Primero activa 'Acceso a estadísticas de uso'");
            return;
        }
        Intent i = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        tvStatus.setText("✅ Panel flotante ACTIVO\nBusca el botón verde en tu pantalla.");
    }

    private void loadSavedApps() {
        new Thread(() -> {
            List<AppVolumePref> prefs = AppDatabase.getInstance(this).appVolumeDao().getAll();
            runOnUiThread(() -> {
                SavedAppsAdapter adapter = new SavedAppsAdapter(this, prefs, (pkg) -> {
                    new AlertDialog.Builder(this)
                        .setTitle("Restablecer volumen")
                        .setMessage("¿Quitar la preferencia guardada de esta app?")
                        .setPositiveButton("Sí", (d, w) -> {
                            new Thread(() -> {
                                AppDatabase.getInstance(this).appVolumeDao().delete(pkg);
                                runOnUiThread(this::loadSavedApps);
                            }).start();
                        })
                        .setNegativeButton("No", null).show();
                });
                rvSaved.setAdapter(adapter);
            });
        }).start();
    }

    private boolean hasUsageStats() {
        AppOpsManager aom = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean isAccessibilityEnabled() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.contains(getPackageName());
        } catch (Exception e) { return false; }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
