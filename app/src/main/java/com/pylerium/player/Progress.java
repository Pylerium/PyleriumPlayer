package com.pylerium.player;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Resume positions and watched state, one preference entry per file: "position,duration,lastPlayed". */
final class Progress {
    static final class Entry {
        final String path; final long position, duration, played;
        Entry(String path, long position, long duration, long played) { this.path = path; this.position = position; this.duration = duration; this.played = played; }
        float fraction() { return duration <= 0 ? 0 : Math.min(1f, position / (float) duration); }
        boolean watched() { return duration > 0 && position >= duration * .93; }
        boolean resumable() { return position > 15_000 && !watched(); }
    }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("progress", Context.MODE_PRIVATE); }

    static Entry get(Context context, String path) {
        String raw = prefs(context).getString("p|" + path, null); if (raw == null) return null;
        try { String[] p = raw.split(","); return new Entry(path, Long.parseLong(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2])); } catch (Exception e) { return null; }
    }
    static void save(Context context, String path, long position, long duration) {
        if (duration <= 0) return; prefs(context).edit().putString("p|" + path, position + "," + duration + "," + System.currentTimeMillis()).apply();
    }
    static void markWatched(Context context, String path, long duration) { save(context, path, duration, duration); }
    static void clear(Context context, String path) { prefs(context).edit().remove("p|" + path).apply(); }

    /** Unfinished items, most recently played first. */
    static List<Entry> continueWatching(Context context, Library library) {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : prefs(context).getAll().entrySet()) {
            if (!e.getKey().startsWith("p|")) continue; String path = e.getKey().substring(2); Entry entry = get(context, path);
            if (entry != null && entry.resumable() && library.byPath.containsKey(path)) out.add(entry);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.played, a.played)); return out;
    }
    /** For a show: the episode to play next (resume the in-progress one, otherwise first unwatched). */
    static Library.Item nextUp(Context context, Library.Show show) {
        List<Library.Item> all = show.all(); Library.Item firstUnwatched = null, resume = null; long newest = -1;
        for (Library.Item item : all) {
            Entry entry = get(context, item.path);
            if (entry != null && entry.resumable() && entry.played > newest) { newest = entry.played; resume = item; }
            if (firstUnwatched == null && (entry == null || !entry.watched())) firstUnwatched = item;
        }
        return resume != null ? resume : firstUnwatched != null ? firstUnwatched : all.get(0);
    }
}
