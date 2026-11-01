package com.pylerium.player;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

/** Pylerium graphite / orange / cyan design tokens and tiny view helpers. */
final class Ui {
    static final int ORANGE = 0xFFFF7B1C, CYAN = 0xFF26D8EE, INK = 0xFFF3F3F3, MUTED = 0xFF8FA2AC, DIM = 0xFF5E727D, PANEL = 0xF2101820, LINE = 0xFF27343B;
    private static Typeface font;

    static Typeface font(Context context) {
        if (font == null) { try { font = Typeface.createFromAsset(context.getAssets(), "fonts/Onest.ttf"); } catch (RuntimeException e) { font = Typeface.DEFAULT; } }
        return font;
    }
    static int dp(Context context, float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    static GradientDrawable round(Context context, int color, float radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(context, radius)); return d; }
    static GradientDrawable outline(Context context, int fill, int stroke, float width, float radius) { GradientDrawable d = round(context, fill, radius); d.setStroke(dp(context, width), stroke); return d; }

    static TextView text(Context context, String value, float size, int color, boolean bold) {
        TextView view = new TextView(context); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTypeface(font(context), bold ? Typeface.BOLD : Typeface.NORMAL); return view;
    }
    /** A focusable pill button that lights up orange when focused. */
    static TextView button(Context context, String label) {
        TextView view = text(context, label, 15, INK, true); view.setLetterSpacing(.06f); view.setGravity(Gravity.CENTER); view.setFocusable(true); view.setClickable(true);
        view.setPadding(dp(context, 26), dp(context, 12), dp(context, 26), dp(context, 12)); style(context, view, false);
        view.setOnFocusChangeListener((v, focused) -> style(context, view, focused)); return view;
    }
    private static void style(Context context, TextView view, boolean focused) {
        view.setBackground(focused ? round(context, ORANGE, 12) : outline(context, 0x33FFFFFF, 0x44FFFFFF, 1, 12)); view.setTextColor(focused ? 0xFF1A0E05 : INK);
    }
    static String clock(long millis) {
        long total = Math.max(0, millis / 1000), h = total / 3600, m = total % 3600 / 60, s = total % 60;
        return h > 0 ? String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s) : String.format(java.util.Locale.US, "%d:%02d", m, s);
    }
    static String size(long bytes) { return bytes >= 1L << 30 ? String.format(java.util.Locale.US, "%.1f GB", bytes / (double) (1L << 30)) : (bytes >> 20) + " MB"; }
}
