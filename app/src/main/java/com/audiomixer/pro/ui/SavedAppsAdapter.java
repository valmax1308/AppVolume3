package com.audiomixer.pro.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.audiomixer.pro.db.AppVolumePref;
import java.util.List;

public class SavedAppsAdapter extends RecyclerView.Adapter<SavedAppsAdapter.VH> {

    public interface OnDelete { void onDelete(String packageName); }

    private final Context ctx;
    private final List<AppVolumePref> items;
    private final OnDelete onDelete;

    public SavedAppsAdapter(Context ctx, List<AppVolumePref> items, OnDelete onDelete) {
        this.ctx = ctx; this.items = items; this.onDelete = onDelete;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(32, 20, 32, 20);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return new VH(row);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        AppVolumePref p = items.get(pos);
        h.row.removeAllViews();

        // Ícono
        ImageView iv = new ImageView(ctx);
        try {
            iv.setImageDrawable(ctx.getPackageManager().getApplicationIcon(p.packageName));
        } catch (PackageManager.NameNotFoundException e) {
            iv.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(80, 80);
        ilp.rightMargin = 24;
        h.row.addView(iv, ilp);

        // Nombre y volumen
        LinearLayout info = new LinearLayout(ctx);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(ctx);
        name.setText(p.appName.isEmpty() ? p.packageName : p.appName);
        name.setTextSize(14f);
        name.setTextColor(0xFF222222);
        info.addView(name);

        TextView vol = new TextView(ctx);
        vol.setText(p.muted ? "Silenciada" : "Volumen: " + p.volumePercent + "%");
        vol.setTextSize(12f);
        vol.setTextColor(p.muted ? 0xFFE24B4A : 0xFF1D9E75);
        info.addView(vol);
        h.row.addView(info);

        // Botón eliminar
        TextView del = new TextView(ctx);
        del.setText("✕");
        del.setTextSize(16f);
        del.setTextColor(0xFFAAAAAA);
        del.setPadding(16, 0, 0, 0);
        del.setOnClickListener(v -> onDelete.onDelete(p.packageName));
        h.row.addView(del);
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        LinearLayout row;
        VH(LinearLayout v) { super(v); row = v; }
    }
}
