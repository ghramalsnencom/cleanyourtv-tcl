package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The prayer line and today's games under the clock, read from the "مباريات اليوم" add-on
 * (com.saad.tv.matches, content://com.saad.tv.matches.feed). The launcher itself still has no network: the
 * add-on fetches, and this only reads its provider off the main thread, on resume, once a minute and when the
 * add-on says the data changed. With the add-on missing both stay hidden.
 *
 * The game cards sit under the prayer line and take focus: Up from the dock lands on them, Up again goes on to
 * the top bar, Down back to the dock, and OK on a card (or on "+N") opens the add-on's matches page.
 */
final class MatchStrip {
    /** What the cards ask of the home screen when focus leaves them. */
    interface Host {
        void onStripFocused();
        void onStripUp();
        void onStripDown();
    }

    private static final String TAG = "TvHomeMatches";
    private static final Uri TODAY = Uri.parse("content://com.saad.tv.matches.feed/today");
    private static final Uri PRAYER = Uri.parse("content://com.saad.tv.matches.feed/prayer");
    private static final ComponentName PAGE = new ComponentName("com.saad.tv.matches", "com.saad.tv.matches.MainActivity");
    private static final int MAX_CHIPS = 3;
    private static final int AFTER_ADHAN = 30;
    private static final String[] PRAYER_AR = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};

    /** One row of the provider, already in Arabic. */
    private static final class Game {
        String home, away, homeScore, awayScore, state, clock;
        long time;
        int group;
        Bitmap homeLogo, awayLogo;
    }

    private final Context ctx;
    private final Host host;
    private final LinearLayout panel;
    private final LinearLayout row;
    private final TextView prayerLine;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final float density;
    private int[] prayerMinutes;
    private boolean running, loading;

    private final ContentObserver observer = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            if (running) load();
        }
    };

    MatchStrip(Context ctx, LinearLayout panel, Host host) {
        this.ctx = ctx;
        this.panel = panel;
        this.host = host;
        density = ctx.getResources().getDisplayMetrics().density;

        prayerLine = new TextView(ctx);
        prayerLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        prayerLine.setTextColor(0xE6FFFFFF);
        prayerLine.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        prayerLine.setSingleLine(true);
        prayerLine.setGravity(Gravity.CENTER_VERTICAL);
        prayerLine.setPadding(dp(12), dp(5), dp(12), dp(5));
        prayerLine.setBackground(round(dp(14), 0x1A8BD8FF, 0x338BD8FF, dp(1)));
        // The panel starts 10dp left of the content line (activity_main.xml), the room the cards' padding needs:
        // the root clips each child to its own bounds, so a zoomed card can only grow inside the panel.
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.leftMargin = dp(10);
        panel.addView(prayerLine, plp);

        row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        // Room around the cards for the focused one's zoom and rim: without it the parent cut their edges.
        row.setPadding(dp(10), dp(6), dp(10), dp(6));
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        row.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(4);
        panel.addView(row, lp);
    }

    /** Fades the prayer line out once the grid scrolls up under the clock, and back in at the dock. */
    void follow(RecyclerView grid) {
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                float want = rv.computeVerticalScrollOffset() < dp(24) ? 1f : 0f;
                if (panel.getAlpha() != want) {
                    panel.animate().cancel();
                    panel.animate().alpha(want).setDuration(want > 0 ? 220 : 120).start();
                }
            }
        });
    }

    void start() {
        if (running) return;
        running = true;
        try {
            ctx.getContentResolver().registerContentObserver(TODAY, false, observer);
            ctx.getContentResolver().registerContentObserver(PRAYER, false, observer);
        } catch (Exception e) {
            Log.w(TAG, "observe: " + e);
        }
        load();
    }

    void stop() {
        running = false;
        ctx.getContentResolver().unregisterContentObserver(observer);
    }

    /** Once a minute with the clock: the prayer line counts down, and the provider decides whether to refetch. */
    void tick() {
        renderPrayer();
        if (running) load();
    }

    /** True when there is a card to move to from the dock: shown, and not faded out by a scrolled grid. */
    boolean canFocus() {
        return panel.getVisibility() == View.VISIBLE && panel.getAlpha() > 0.5f
                && row.getVisibility() == View.VISIBLE && row.getChildCount() > 0;
    }

    void focusFirst() {
        if (canFocus()) row.getChildAt(0).requestFocus();
    }

    private void load() {
        if (loading) return;
        loading = true;
        new Thread(() -> {
            List<Game> games = null;
            int[] minutes = null;
            try {
                games = readGames();
                minutes = readPrayer();
            } catch (Exception e) {
                Log.w(TAG, "read: " + e);   // add-on missing or broken: the panel stays as it is
            }
            final List<Game> g = games;
            final int[] m = minutes;
            main.post(() -> {
                loading = false;
                if (!running) return;
                if (m != null) prayerMinutes = m;
                renderPrayer();
                if (g != null) renderGames(g);
            });
        }).start();
    }

    private List<Game> readGames() {
        try (Cursor c = ctx.getContentResolver().query(TODAY, null, null, null, null)) {
            if (c == null) return null;
            List<Game> out = new ArrayList<>();
            while (c.moveToNext()) {
                Game g = new Game();
                g.home = c.getString(c.getColumnIndexOrThrow("home"));
                g.away = c.getString(c.getColumnIndexOrThrow("away"));
                g.homeScore = c.getString(c.getColumnIndexOrThrow("home_score"));
                g.awayScore = c.getString(c.getColumnIndexOrThrow("away_score"));
                g.state = c.getString(c.getColumnIndexOrThrow("state"));
                g.clock = c.getString(c.getColumnIndexOrThrow("clock"));
                g.time = c.getLong(c.getColumnIndexOrThrow("time"));
                g.group = c.getInt(c.getColumnIndexOrThrow("grp"));
                g.homeLogo = bitmap(c.getBlob(c.getColumnIndexOrThrow("home_logo")));
                g.awayLogo = bitmap(c.getBlob(c.getColumnIndexOrThrow("away_logo")));
                out.add(g);
            }
            return out;
        }
    }

    private int[] readPrayer() {
        try (Cursor c = ctx.getContentResolver().query(PRAYER, null, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            int[] m = new int[6];
            for (int i = 0; i < 6; i++) {
                String v = LatinTextClock.latin(c.getString(i)).toString();
                m[i] = Integer.parseInt(v.substring(0, 2)) * 60 + Integer.parseInt(v.substring(3, 5));
            }
            return m;
        }
    }

    private static Bitmap bitmap(byte[] b) {
        return b == null || b.length == 0 ? null : BitmapFactory.decodeByteArray(b, 0, b.length);
    }

    // --- drawing ---

    private void renderPrayer() {
        if (prayerMinutes == null) {
            panel.setVisibility(View.GONE);
            return;
        }
        Calendar c = Calendar.getInstance();
        prayerLine.setText("◷  " + line(prayerMinutes, c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)));
        panel.setVisibility(View.VISIBLE);
    }

    private void renderGames(List<Game> games) {
        boolean hadFocus = row.hasFocus();
        int focusedIndex = hadFocus ? row.indexOfChild(row.getFocusedChild()) : -1;
        row.removeAllViews();
        if (games.isEmpty()) {
            TextView none = label(13, 0x99FFFFFF, "sans-serif");
            none.setText("لا توجد مباريات اليوم  ·  اضغط لعرض المباريات القادمة");
            none.setGravity(Gravity.CENTER_VERTICAL);
            none.setPadding(dp(16), 0, dp(16), 0);
            none.setBackground(round(dp(16), 0x12FFFFFF, 0x1FFFFFFF, dp(1)));
            add(none, -2, 0);
        } else {
            int shown = Math.min(MAX_CHIPS, games.size());
            for (int i = 0; i < shown; i++) add(chip(games.get(i)), -2, i);
            if (games.size() > shown) {
                TextView more = label(14, 0xE6FFFFFF, "sans-serif-medium");
                more.setText("+" + (games.size() - shown));
                more.setGravity(Gravity.CENTER);
                more.setPadding(dp(14), 0, dp(14), 0);
                more.setBackground(round(dp(16), 0x14FFFFFF, 0x24FFFFFF, dp(1)));
                add(more, -2, shown);
            }
        }
        row.setVisibility(View.VISIBLE);
        if (hadFocus) row.getChildAt(Math.max(0, Math.min(focusedIndex, row.getChildCount() - 1))).requestFocus();
    }

    /** Adds one card to the row: focusable, OK opens the matches page, Up and Down leave for the dock and grid. */
    private void add(View card, int width, int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, dp(44));
        if (index > 0) lp.leftMargin = dp(10);
        card.setFocusable(true);
        card.setClickable(true);
        final android.graphics.drawable.Drawable normal = card.getBackground();
        card.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(140).start();
            v.setBackground(has ? round(dp(16), 0x33FFFFFF, 0xF2FFFFFF, dp(2)) : normal);
            if (has) host.onStripFocused();
        });
        card.setOnClickListener(v -> openPage());
        row.addView(card, lp);
    }

    /**
     * Keys while a card has focus, from the activity's dispatchKeyEvent: Up back to the dock, Down to the grid,
     * Left and Right along the cards without leaving the row, OK opens the matches page. False for anything else.
     */
    boolean handleKey(View focus, KeyEvent e) {
        if (focus == null || focus.getParent() != row) return false;
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        int i = row.indexOfChild(focus);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) host.onStripUp();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) host.onStripDown();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down && i > 0) row.getChildAt(i - 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down && i < row.getChildCount() - 1) row.getChildAt(i + 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) openPage();
                return true;
            default:
                return false;
        }
    }

    private void openPage() {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(PAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w(TAG, "open: " + e);
        }
    }

    /** away logo · away · score or time · home · home logo, left to right, so the home side reads first in Arabic. */
    private View chip(Game g) {
        boolean live = "in".equals(g.state), done = "post".equals(g.state);
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(10), 0, dp(10), 0);
        c.setBackground(live ? round(dp(16), 0x33FF3B30, 0x80FF3B30, dp(1))
                : round(dp(16), g.group == 1 ? 0x1F2ED47A : 0x14FFFFFF, g.group == 1 ? 0x552ED47A : 0x24FFFFFF, dp(1)));

        c.addView(logo(g.awayLogo));
        c.addView(name(g.away));

        LinearLayout mid = new LinearLayout(ctx);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        TextView big = label(15, 0xFFFFFFFF, "sans-serif-medium");
        big.setSingleLine(true);
        big.setGravity(Gravity.CENTER);
        big.setText("pre".equals(g.state) ? new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(g.time))
                : LatinTextClock.latin(g.awayScore + " - " + g.homeScore));
        mid.addView(big);
        if (live || done) {
            TextView st = label(9, live ? 0xFFFF8A80 : 0x80FFFFFF, "sans-serif-medium");
            st.setSingleLine(true);
            st.setText(live ? ("استراحة".equals(g.clock) ? "● استراحة" : "● " + g.clock) : "انتهت");
            mid.addView(st);
        }
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(56), -2);
        mlp.leftMargin = dp(6);
        mlp.rightMargin = dp(6);
        c.addView(mid, mlp);

        c.addView(name(g.home));
        c.addView(logo(g.homeLogo));
        return c;
    }

    private ImageView logo(Bitmap b) {
        ImageView v = new ImageView(ctx);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (b != null) v.setImageBitmap(b);
        else {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(0x26FFFFFF);
            v.setImageDrawable(dot);
        }
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
        return v;
    }

    private TextView name(String s) {
        TextView t = label(13, 0xF2FFFFFF, "sans-serif-medium");
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        t.setText(s);
        // An exact width: wrap_content with ellipsize came out at the full maximum for Arabic names.
        int w = (int) Math.ceil(t.getPaint().measureText(s == null ? "" : s));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.min(w + dp(2), dp(90)), -2);
        lp.leftMargin = dp(6);
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView label(float sp, int color, String family) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        return t;
    }

    private static GradientDrawable round(int radius, int fill, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radius);
        d.setColor(fill);
        d.setStroke(strokeWidth, stroke);
        return d;
    }

    // --- the prayer line, the same wording as the add-on's page ---

    private String line(int[] minutes, int now) {
        int[] prayers = {0, 2, 3, 4, 5};   // sunrise has no adhan
        for (int i = prayers.length - 1; i >= 0; i--) {
            int p = prayers[i], since = now - minutes[p];
            if (since >= 0 && since < AFTER_ADHAN) {
                return since == 0 ? "حان الآن أذان " + PRAYER_AR[p] : "مرّ " + duration(since) + " على أذان " + PRAYER_AR[p];
            }
        }
        for (int p : prayers) {
            if (minutes[p] > now) return "باقي " + duration(minutes[p] - now) + " على أذان " + PRAYER_AR[p];
        }
        return "باقي " + duration(24 * 60 - now + minutes[0]) + " على أذان الفجر";
    }

    private static String duration(int total) {
        int h = total / 60, m = total % 60;
        String hours = h == 0 ? "" : h == 1 ? "ساعة" : h == 2 ? "ساعتين" : h <= 10 ? h + " ساعات" : h + " ساعة";
        String mins = m == 0 ? "" : m == 1 ? "دقيقة" : m == 2 ? "دقيقتين" : m <= 10 ? m + " دقائق" : m + " دقيقة";
        if (h == 0) return mins;
        if (m == 0) return hours;
        if (m == 30) return hours + " ونصف";
        if (m == 15) return hours + " وربع";
        return hours + " و" + mins;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
