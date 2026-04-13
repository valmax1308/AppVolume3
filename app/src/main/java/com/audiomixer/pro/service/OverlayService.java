package com.audiomixer.pro.service;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import androidx.core.app.NotificationCompat;
import com.audiomixer.pro.R;
import com.audiomixer.pro.db.AppDatabase;
import com.audiomixer.pro.db.AppVolumePref;
import com.audiomixer.pro.ui.MainActivity;
import com.audiomixer.pro.util.AudioAppDetector;
import com.audiomixer.pro.util.AudioAppDetector.AppAudioInfo;
import java.util.List;

public class OverlayService extends Service {

    private static final String CHANNEL_ID = "AudioMixerChannel";
    private static final int NOTIF_ID = 1;
    public static final String ACTION_SHOW_PANEL = "com.audiomixer.pro.SHOW_PANEL";
    public static final String ACTION_STOP = "com.audiomixer.pro.STOP";

    private WindowManager wm;
    private AudioManager am;

    private View fabView;
    private View panelView;
    private boolean panelVisible = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoHide = this::hidePanel;

    // Receptor para mostrar panel desde AccessibilityService
    private final BroadcastReceiver showReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (ACTION_SHOW_PANEL.equals(intent.getAction())) {
                if (panelVisible) hidePanel(); else showPanel();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        registerReceiver(showReceiver, new IntentFilter(ACTION_SHOW_PANEL));
        createFab();
    }

    // ── FAB (botón flotante pequeño) ───────────────────────────────────────

    private void createFab() {
        fabView = buildFabView();
        WindowManager.LayoutParams lp = fabParams();
        fabView.setOnClickListener(v -> { if (panelVisible) hidePanel(); else showPanel(); });
        attachDrag(fabView, lp);
        wm.addView(fabView, lp);
    }

