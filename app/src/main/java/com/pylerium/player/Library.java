package com.pylerium.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The user's media: folders they chose, scanned into movies and TV shows, plus resume/watched state. */
final class Library {
    static final class Item {
        String path, title, show; int year, season, episode; long size;
        boolean isEpisode() { return show != null; }
        String label() { return isEpisode() ? "Episode " + episode : title; }
    }
    static final class Show {
        String name; final TreeMap<Integer, List<Item>> seasons = new TreeMap<>();
        int episodeCount() { int n = 0; for (List<Item> list : seasons.values()) n += list.size(); return n; }
        List<Item> all() { List<Item> out = new ArrayList<>(); for (List<Item> list : seasons.values()) out.addAll(list); return out; }
    }

    final List<Item> movies = new ArrayList<>();
    final List<Show> shows = new ArrayList<>();
    final Map<String, Item> byPath = new LinkedHashMap<>();

    private static final String PREFS = "library", FOLDERS = "folders", CACHE = "cache_v1";
    private static final Pattern EPISODE = Pattern.compile("(?i)^(.*?)[\\s._\\-\\[(]*(?:s(\\d{1,2})[\\s._\\-]*e(\\d{1,3})|(\\d{1,2})x(\\d{2,3}))(?![0-9a-z]).*$");
    private static final Pattern SEASON_DIR = Pattern.compile("(?i)^(?:season|series|s)[\\s._-]*(\\d{1,2})$");
    private static final Pattern YEAR = Pattern.compile("^(.*?)[\\s._(\\[-]+((?:19|20)\\d\\d)(?:[\\s._)\\]-].*|)$");
    private static final Pattern JUNK = Pattern.compile("(?i)[\\s._\\-\\[(](480p|576p|720p|1080p|2160p|4k|uhd|bluray|blu-ray|brrip|bdrip|webrip|web-dl|webdl|dvdrip|hdtv|hdrip|x264|x265|h264|h265|hevc|xvid|remux|proper|repack)\\b.*$");

    static List<String> folders(Context context) {
        List<String> out = new ArrayList<>();
        try { JSONArray array = new JSONArray(prefs(context).getString(FOLDERS, "[]")); for (int i = 0; i < array.length(); i++) out.add(array.getString(i)); } catch (Exception ignored) { }
        return out;
    }
    static void saveFolders(Context context, List<String> folders) { prefs(context).edit().putString(FOLDERS, new JSONArray(folders).toString()).apply(); }
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** Instant: the library as of the last scan. */
    static Library cached(Context context) {
        Library library = new Library();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(CACHE, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i); Item item = new Item();
                item.path = o.getString("p"); item.title = o.getString("t"); item.show = o.optString("s", null); item.year = o.optInt("y"); item.season = o.optInt("n"); item.episode = o.optInt("e"); item.size = o.optLong("z");
                library.add(item);
            }
        } catch (Exception ignored) { }
        library.finish(); return library;
    }
    /** Walks the chosen folders. Slow on big drives: call off the main thread. */
    static Library scan(Context context) {
        Library library = new Library(); List<Item> found = new ArrayList<>();
        for (String folder : folders(context)) walk(new File(folder), new File(folder), found, 0);
        for (Item item : found) library.add(item);
        library.finish();
        JSONArray array = new JSONArray();
        try { for (Item item : library.byPath.values()) { JSONObject o = new JSONObject(); o.put("p", item.path); o.put("t", item.title); if (item.show != null) o.put("s", item.show); o.put("y", item.year); o.put("n", item.season); o.put("e", item.episode); o.put("z", item.size); array.put(o); } } catch (Exception ignored) { }
        prefs(context).edit().putString(CACHE, array.toString()).apply();
        return library;
    }

    private void add(Item item) { byPath.put(item.path, item); }
    private void finish() {
        movies.clear(); shows.clear(); Map<String, Show> index = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Item item : byPath.values()) {
            if (!item.isEpisode()) { movies.add(item); continue; }
            Show show = index.get(item.show); if (show == null) { show = new Show(); show.name = item.show; index.put(item.show, show); }
            List<Item> season = show.seasons.get(item.season); if (season == null) { season = new ArrayList<>(); show.seasons.put(item.season, season); } season.add(item);
        }
        for (Show show : index.values()) { for (List<Item> list : show.seasons.values()) Collections.sort(list, (a, b) -> Integer.compare(a.episode, b.episode)); shows.add(show); }
        Collections.sort(movies, (a, b) -> a.title.compareToIgnoreCase(b.title));
    }
    boolean isEmpty() { return byPath.isEmpty(); }

    private static void walk(File root, File folder, List<Item> out, int depth) {
        if (depth > 10 || out.size() > 5000) return; File[] children = folder.listFiles(); if (children == null) return;
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File child : children) {
            String name = child.getName(); if (name.startsWith(".")) continue;
            if (child.isDirectory()) walk(root, child, out, depth + 1);
            else if (isVideo(name) && child.length() > 30L * 1024 * 1024 && !name.toLowerCase(Locale.ROOT).contains("sample")) out.add(describe(root, child));
        }
    }
    static boolean isVideo(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".mov") || n.endsWith(".m4v") || n.endsWith(".webm") || n.endsWith(".ts") || n.endsWith(".wmv");
    }

    /** Works out whether a file is a movie or an episode, and what it should be called. */
    static Item describe(File root, File file) {
        Item item = new Item(); item.path = file.getPath(); item.size = file.length(); String base = file.getName(); int dot = base.lastIndexOf('.'); if (dot > 0) base = base.substring(0, dot);
        File parent = file.getParentFile(); Matcher episode = EPISODE.matcher(base);
        int season = -1, number = 0; String show = null;
        if (episode.matches()) {
            show = episode.group(1); season = parseInt(episode.group(2) != null ? episode.group(2) : episode.group(4)); number = parseInt(episode.group(3) != null ? episode.group(3) : episode.group(5));
        } else if (parent != null && !parent.equals(root)) {
            Matcher dir = SEASON_DIR.matcher(parent.getName()); Matcher number1 = Pattern.compile("^\\D*(\\d{1,3})\\D.*").matcher(base + " ");
            if (dir.matches() && number1.matches()) { season = parseInt(dir.group(1)); number = parseInt(number1.group(1)); show = ""; }
        }
        if (season >= 0 && number > 0) {
            String name = tidy(show == null ? "" : show);
            if (name.isEmpty() && parent != null) { File up = SEASON_DIR.matcher(parent.getName()).matches() ? parent.getParentFile() : parent; if (up != null && !up.equals(root)) name = tidy(up.getName()); }
            if (name.isEmpty()) name = tidy(root.getName());
            item.show = name; item.season = season; item.episode = number; item.title = name + " S" + two(season) + "E" + two(number); return item;
        }
        String cleaned = JUNK.matcher(base).replaceFirst(""); Matcher year = YEAR.matcher(cleaned);
        if (year.matches()) { item.year = parseInt(year.group(2)); cleaned = year.group(1); }
        item.title = tidy(cleaned); if (item.title.isEmpty()) item.title = tidy(base); return item;
    }
    private static String tidy(String value) {
        String out = JUNK.matcher(value).replaceFirst("").replaceAll("[._]+", " ").replaceAll("\\s*[\\[(].*$", "").replaceAll("\\s+", " ").trim();
        while (out.endsWith("-") || out.endsWith(".")) out = out.substring(0, out.length() - 1).trim(); return out;
    }
    private static int parseInt(String value) { try { return Integer.parseInt(value); } catch (Exception e) { return 0; } }
    private static String two(int n) { return n < 10 ? "0" + n : String.valueOf(n); }
}
