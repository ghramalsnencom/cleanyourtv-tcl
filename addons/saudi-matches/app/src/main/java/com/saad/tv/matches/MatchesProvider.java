package com.saad.tv.matches;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Today's games and prayer times for the home screen, read-only. The launcher has no network permission, so this
 * app does the fetching: a query answers at once from the cache and, when the cache is old, refreshes it in the
 * background and calls notifyChange, so a launcher observing the URI asks again.
 *
 * content://com.saad.tv.matches.feed/today   one row per game, live ones first, logos as small PNG blobs
 * content://com.saad.tv.matches.feed/prayer  one row: fajr, sunrise, dhuhr, asr, maghrib, isha ("HH:mm"), hijri
 */
public class MatchesProvider extends ContentProvider {
    static final String AUTHORITY = "com.saad.tv.matches.feed";
    static final Uri TODAY = Uri.parse("content://" + AUTHORITY + "/today");
    static final Uri PRAYER = Uri.parse("content://" + AUTHORITY + "/prayer");
    private static final String[] GAME_COLUMNS = {"home", "away", "home_score", "away_score", "state", "clock",
            "time", "comp", "grp", "home_logo", "away_logo"};
    private static final String[] PRAYER_COLUMNS = {"fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha", "hijri"};

    private final AtomicBoolean gamesBusy = new AtomicBoolean(), prayerBusy = new AtomicBoolean();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        String path = uri.getLastPathSegment();
        if ("today".equals(path)) return games();
        if ("prayer".equals(path)) return prayer();
        return null;
    }

    // --- games ---

    private Cursor games() {
        SharedPreferences p = prefs();
        String day = p.getString("games_day", "");
        long age = System.currentTimeMillis() - p.getLong("games_at", 0);
        boolean live = p.getBoolean("games_live", false);
        if (!Data.day(0).equals(day) || age > (live ? 60_000L : 5 * 60_000L)) refreshGames();
        MatrixCursor c = new MatrixCursor(GAME_COLUMNS);
        if (!Data.day(0).equals(day)) return c;   // yesterday's games are not today's
        try {
            JSONArray a = new JSONArray(p.getString("games", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject g = a.getJSONObject(i);
                c.addRow(new Object[] {g.optString("home"), g.optString("away"), g.optString("hs"), g.optString("as"),
                        g.optString("state"), g.optString("clock"), g.optLong("time"), g.optString("comp"),
                        g.optInt("grp"), logoBytes(g.optString("hl")), logoBytes(g.optString("al"))});
            }
        } catch (Exception e) {
            Log.w(Data.TAG, "games cursor: " + e);
        }
        return c;
    }

    private void refreshGames() {
        if (!gamesBusy.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                List<Data.Match> list = Data.today();
                JSONArray a = new JSONArray();
                boolean live = false;
                for (Data.Match m : list) {
                    live |= m.live();
                    JSONObject g = new JSONObject();
                    g.put("home", Data.ar(m.home)).put("away", Data.ar(m.away));
                    g.put("hs", Data.num(m.homeScore)).put("as", Data.num(m.awayScore));
                    g.put("state", m.state).put("clock", m.clock == null ? "" : m.clock);
                    g.put("time", m.time).put("comp", m.comp == null ? "" : m.comp).put("grp", m.group);
                    g.put("hl", saveLogo(m.homeLogo)).put("al", saveLogo(m.awayLogo));
                    a.put(g);
                }
                prefs().edit().putString("games", a.toString()).putString("games_day", Data.day(0))
                        .putLong("games_at", System.currentTimeMillis()).putBoolean("games_live", live).apply();
                notifyChange(TODAY);
            } catch (Exception e) {
                Log.w(Data.TAG, "games refresh: " + e);
                // Try again in a minute rather than on every query.
                prefs().edit().putLong("games_at", System.currentTimeMillis() - 4 * 60_000L).apply();
            } finally {
                gamesBusy.set(false);
            }
        }).start();
    }

    // --- prayer ---

    private Cursor prayer() {
        SharedPreferences p = prefs();
        if (!Data.day(0).equals(p.getString("prayer_day", ""))) refreshPrayer();
        MatrixCursor c = new MatrixCursor(PRAYER_COLUMNS);
        String times = p.getString("prayer_times", null);
        if (times == null || !Data.day(0).equals(p.getString("prayer_day", ""))) return c;
        String[] t = times.split(",");
        if (t.length != 6) return c;
        c.addRow(new Object[] {t[0], t[1], t[2], t[3], t[4], t[5], p.getString("prayer_hijri", "")});
        return c;
    }

    private void refreshPrayer() {
        if (!prayerBusy.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                Data.Prayers pr = Data.prayers();
                prefs().edit().putString("prayer_times", String.join(",", pr.times)).putString("prayer_hijri", pr.hijri)
                        .putString("prayer_day", pr.day).apply();
                notifyChange(PRAYER);
            } catch (Exception e) {
                Log.w(Data.TAG, "prayer refresh: " + e);
            } finally {
                prayerBusy.set(false);
            }
        }).start();
    }

    // --- logos: small PNGs in the cache folder, by a hash of the URL ---

    private String saveLogo(String url) {
        if (url == null || url.isEmpty()) return "";
        String name = Integer.toHexString(url.hashCode()) + ".png";
        File f = new File(logoDir(), name);
        if (f.exists()) return name;
        Bitmap b = Data.logo(url);
        if (b == null) return "";
        try (FileOutputStream out = new FileOutputStream(f)) {
            Bitmap small = Bitmap.createScaledBitmap(b, 64, 64 * b.getHeight() / Math.max(1, b.getWidth()), true);
            small.compress(Bitmap.CompressFormat.PNG, 100, out);
            return name;
        } catch (Exception e) {
            Log.w(Data.TAG, "logo save: " + e);
            return "";
        }
    }

    private byte[] logoBytes(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return Files.readAllBytes(new File(logoDir(), name).toPath());
        } catch (Exception e) {
            return null;
        }
    }

    private File logoDir() {
        File d = new File(getContext().getCacheDir(), "logos");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("feed", Context.MODE_PRIVATE);
    }

    private void notifyChange(Uri uri) {
        getContext().getContentResolver().notifyChange(uri, null);
    }

    // Read-only.
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