    private View buildFabView() {
        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.ic_volume_icon);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(dp(10), dp(10), dp(10), dp(10));
        iv.setBackground(circle(0xCC1D9E75));
        iv.setElevation(dp(6));
        return iv;
    }

    private WindowManager.LayoutParams fabParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(50), dp(50), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = dp(10);
        lp.y = dp(220);
        return lp;
    }

    private void attachDrag(View v, WindowManager.LayoutParams lp) {
        v.setOnTouchListener(new View.OnTouchListener() {
            float ix, iy; int ilx, ily; boolean moved;
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ix = e.getRawX(); iy = e.getRawY();
                        ilx = lp.x; ily = lp.y; moved = false; break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = ix - e.getRawX(), dy = e.getRawY() - iy;
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true;
                        lp.x = ilx + (int) dx; lp.y = ily + (int) dy;
                        wm.updateViewLayout(v, lp); break;
                    case MotionEvent.ACTION_UP:
                        if (!moved) view.performClick(); break;
                }
                return true;
            }
        });
    }

    // ── Panel de volumen ───────────────────────────────────────────────────

    public void showPanel() {
        if (panelView != null) { wm.removeView(panelView); panelView = null; }
        panelView = buildPanel();
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        lp.x = dp(66);
        wm.addView(panelView, lp);
        panelView.setAlpha(0f);
        panelView.setScaleX(0.85f);
        panelView.animate().alpha(1f).scaleX(1f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
        panelVisible = true;
        handler.removeCallbacks(autoHide);
        handler.postDelayed(autoHide, 7000);
    }

    public void hidePanel() {
        if (panelView == null) return;
        panelView.animate().alpha(0f).scaleX(0.85f).setDuration(160).withEndAction(() -> {
            if (panelView != null) { wm.removeView(panelView); panelView = null; }
        }).start();
        panelVisible = false;
    }

    private View buildPanel() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(roundRect(0xF0F0F0F0, dp(20)));
        root.setPadding(dp(12), dp(16), dp(12), dp(12));
        root.setElevation(dp(12));

        // Título
        TextView title = new TextView(this);
        title.setText("AudioMixer");
        title.setTextColor(0xFF333333);
        title.setTextSize(12f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(10));
        root.addView(title);

        // Fila de sliders
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

        // Sliders del sistema
        addSystemSlider(row, AudioManager.STREAM_MUSIC, "Media", 0xFF1D9E75, "♪");
        addSystemSlider(row, AudioManager.STREAM_NOTIFICATION, "Notif", 0xFF6C63FF, "🔔");

        // Apps activas
        List<AppAudioInfo> apps = AudioAppDetector.getActiveApps(this);
        for (AppAudioInfo app : apps) addAppSlider(row, app);

        root.addView(row);

        // Separador
        View sep = new View(this);
        sep.setBackgroundColor(0x22000000);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        slp.topMargin = dp(10);
        slp.bottomMargin = dp(6);
        root.addView(sep, slp);

        // Botón cerrar
        TextView close = new TextView(this);
        close.setText("Cerrar");
        close.setTextColor(0xFF888888);
        close.setTextSize(11f);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> hidePanel());
        root.addView(close);

        return root;
    }

    // ── Sliders ────────────────────────────────────────────────────────────

    private void addSystemSlider(LinearLayout row, int stream, String label, int color, String emoji) {
        int max = am.getStreamMaxVolume(stream);
        int cur = am.getStreamVolume(stream);
        int pct = max > 0 ? Math.round(cur * 100f / max) : 0;
        row.addView(buildSliderCol(label, null, emoji, pct, color, (p) -> {
            int val = Math.round(p / 100f * max);
            am.setStreamVolume(stream, val, 0);
            resetAutoHide();
        }));
    }

    private void addAppSlider(LinearLayout row, AppAudioInfo app) {
        row.addView(buildSliderCol(app.appName, app.icon, null,
                app.muted ? 0 : app.volumePercent, 0xFF378ADD, (p) -> {
                    saveAppVolume(app.packageName, app.appName, p);
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(p / 100f * max), 0);
                    resetAutoHide();
                }));
    }

    interface ProgressListener { void onProgress(int percent); }

    private View buildSliderCol(String label, Drawable icon, String emoji,
                                 int initPct, int color, ProgressListener listener) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(6), 0, dp(6), 0);

        // Ícono de la app
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            iv.setBackground(circle(0x11000000));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(32), dp(32));
            ilp.bottomMargin = dp(4);
            col.addView(iv, ilp);
        } else {
            TextView em = new TextView(this);
            em.setText(emoji != null ? emoji : "♪");
            em.setTextSize(18f);
            em.setGravity(Gravity.CENTER);
            col.addView(em);
        }

        // Porcentaje
        TextView tvPct = new TextView(this);
        tvPct.setText(initPct + "%");
        tvPct.setTextColor(color);
        tvPct.setTextSize(10f);
        tvPct.setGravity(Gravity.CENTER);
        col.addView(tvPct);

        // SeekBar vertical (rotado 270°)
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(initPct);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(color));
        sb.setRotation(270f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(36), dp(140));
        slp.topMargin = dp(6);
        slp.bottomMargin = dp(6);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) { tvPct.setText(p + "%"); listener.onProgress(p); }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        col.addView(sb, slp);

        // Etiqueta nombre
        TextView tvLabel = new TextView(this);
        String name = label.length() > 7 ? label.substring(0, 6) + "…" : label;
        tvLabel.setText(name);
        tvLabel.setTextColor(0xFF666666);
        tvLabel.setTextSize(9f);
        tvLabel.setGravity(Gravity.CENTER);
        col.addView(tvLabel);

        return col;
    }

    // ── DB ─────────────────────────────────────────────────────────────────

    private void saveAppVolume(String pkg, String name, int percent) {
        AppDatabase db = AppDatabase.getInstance(this);
        AppVolumePref pref = new AppVolumePref();
        pref.packageName = pkg;
        pref.appName = name;
        pref.volumePercent = percent;
        pref.muted = (percent == 0);
        pref.lastUsed = System.currentTimeMillis();
        new Thread(() -> db.appVolumeDao().save(pref)).start();
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    private void resetAutoHide() {
        handler.removeCallbacks(autoHide);
        handler.postDelayed(autoHide, 7000);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Drawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private Drawable roundRect(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    // ── Ciclo de vida ──────────────────────────────────────────────────────

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(autoHide);
        unregisterReceiver(showReceiver);
        if (fabView != null) wm.removeView(fabView);
        if (panelView != null) wm.removeView(panelView);
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    // ── Notificación ───────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "AudioMixer Pro", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Panel de control de volumen activo");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, OverlayService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AudioMixer Pro activo")
                .setContentText("Toca el botón verde flotante para controlar el volumen")
                .setSmallIcon(R.drawable.ic_volume_icon)
                .setContentIntent(pi)
                .addAction(0, "Detener", stopPi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
}
