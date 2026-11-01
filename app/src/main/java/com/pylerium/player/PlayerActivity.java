package com.pylerium.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;
import java.util.List;

/** Full-screen player: resume, seek, audio/subtitle tracks and automatic next episode. */
public class PlayerActivity extends Activity {
    private static final long STALL_MS = 30_000, SAVE_MS = 5_000, UP_NEXT_SECONDS = 10;
    private static LibVLC vlc;
    private Library library; private List<String> queue; private int index;
    private VLCVideoLayout videoLayout; private MediaPlayer player; private ProgressBar spinner;
    private SeekView seek; private TextView title, subtitle, elapsed, remaining, hint, notice, upNext;
    private View panel, scrim;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long duration, seekTarget = -1, lastSave, lastKey; private int streak, generation; private boolean paused, ending;
    private final Runnable hide = () -> { if (!paused && seekTarget < 0) setOverlay(false); };
    private final Runnable hideNotice = () -> notice.animate().alpha(0).setDuration(250).start();
    private final Runnable commitSeek = this::commitSeek;
    private final Runnable tick = new Runnable() { @Override public void run() { update(); handler.postDelayed(this, 500); } };

    static synchronized LibVLC engine(Context context) {
        if (vlc == null) { limitFonts(context); vlc = new LibVLC(context.getApplicationContext(), new ArrayList<>(java.util.Arrays.asList("--file-caching=1500", "--audio-time-stretch", "--avcodec-skiploopfilter=1", "--no-stats", "--freetype-font=Onest"))); }
        return vlc;
    }
    /** Subtitle rendering scans every system font on first use (minutes on a TV). Point fontconfig at one bundled font instead. */
    private static void limitFonts(Context context) {
        try {
            java.io.File fonts = new java.io.File(context.getFilesDir(), "fonts"); fonts.mkdirs(); java.io.File font = new java.io.File(fonts, "Onest.ttf");
            if (!font.exists()) try (java.io.InputStream in = context.getAssets().open("fonts/Onest.ttf"); java.io.FileOutputStream out = new java.io.FileOutputStream(font)) { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n); }
            java.io.File conf = new java.io.File(context.getFilesDir(), "fonts.conf");
            String xml = "<?xml version=\"1.0\"?><!DOCTYPE fontconfig SYSTEM \"fonts.dtd\"><fontconfig><dir>" + fonts.getPath() + "</dir><cachedir>" + context.getCacheDir().getPath() + "/fontconfig</cachedir></fontconfig>";
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(conf)) { out.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            android.system.Os.setenv("FONTCONFIG_FILE", conf.getPath(), true); android.system.Os.setenv("FONTCONFIG_PATH", context.getFilesDir().getPath(), true);
        } catch (Exception ignored) { }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        library = Library.cached(this); queue = getIntent().getStringArrayListExtra("queue"); if (queue == null || queue.isEmpty()) { finish(); return; }
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        videoLayout = new VLCVideoLayout(this); root.addView(videoLayout, new FrameLayout.LayoutParams(-1, -1));
        spinner = new ProgressBar(this); root.addView(spinner, new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER));

        scrim = new View(this); scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xF5050709, 0xB8050709, 0x00050709}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, dp(280), Gravity.BOTTOM));
        LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setPadding(dp(56), 0, dp(56), dp(34)); panel = p;
        title = Ui.text(this, "", 28, Color.WHITE, true); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        subtitle = Ui.text(this, "", 14, Ui.MUTED, false); subtitle.setLetterSpacing(.04f); p.addView(title); p.addView(subtitle);
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(0, dp(18), 0, 0);
        elapsed = Ui.text(this, "0:00", 14, Color.WHITE, true); remaining = Ui.text(this, "0:00", 14, Ui.MUTED, true);
        seek = new SeekView(this); bar.addView(elapsed, new LinearLayout.LayoutParams(dp(78), -2)); bar.addView(seek, new LinearLayout.LayoutParams(0, dp(22), 1)); remaining.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); bar.addView(remaining, new LinearLayout.LayoutParams(dp(78), -2)); p.addView(bar);
        hint = Ui.text(this, "◀ ▶  SEEK      OK  PLAY / PAUSE      ▲  AUDIO & SUBTITLES      ▼  HIDE", 11, Ui.DIM, true); hint.setLetterSpacing(.08f); hint.setPadding(0, dp(12), 0, 0); p.addView(hint);
        root.addView(p, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        notice = Ui.text(this, "", 18, Color.WHITE, true); notice.setPadding(dp(24), dp(12), dp(24), dp(12)); notice.setBackground(Ui.round(this, 0xE60B1115, 14)); notice.setAlpha(0);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.TOP); nl.topMargin = dp(30); root.addView(notice, nl);
        upNext = Ui.text(this, "", 16, Color.WHITE, true); upNext.setPadding(dp(26), dp(18), dp(26), dp(18)); upNext.setBackground(Ui.outline(this, 0xF2101820, Ui.ORANGE, 2, 16)); upNext.setVisibility(View.GONE); upNext.setLineSpacing(dp(4), 1);
        FrameLayout.LayoutParams ul = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM); ul.setMargins(0, 0, dp(56), dp(130)); root.addView(upNext, ul);
        setContentView(root);

        player = new MediaPlayer(engine(this)); player.attachViews(videoLayout, null, true, false); player.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT);
        player.setEventListener(event -> {
            int token = generation;
            switch (event.type) {
                case MediaPlayer.Event.Playing: handler.post(() -> { if (token == generation) { spinner.setVisibility(View.GONE); update(); } }); break;
                case MediaPlayer.Event.LengthChanged: handler.post(() -> { if (token == generation) duration = player.getLength(); }); break;
                case MediaPlayer.Event.EndReached: handler.post(() -> { if (token == generation) onEnded(); }); break;
                case MediaPlayer.Event.EncounteredError: handler.post(() -> { if (token == generation) onError(); }); break;
                default: break;
            }
        });
    }
    @Override protected void onStart() { super.onStart(); if (player != null) { start(index); handler.post(tick); } }

    private void start(int i) {
        generation++; handler.removeCallbacks(hide); handler.removeCallbacks(commitSeek); upNext.setVisibility(View.GONE); ending = false; paused = false; seekTarget = -1; duration = 0;
        player.stop(); index = i; String path = queue.get(i); Library.Item item = library.byPath.get(path); spinner.setVisibility(View.VISIBLE);
        title.setText(item != null ? (item.isEpisode() ? item.show : item.title) : new java.io.File(path).getName());
        subtitle.setText(item == null ? "" : item.isEpisode() ? "SEASON " + item.season + "  •  EPISODE " + item.episode : item.year > 0 ? "MOVIE  •  " + item.year : "MOVIE");
        Progress.Entry saved = Progress.get(this, path); Media media = new Media(engine(this), Uri.fromFile(new java.io.File(path)));
        if (saved != null && saved.resumable()) { media.addOption(":start-time=" + (Math.max(0, saved.position - 3000) / 1000.0)); duration = saved.duration; toast("Resuming from " + Ui.clock(saved.position)); }
        media.setHWDecoderEnabled(true, false); player.setMedia(media); media.release(); player.play(); setOverlay(true); scheduleHide();
        int token = generation; handler.postDelayed(() -> { if (token == generation && spinner.getVisibility() == View.VISIBLE) onError(); }, STALL_MS);
    }

    private void update() {
        if (player == null) return; long length = duration > 0 ? duration : player.getLength(), time = seekTarget >= 0 ? seekTarget : Math.max(0, player.getTime());
        if (length > 0) { duration = length; seek.set(time / (float) length); elapsed.setText(Ui.clock(time)); remaining.setText("-" + Ui.clock(length - time)); }
        long now = System.currentTimeMillis();
        if (now - lastSave > SAVE_MS && player.isPlaying() && length > 0) { lastSave = now; Progress.save(this, queue.get(index), player.getTime(), length); }
    }
    private void save() { if (player != null && duration > 0 && !ending) Progress.save(this, queue.get(index), Math.max(0, player.getTime()), duration); }

    private void onEnded() {
        if (ending) return; ending = true; Progress.markWatched(this, queue.get(index), Math.max(1, duration));
        if (index + 1 >= queue.size()) { finish(); return; }
        Library.Item next = library.byPath.get(queue.get(index + 1)); String name = next == null ? "Next episode" : next.show + "  S" + next.season + " E" + next.episode; int token = generation;
        countdown(name, (int) UP_NEXT_SECONDS, token);
    }
    private void countdown(String name, int left, int token) {
        if (token != generation) return; if (left <= 0) { start(index + 1); return; }
        upNext.setText("UP NEXT\n" + name + "\nPlaying in " + left + "s   •   OK to play now   •   BACK to stop"); upNext.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> countdown(name, left - 1, token), 1000);
    }
    private void onError() {
        spinner.setVisibility(View.GONE); generation++; toast("Can't play this file");
        handler.postDelayed(() -> { if (index + 1 < queue.size()) start(index + 1); else finish(); }, 2500);
    }

    // ---- overlay & seeking ----
    private void setOverlay(boolean visible) { panel.animate().alpha(visible ? 1 : 0).setDuration(220).start(); scrim.animate().alpha(visible ? 1 : 0).setDuration(220).start(); }
    private void scheduleHide() { handler.removeCallbacks(hide); handler.postDelayed(hide, 4500); }
    private void toast(String message) { notice.setText(message); notice.animate().cancel(); notice.setAlpha(1); handler.removeCallbacks(hideNotice); handler.postDelayed(hideNotice, 2400); }
    private void togglePause() {
        paused = !paused; if (paused) { player.pause(); setOverlay(true); toast("❚❚  Paused"); handler.removeCallbacks(hide); } else { player.play(); toast("▶  Playing"); setOverlay(true); scheduleHide(); }
    }
    private void seekBy(int direction) {
        long now = System.currentTimeMillis(); streak = now - lastKey < 900 ? streak + 1 : 0; lastKey = now;
        long step = streak < 3 ? 10_000 : streak < 7 ? 30_000 : 60_000; if (duration <= 0) return;
        long base = seekTarget >= 0 ? seekTarget : player.getTime(); seekTarget = Math.max(0, Math.min(duration - 2000, base + direction * step));
        setOverlay(true); update(); toast((direction > 0 ? "▶▶  +" : "◀◀  -") + (step / 1000) + "s"); handler.removeCallbacks(commitSeek); handler.postDelayed(commitSeek, 450);
    }
    private void commitSeek() { if (seekTarget >= 0 && player != null) { player.setTime(seekTarget); seekTarget = -1; update(); if (!paused) scheduleHide(); } }

    private void tracks() {
        MediaPlayer.TrackDescription[] audio = player.getAudioTracks(), subs = player.getSpuTracks();
        String a = name(audio, player.getAudioTrack(), "Default"), s = name(subs, player.getSpuTrack(), "Off");
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("AUDIO & SUBTITLES")
                .setItems(new String[]{"Audio   —   " + a, "Subtitles   —   " + s}, (d, which) -> choose(which == 0)).setNegativeButton("Close", null).show();
    }
    private void choose(boolean audio) {
        MediaPlayer.TrackDescription[] list = audio ? player.getAudioTracks() : player.getSpuTracks(); if (list == null || list.length == 0) { toast(audio ? "No audio tracks" : "No subtitles in this file"); return; }
        int current = audio ? player.getAudioTrack() : player.getSpuTrack(), checked = 0; String[] names = new String[list.length];
        for (int i = 0; i < list.length; i++) { names[i] = list[i].id == -1 ? (audio ? "Disabled" : "Off") : list[i].name; if (list[i].id == current) checked = i; }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(audio ? "AUDIO" : "SUBTITLES").setSingleChoiceItems(names, checked, (d, which) -> {
            if (audio) player.setAudioTrack(list[which].id); else player.setSpuTrack(list[which].id); toast((audio ? "Audio: " : "Subtitles: ") + names[which]); d.dismiss();
        }).show();
    }
    private static String name(MediaPlayer.TrackDescription[] list, int id, String fallback) {
        if (list != null) for (MediaPlayer.TrackDescription t : list) if (t.id == id && id != -1) return t.name; return fallback;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (upNext.getVisibility() == View.VISIBLE) { generation++; start(index + 1); return true; } if (event.getRepeatCount() == 0) togglePause(); return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY: if (paused) togglePause(); return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE: if (!paused) togglePause(); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: seekBy(1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_MEDIA_REWIND: seekBy(-1); return true;
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK: tracks(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_INFO: if (panel.getAlpha() > .5f && !paused) setOverlay(false); else { setOverlay(true); if (!paused) scheduleHide(); } return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT: case KeyEvent.KEYCODE_CHANNEL_UP: if (index + 1 < queue.size()) { save(); start(index + 1); } else toast("Last item in the queue"); return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: case KeyEvent.KEYCODE_CHANNEL_DOWN: if (player.getTime() > 8000 || index == 0) { player.setTime(0); } else { save(); start(index - 1); } return true;
            default: return super.dispatchKeyEvent(event);
        }
    }
    @Override protected void onStop() { save(); generation++; handler.removeCallbacksAndMessages(null); if (player != null) player.stop(); super.onStop(); }
    @Override protected void onDestroy() { if (player != null) { player.setEventListener(null); player.detachViews(); player.release(); } super.onDestroy(); }
    private int dp(float n) { return Ui.dp(this, n); }

    /** Thin rounded seek bar with a knob. */
    private static final class SeekView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); private float value;
        SeekView(Context context) { super(context); }
        void set(float v) { value = Math.max(0, Math.min(1, v)); invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density, h = 6 * d, y = getHeight() / 2f, w = getWidth(), x = w * value;
            paint.setColor(0x44FFFFFF); c.drawRoundRect(0, y - h / 2, w, y + h / 2, h, h, paint);
            paint.setColor(Ui.ORANGE); c.drawRoundRect(0, y - h / 2, x, y + h / 2, h, h, paint); c.drawCircle(x, y, 8 * d, paint);
        }
    }
}
