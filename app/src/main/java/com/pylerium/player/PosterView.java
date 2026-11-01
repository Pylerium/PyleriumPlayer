package com.pylerium.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/** Generated poster art: a title-seeded graphite gradient, so every card looks intentional without downloaded artwork. */
final class PosterView extends View {
    static final int WIDTH = 150, HEIGHT = 225;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint type = new TextPaint(Paint.ANTI_ALIAS_FLAG), titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG), subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float density;
    private String title = "", subtitle = "", badge = ""; private float progress; private boolean watched; private int hue;
    private StaticLayout titleLayout, subtitleLayout;

    PosterView(Context context) {
        super(context); density = context.getResources().getDisplayMetrics().density; setFocusable(true); setClickable(true);
        edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(3 * density); edge.setColor(Ui.ORANGE); type.setTypeface(Ui.font(context));
        titlePaint.setTypeface(Typeface.create(Ui.font(context), Typeface.BOLD)); titlePaint.setTextSize(16 * density); titlePaint.setColor(Color.WHITE);
        subPaint.setTypeface(Ui.font(context)); subPaint.setTextSize(11.5f * density); subPaint.setColor(0xFFB6C4CB);
    }
    void bind(String title, String subtitle, String badge, float progress, boolean watched) {
        this.title = title; this.subtitle = subtitle; this.badge = badge; this.progress = progress; this.watched = watched;
        hue = (title.toLowerCase().hashCode() & 0x7fffffff) % 360; titleLayout = null; invalidate();
    }
    @Override protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        animate().scaleX(focused ? 1.09f : 1f).scaleY(focused ? 1.09f : 1f).setDuration(140).start(); setElevation(focused ? 12 * density : 0); invalidate();
    }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); titleLayout = null; }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = 12 * density; box.set(0, 0, w, h);
        fill.setShader(new LinearGradient(0, 0, w, h, Color.HSVToColor(new float[]{hue, .50f, .46f}), Color.HSVToColor(new float[]{(hue + 40) % 360, .60f, .13f}), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(box, r, r, fill); fill.setShader(null);
        type.setTextAlign(Paint.Align.LEFT); type.setTypeface(Typeface.create(Ui.font(getContext()), Typeface.BOLD)); type.setTextSize(110 * density); type.setColor(0x22FFFFFF);
        canvas.drawText(initial(), 10 * density, 108 * density, type);
        fill.setShader(new LinearGradient(0, h * .42f, 0, h, 0x00050709, 0xEE050709, Shader.TileMode.CLAMP)); canvas.drawRoundRect(box, r, r, fill); fill.setShader(null);
        if (titleLayout == null) {
            int width = (int) (w - 28 * density);
            titleLayout = StaticLayout.Builder.obtain(title, 0, title.length(), titlePaint, width).setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
            subtitleLayout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length(), subPaint, width).setMaxLines(1).setEllipsize(TextUtils.TruncateAt.END).build();
        }
        float bottom = h - (progress > 0 ? 18 : 14) * density;
        canvas.save(); canvas.translate(14 * density, bottom - subtitleLayout.getHeight()); subtitleLayout.draw(canvas); canvas.restore();
        canvas.save(); canvas.translate(14 * density, bottom - subtitleLayout.getHeight() - 4 * density - titleLayout.getHeight()); titleLayout.draw(canvas); canvas.restore();
        if (!badge.isEmpty() || watched) {
            String label = watched ? "✓ WATCHED" : badge; type.setTextSize(10 * density); type.setLetterSpacing(.08f); type.setTypeface(Typeface.create(Ui.font(getContext()), Typeface.BOLD));
            float tw = type.measureText(label), bx = w - tw - 22 * density; box.set(bx, 10 * density, w - 10 * density, 28 * density);
            fill.setColor(watched ? 0xCC1B8A4B : 0xCC050709); canvas.drawRoundRect(box, 6 * density, 6 * density, fill); type.setColor(Color.WHITE); canvas.drawText(label, bx + 6 * density, 23 * density, type); type.setLetterSpacing(0);
        }
        if (progress > 0 && !watched) {
            float y = h - 9 * density, x0 = 14 * density, x1 = w - 14 * density; fill.setColor(0x44FFFFFF); canvas.drawRoundRect(x0, y, x1, y + 3 * density, 2 * density, 2 * density, fill);
            fill.setColor(Ui.ORANGE); canvas.drawRoundRect(x0, y, x0 + (x1 - x0) * progress, y + 3 * density, 2 * density, 2 * density, fill);
        }
        if (isFocused()) { box.set(1.5f * density, 1.5f * density, w - 1.5f * density, h - 1.5f * density); canvas.drawRoundRect(box, r, r, edge); }
    }
    private String initial() { String t = title.replaceAll("^(?i)(the|a|an)\\s+", ""); return t.isEmpty() ? "" : t.substring(0, 1).toUpperCase(); }
}
