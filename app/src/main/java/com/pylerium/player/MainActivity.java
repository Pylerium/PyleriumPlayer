package com.pylerium.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Home: continue watching, movies and TV shows as poster rows, with a hero panel describing whatever has focus. */
public class MainActivity extends Activity {
    private static final int PICK_FOLDER = 60;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Library library = new Library();
    private LinearLayout rows, empty; private ScrollView scroller;
    private TextView status, heroTitle, heroMeta, heroDetail, emptyBody;
    private Card focused; private boolean scanning, pendingPick; private int restoreRow = -1, restorePos;

    private static final class Card {
        Library.Item item; Library.Show show; String title, subtitle, badge = ""; float fraction; boolean watched;
        String meta, detail;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setStatusBarColor(0); getWindow().setNavigationBarColor(0xFF050709);
        FrameLayout root = new FrameLayout(this); root.setBackgroundResource(R.drawable.home_backdrop);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(32), dp(20), 0, 0);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(14), 0, dp(46), 0);
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.pylerium_logo); top.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView brand = Ui.text(this, "PYLERIUM / PLAYER", 19, Ui.ORANGE, true); brand.setLetterSpacing(.14f);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, dp(44), 1); brandLp.setMarginStart(dp(12)); top.addView(brand, brandLp);
        status = Ui.text(this, "", 12, Ui.MUTED, false); status.setLetterSpacing(.06f); top.addView(status); page.addView(top);

        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(14), dp(10), dp(46), 0);
        heroTitle = Ui.text(this, "", 32, Ui.INK, true); heroTitle.setSingleLine(true); heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroMeta = Ui.text(this, "", 14, Ui.MUTED, false); heroDetail = Ui.text(this, "", 13, Ui.CYAN, true); heroDetail.setLetterSpacing(.04f);
        hero.addView(heroTitle, new LinearLayout.LayoutParams(-1, dp(46))); hero.addView(heroMeta, new LinearLayout.LayoutParams(-1, dp(26))); hero.addView(heroDetail, new LinearLayout.LayoutParams(-1, dp(26)));
        page.addView(hero, new LinearLayout.LayoutParams(-1, dp(112)));

        scroller = new ScrollView(this); scroller.setVerticalScrollBarEnabled(false); scroller.setClipChildren(true); scroller.setClipToPadding(false);
        rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); rows.setClipChildren(false); rows.setPadding(0, 0, 0, dp(40)); scroller.addView(rows, new ViewGroup.LayoutParams(-1, -2));
        page.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));

        empty = new LinearLayout(this); empty.setOrientation(LinearLayout.VERTICAL); empty.setGravity(Gravity.CENTER); empty.setVisibility(View.GONE);
        TextView welcome = Ui.text(this, "Welcome to Pylerium Player", 30, Ui.INK, true); welcome.setGravity(Gravity.CENTER);
        emptyBody = Ui.text(this, "Add a folder on your TV or drive.\nMovies and TV shows are sorted automatically.", 16, Ui.MUTED, false); emptyBody.setGravity(Gravity.CENTER); emptyBody.setPadding(0, dp(10), 0, dp(26));
        TextView add = Ui.button(this, "ADD A FOLDER"); add.setOnClickListener(v -> addFolder()); add.setId(View.generateViewId());
        empty.addView(welcome); empty.addView(emptyBody); empty.addView(add, new LinearLayout.LayoutParams(-2, -2));
        root.addView(empty, new FrameLayout.LayoutParams(-1, -1)); setContentView(root);

        library = Library.cached(this); render(); rescan();
    }

    private void rescan() {
        if (scanning || Library.folders(this).isEmpty()) return; scanning = true; status.setText("SCANNING…");
        worker.execute(() -> { Library scanned = Library.scan(this); runOnUiThread(() -> { scanning = false; if (isDestroyed()) return; library = scanned; render(); }); });
    }

    private void render() {
        rows.removeAllViews(); focused = null;
        boolean none = library.isEmpty(); empty.setVisibility(none ? View.VISIBLE : View.GONE); scroller.setVisibility(none ? View.GONE : View.VISIBLE);
        status.setText(scanning ? "SCANNING…" : library.movies.size() + " MOVIES  •  " + library.shows.size() + " SHOWS");
        if (none) {
            boolean hasFolders = !Library.folders(this).isEmpty(); emptyBody.setText(hasFolders ? "No videos found in your folders yet.\nAdd another folder, or press MENU to rescan." : "Add a folder on your TV or drive.\nMovies and TV shows are sorted automatically.");
            heroTitle.setText(""); heroMeta.setText(""); heroDetail.setText(""); empty.getChildAt(2).requestFocus(); return;
        }
        List<Card> resume = new ArrayList<>(), movies = new ArrayList<>(), shows = new ArrayList<>();
        for (Progress.Entry entry : Progress.continueWatching(this, library)) { Library.Item item = library.byPath.get(entry.path); Card c = card(item); resume.add(c); }
        for (Library.Item item : library.movies) movies.add(card(item));
        for (Library.Show show : library.shows) shows.add(card(show));
        int row = 0; row = addRow("CONTINUE WATCHING", resume, row); row = addRow("MOVIES", movies, row); addRow("TV SHOWS", shows, row);
        View target = restoreRow >= 0 && restoreRow < rows.getChildCount() / 2 ? ((ViewGroup) rows.getChildAt(restoreRow * 2 + 1)) : null;
        if (target instanceof RecyclerView) { RecyclerView list = (RecyclerView) target; int pos = Math.min(restorePos, list.getAdapter().getItemCount() - 1); list.scrollToPosition(pos); list.post(() -> focusAt(list, pos)); }
        else if (rows.getChildCount() > 1) { RecyclerView first = (RecyclerView) rows.getChildAt(1); first.post(() -> focusAt(first, 0)); }
        restoreRow = -1;
    }
    private void focusAt(RecyclerView list, int position) {
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position); if (holder != null) holder.itemView.requestFocus(); else list.post(() -> { RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(position); if (h != null) h.itemView.requestFocus(); });
    }

    private Card card(Library.Item item) {
        Card c = new Card(); c.item = item; c.title = item.isEpisode() ? item.show : item.title; Progress.Entry entry = Progress.get(this, item.path);
        c.subtitle = item.isEpisode() ? "Season " + item.season + "  •  Episode " + item.episode : item.year > 0 ? String.valueOf(item.year) : "Movie";
        if (entry != null) { c.watched = entry.watched(); c.fraction = c.watched ? 0 : entry.fraction(); }
        c.meta = (item.isEpisode() ? "TV Show  •  S" + item.season + " E" + item.episode : "Movie" + (item.year > 0 ? "  •  " + item.year : "")) + "  •  " + Ui.size(item.size);
        c.detail = entry != null && entry.resumable() ? "RESUME AT " + Ui.clock(entry.position) + "  •  " + Ui.clock(entry.duration - entry.position) + " LEFT" : c.watched ? "WATCHED" : "PRESS OK TO PLAY";
        return c;
    }
    private Card card(Library.Show show) {
        Card c = new Card(); c.show = show; c.title = show.name; int seasons = show.seasons.size(), episodes = show.episodeCount(), done = 0;
        for (Library.Item item : show.all()) { Progress.Entry e = Progress.get(this, item.path); if (e != null && e.watched()) done++; }
        c.subtitle = seasons + (seasons == 1 ? " season" : " seasons"); c.badge = episodes + " EP"; c.fraction = done == episodes ? 0 : done / (float) episodes; c.watched = done == episodes && episodes > 0;
        Library.Item next = Progress.nextUp(this, show); Progress.Entry e = Progress.get(this, next.path);
        c.meta = "TV Show  •  " + seasons + (seasons == 1 ? " season" : " seasons") + "  •  " + episodes + " episodes";
        c.detail = c.watched ? "ALL EPISODES WATCHED" : (e != null && e.resumable() ? "RESUME " : "NEXT UP  ") + "S" + next.season + " E" + next.episode;
        return c;
    }

    private int addRow(String label, List<Card> cards, int rowIndex) {
        if (cards.isEmpty()) return rowIndex;
        TextView title = Ui.text(this, label, 12, Ui.MUTED, true); title.setLetterSpacing(.16f); title.setPadding(dp(14), dp(8), 0, 0); rows.addView(title, new LinearLayout.LayoutParams(-1, dp(34)));
        RecyclerView list = new RecyclerView(this); list.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)); list.setClipChildren(false); list.setClipToPadding(false);
        list.setPadding(dp(14), dp(16), dp(46), dp(16)); list.setItemAnimator(null); list.setHasFixedSize(true); list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setAdapter(new CardAdapter(cards, rowIndex)); rows.addView(list, new LinearLayout.LayoutParams(-1, dp(PosterView.HEIGHT + 34))); return rowIndex + 1;
    }

    private final class CardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Card> cards; private final int rowIndex;
        CardAdapter(List<Card> cards, int rowIndex) { this.cards = cards; this.rowIndex = rowIndex; }
        @Override public int getItemCount() { return cards.size(); }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            PosterView view = new PosterView(MainActivity.this); RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(dp(PosterView.WIDTH), dp(PosterView.HEIGHT)); lp.setMarginEnd(dp(16)); view.setLayoutParams(lp);
            return new RecyclerView.ViewHolder(view) { };
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Card c = cards.get(position); PosterView view = (PosterView) holder.itemView; view.bind(c.title, c.subtitle, c.badge, c.fraction, c.watched);
            view.setOnFocusChangeListener((v, has) -> { if (has) { focused = c; restoreRow = rowIndex; restorePos = holder.getAdapterPosition(); hero(c); } });
            view.setOnClickListener(v -> open(c));
        }
    }
    private void hero(Card c) { heroTitle.setText(c.title); heroMeta.setText(c.meta); heroDetail.setText(c.detail); }
    private void open(Card c) {
        if (c.show != null) startActivity(new Intent(this, ShowActivity.class).putExtra("show", c.show.name)); else play(this, library, c.item);
    }
    /** Starts playback; episodes queue the rest of the show so the next one follows automatically. */
    static void play(Context context, Library library, Library.Item item) {
        ArrayList<String> queue = new ArrayList<>(); queue.add(item.path);
        if (item.isEpisode()) { boolean after = false; for (Library.Show show : library.shows) if (show.name.equalsIgnoreCase(item.show)) for (Library.Item e : show.all()) { if (after) queue.add(e.path); if (e == item) after = true; } }
        context.startActivity(new Intent(context, PlayerActivity.class).putStringArrayListExtra("queue", queue));
    }

    // ---- folders & menu ----
    private void menu() {
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        labels.add("Add a folder"); actions.add(this::addFolder);
        labels.add("Rescan library"); actions.add(() -> { scanning = false; rescan(); });
        if (focused != null && focused.item != null) {
            Progress.Entry e = Progress.get(this, focused.item.path); boolean watched = e != null && e.watched(); Library.Item item = focused.item;
            labels.add(watched ? "Mark as unwatched" : "Mark as watched"); actions.add(() -> { if (watched) Progress.clear(this, item.path); else Progress.markWatched(this, item.path, Math.max(1, e == null ? 1 : e.duration)); render(); });
        }
        if (!Library.folders(this).isEmpty()) { labels.add("Remove a folder…"); actions.add(this::removeFolder); }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("LIBRARY").setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run()).setNegativeButton("Close", null).show();
    }
    private void removeFolder() {
        List<String> folders = Library.folders(this);
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("REMOVE A FOLDER").setItems(folders.toArray(new String[0]), (d, which) -> {
            folders.remove(which); Library.saveFolders(this, folders); library = Library.cached(this); worker.execute(() -> { Library scanned = Library.scan(this); runOnUiThread(() -> { library = scanned; render(); }); });
        }).setNegativeButton("Cancel", null).show();
    }
    private void addFolder() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            pendingPick = true;
            try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (Exception ignored) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
            Toast.makeText(this, "Allow file access, then return to Pylerium Player", Toast.LENGTH_LONG).show(); return;
        }
        pendingPick = false; startActivityForResult(new Intent(this, FileBrowserActivity.class).putExtra("mode", "folder"), PICK_FOLDER);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if (request != PICK_FOLDER || result != RESULT_OK || data == null) return;
        ArrayList<String> paths = data.getStringArrayListExtra("paths"); if (paths == null || paths.isEmpty()) return;
        List<String> folders = Library.folders(this); if (!folders.contains(paths.get(0))) folders.add(paths.get(0)); Library.saveFolders(this, folders);
        Toast.makeText(this, "Added " + new File(paths.get(0)).getName(), Toast.LENGTH_SHORT).show(); scanning = false; rescan();
    }
    @Override protected void onResume() {
        super.onResume();
        if (pendingPick && (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager())) { pendingPick = false; addFolder(); return; }
        if (!library.isEmpty() && !scanning) render();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && (event.getKeyCode() == KeyEvent.KEYCODE_MENU || event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS)) { menu(); return true; }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
    private int dp(float v) { return Ui.dp(this, v); }
}
