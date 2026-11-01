package com.pylerium.player;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/** One show: seasons on the left, episodes with resume/watched state on the right. */
public class ShowActivity extends Activity {
    private Library library; private Library.Show show; private LinearLayout seasonList, episodeList; private TextView play, meta;
    private int season;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setStatusBarColor(0); getWindow().setNavigationBarColor(0xFF050709);
        library = Library.cached(this); String name = getIntent().getStringExtra("show");
        for (Library.Show s : library.shows) if (s.name.equalsIgnoreCase(name)) show = s;
        if (show == null) { finish(); return; }
        FrameLayout root = new FrameLayout(this); root.setBackgroundResource(R.drawable.home_backdrop);
        LinearLayout page = new LinearLayout(this); page.setPadding(dp(46), dp(30), dp(46), dp(24)); root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL); left.setPadding(0, 0, dp(36), 0);
        TextView brand = Ui.text(this, "PYLERIUM / TV SHOWS", 12, Ui.ORANGE, true); brand.setLetterSpacing(.16f); left.addView(brand, new LinearLayout.LayoutParams(-1, dp(28)));
        TextView title = Ui.text(this, show.name, 30, Ui.INK, true); title.setGravity(Gravity.TOP); title.setMaxLines(3); title.setEllipsize(TextUtils.TruncateAt.END); left.addView(title, new LinearLayout.LayoutParams(-1, -2));
        meta = Ui.text(this, "", 13, Ui.MUTED, false); meta.setPadding(0, dp(8), 0, dp(18)); left.addView(meta);
        play = Ui.button(this, ""); play.setOnClickListener(v -> MainActivity.play(this, library, Progress.nextUp(this, show))); left.addView(play, new LinearLayout.LayoutParams(-1, -2));
        TextView label = Ui.text(this, "SEASONS", 11, Ui.DIM, true); label.setLetterSpacing(.16f); label.setPadding(0, dp(22), 0, dp(6)); left.addView(label);
        ScrollView seasonScroll = new ScrollView(this); seasonScroll.setVerticalScrollBarEnabled(false); seasonList = new LinearLayout(this); seasonList.setOrientation(LinearLayout.VERTICAL); seasonScroll.addView(seasonList);
        left.addView(seasonScroll, new LinearLayout.LayoutParams(-1, 0, 1)); page.addView(left, new LinearLayout.LayoutParams(dp(320), -1));

        ScrollView episodeScroll = new ScrollView(this); episodeScroll.setVerticalScrollBarEnabled(false); episodeScroll.setClipToPadding(false); episodeScroll.setPadding(0, dp(6), 0, dp(6));
        episodeList = new LinearLayout(this); episodeList.setOrientation(LinearLayout.VERTICAL); episodeScroll.addView(episodeList, new ViewGroup.LayoutParams(-1, -2)); page.addView(episodeScroll, new LinearLayout.LayoutParams(0, -1, 1));
        setContentView(root); season = Progress.nextUp(this, show).season; refresh(true);
    }

    private void refresh(boolean focusPlay) {
        Library.Item next = Progress.nextUp(this, show); Progress.Entry entry = Progress.get(this, next.path);
        meta.setText(show.seasons.size() + (show.seasons.size() == 1 ? " season" : " seasons") + "  •  " + show.episodeCount() + " episodes");
        play.setText((entry != null && entry.resumable() ? "▶  RESUME  S" : "▶  PLAY  S") + next.season + " E" + next.episode);
        seasonList.removeAllViews();
        for (int number : show.seasons.keySet()) {
            TextView button = Ui.text(this, "Season " + number + "   (" + show.seasons.get(number).size() + ")", 16, number == season ? Ui.ORANGE : Ui.INK, number == season); button.setFocusable(true);
            button.setPadding(dp(16), dp(11), dp(16), dp(11)); button.setBackground(Ui.round(this, 0, 10));
            button.setOnFocusChangeListener((v, focused) -> { button.setBackground(focused ? Ui.outline(this, 0x2231231B, Ui.ORANGE, 2, 10) : Ui.round(this, 0, 10)); if (focused && season != number) { season = number; fill(); recolor(); } });
            button.setOnClickListener(v -> { if (episodeList.getChildCount() > 0) episodeList.getChildAt(0).requestFocus(); }); seasonList.addView(button, new LinearLayout.LayoutParams(-1, dp(48)));
        }
        fill(); if (focusPlay) play.requestFocus();
    }
    private void recolor() { int i = 0; for (int number : show.seasons.keySet()) ((TextView) seasonList.getChildAt(i++)).setTextColor(number == season ? Ui.ORANGE : Ui.INK); }

    private void fill() {
        episodeList.removeAllViews(); List<Library.Item> episodes = show.seasons.get(season); if (episodes == null) return;
        for (Library.Item item : episodes) {
            Progress.Entry e = Progress.get(this, item.path); boolean watched = e != null && e.watched();
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setFocusable(true); row.setClickable(true); row.setTag(item); row.setPadding(dp(16), 0, dp(20), 0);
            row.setBackground(Ui.round(this, 0x99101820, 12));
            row.setOnFocusChangeListener((v, focused) -> row.setBackground(focused ? Ui.outline(this, 0xFF31231B, Ui.ORANGE, 2, 12) : Ui.round(this, 0x99101820, 12)));
            row.setOnClickListener(v -> MainActivity.play(this, library, item));
            TextView number = Ui.text(this, String.valueOf(item.episode), 22, watched ? Ui.DIM : Ui.ORANGE, true); number.setGravity(Gravity.CENTER); row.addView(number, new LinearLayout.LayoutParams(dp(54), -1));
            LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = Ui.text(this, "Episode " + item.episode, 18, watched ? Ui.MUTED : Ui.INK, true); TextView file = Ui.text(this, new File(item.path).getName(), 11, Ui.DIM, false); file.setSingleLine(true); file.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            words.addView(name); words.addView(file); row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            String state = watched ? "✓ WATCHED" : e != null && e.resumable() ? Ui.clock(e.duration - e.position) + " LEFT" : Ui.size(item.size);
            row.addView(Ui.text(this, state, 12, watched ? 0xFF3FBF78 : e != null && e.resumable() ? Ui.ORANGE : Ui.MUTED, true));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(68)); lp.bottomMargin = dp(8); episodeList.addView(row, lp);
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        View focus = getCurrentFocus();
        if (event.getAction() == KeyEvent.ACTION_DOWN && (event.getKeyCode() == KeyEvent.KEYCODE_MENU || event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) && focus != null && focus.getTag() instanceof Library.Item) {
            Library.Item item = (Library.Item) focus.getTag(); Progress.Entry e = Progress.get(this, item.path); int index = episodeList.indexOfChild(focus);
            if (e != null && e.watched()) Progress.clear(this, item.path); else Progress.markWatched(this, item.path, Math.max(1, e == null ? 1 : e.duration));
            fill(); refreshPlay(); View again = episodeList.getChildAt(index); if (again != null) again.requestFocus(); return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private void refreshPlay() { Library.Item next = Progress.nextUp(this, show); Progress.Entry entry = Progress.get(this, next.path); play.setText((entry != null && entry.resumable() ? "▶  RESUME  S" : "▶  PLAY  S") + next.season + " E" + next.episode); }
    @Override protected void onResume() {
        super.onResume(); if (show == null || episodeList == null) return;
        View f = getCurrentFocus(); int index = f != null ? episodeList.indexOfChild(f) : -1; fill(); refreshPlay(); if (index >= 0 && episodeList.getChildAt(index) != null) episodeList.getChildAt(index).requestFocus();
    }
    private int dp(float v) { return Ui.dp(this, v); }
}
