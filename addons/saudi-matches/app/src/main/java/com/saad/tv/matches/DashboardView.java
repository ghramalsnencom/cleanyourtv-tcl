package com.saad.tv.matches;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The home page and the screensaver: clock, Gregorian and Hijri date, Taif prayer times with the next prayer, and
 * today's games as glass cards. Interactive in the activity (cards open the full schedule), still in the screensaver.
 */
final class DashboardView extends FrameLayout {
    private static final long MINUTE = 60_000L;
    private static final int GOLD = 0xFF8BD8FF;   // the accent: a light sky blue
    private static final int GREEN = 0xFF2ED47A;

    private final boolean interactive;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Locale arabic = new Locale("ar");
    private final SharedPreferences prefs;
    private final Backdrop backdrop;
    private final LinearLayout content;
    private TextView clock, date, nextName, nextIn, gamesTitle, gamesNote;
    private final TextView[] prayerName = new TextView[6], prayerTime = new TextView[6];
    private final LinearLayout[] prayerTile = new LinearLayout[6];
    private LinearLayout games;
    private HorizontalScrollView gamesScroll;

    private Data.Prayers prayers;
    private List<Data.Match> matches;
    private boolean running, loadingGames, loadingPrayers;
    private long nextGames;
    private int driftStep;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            renderClock();
            if (prayers == null || !Data.day(0).equals(prayers.day)) loadPrayers();
            if (System.currentTimeMillis() >= nextGames) loadGames();
            if (!interactive) {
                // Screensaver: keep the picture moving a little so nothing sits still for hours.
                driftStep++;
                backdrop.drift((driftStep % 120) / 120f);
                float d = getResources().getDisplayMetrics().density;
                content.setTranslationX((float) Math.sin(driftStep / 7.0) * 6 * d);
                content.setTranslationY((float) Math.cos(driftStep / 9.0) * 5 * d);
            }
            main.postDelayed(this, 20_000L);
        }
    };

    DashboardView(Context c, boolean interactive) {
        super(c);
        this.interactive = interactive;
        prefs = c.getSharedPreferences("cache", Context.MODE_PRIVATE);
        setLayoutDirection(LAYOUT_DIRECTION_RTL);
        backdrop = new Backdrop(c);
        addView(backdrop, new LayoutParams(-1, -1));

        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(44), dp(30), dp(44), dp(26));
        addView(content, new LayoutParams(-1, -1));

        buildHeader();
        buildPrayers();
        buildGames();
        restoreCache();
    }

    void start() {
        if (running) return;
        running = true;
        nextGames = 0;
        main.post(tick);
    }

    void stop() {
        running = false;
        main.removeCallbacks(tick);
    }

    // --- layout ---

    private void buildHeader() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        LinearLayout left = new LinearLayout(getContext());
        left.setOrientation(LinearLayout.VERTICAL);
        clock = text(70, "sans-serif-thin", Typeface.NORMAL, 0xFFFFFFFF);
        clock.setTextDirection(TEXT_DIRECTION_LTR);
        clock.setGravity(Gravity.RIGHT);   // the RTL row starts at the right; LTR digits would otherwise sit left
        left.addView(clock, new LinearLayout.LayoutParams(-1, -2));
        date = text(17, "sans-serif-light", Typeface.NORMAL, 0xCCFFFFFF);
        date.setPadding(0, dp(2), dp(4), 0);
        left.addView(date, new LinearLayout.LayoutParams(-1, -2));
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout right = new LinearLayout(getContext());
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView city = text(13, "sans-serif-medium", Typeface.NORMAL, 0xFFFFFFFF);
        city.setText("الطائف  ·  أوقات الصلاة");
        city.setPadding(dp(12), dp(5), dp(12), dp(5));
        city.setBackground(glass(dp(14), 0x22FFFFFF, 0x33FFFFFF));
        right.addView(city, new LinearLayout.LayoutParams(-2, -2));
        nextName = text(30, "sans-serif-medium", Typeface.NORMAL, GOLD);
        nextName.setSingleLine(true);
        nextName.setPadding(0, dp(10), 0, 0);
        right.addView(nextName);
        nextIn = text(15, "sans-serif-light", Typeface.NORMAL, 0xCCFFFFFF);
        right.addView(nextIn);
        row.addView(right, new LinearLayout.LayoutParams(-2, -2));
        content.addView(row);
    }

    private void buildPrayers() {
        LinearLayout strip = new LinearLayout(getContext());
        strip.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 6; i++) {
            LinearLayout tile = new LinearLayout(getContext());
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(6), dp(10), dp(6), dp(10));
            TextView n = text(15, "sans-serif-medium", Typeface.NORMAL, 0xDDFFFFFF);
            n.setGravity(Gravity.CENTER);
            n.setText(Data.PRAYER_AR[i]);
            tile.addView(n);
            TextView t = text(26, "sans-serif-light", Typeface.NORMAL, 0xFFFFFFFF);
            t.setGravity(Gravity.CENTER);
            t.setTextDirection(TEXT_DIRECTION_LTR);
            t.setText("--:--");
            t.setPadding(0, dp(4), 0, 0);
            tile.addView(t);
            prayerTile[i] = tile;
            prayerName[i] = n;
            prayerTime[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(i == 0 ? 0 : dp(5), 0, i == 5 ? 0 : dp(5), 0);
            strip.addView(tile, lp);
        }
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(18);
        content.addView(strip, slp);
    }

    private void buildGames() {
        LinearLayout head = new LinearLayout(getContext());
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        gamesTitle = text(21, "sans-serif-medium", Typeface.BOLD, 0xFFFFFFFF);
        gamesTitle.setText("مباريات اليوم");
        head.addView(gamesTitle);
        gamesNote = text(13, "sans-serif", Typeface.NORMAL, 0x99FFFFFF);
        gamesNote.setPaddingRelative(dp(14), dp(4), 0, 0);
        head.addView(gamesNote, new LinearLayout.LayoutParams(0, -2, 1f));
        if (interactive) {
            TextView all = text(14, "sans-serif-medium", Typeface.NORMAL, 0xFFFFFFFF);
            all.setText("الجدول الكامل  ‹");
            all.setPadding(dp(14), dp(6), dp(14), dp(6));
            all.setBackground(glass(dp(16), 0x22FFFFFF, 0x33FFFFFF));
            focusable(all, dp(16));
            all.setOnClickListener(v -> openSchedule());
            head.addView(all);
        }
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = dp(20);
        content.addView(head, hlp);

        gamesScroll = new HorizontalScrollView(getContext());
        gamesScroll.setHorizontalScrollBarEnabled(false);
        gamesScroll.setClipToPadding(false);
        gamesScroll.setPadding(dp(10), dp(14), dp(10), dp(12));   // room for the focused card's zoom
        games = new LinearLayout(getContext());
        games.setOrientation(LinearLayout.HORIZONTAL);
        gamesScroll.addView(games, new LayoutParams(-2, -1));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, 0, 1f);
        glp.topMargin = dp(4);
        content.addView(gamesScroll, glp);
    }

    // --- data ---

    private void loadPrayers() {
        if (loadingPrayers) return;
        loadingPrayers = true;
        new Thread(() -> {
            Data.Prayers p = null;
            try {
                p = Data.prayers();
            } catch (Exception e) {
                Log.w(Data.TAG, "prayers: " + e);
            }
            final Data.Prayers got = p;
            main.post(() -> {
                loadingPrayers = false;
                if (got == null) return;
                prayers = got;
                prefs.edit().putString("times", TextUtils.join(",", got.times)).putString("hijri", got.hijri)
                        .putString("day", got.day).apply();
                renderClock();
            });
        }).start();
    }

    private void loadGames() {
        if (loadingGames) return;
        loadingGames = true;
        nextGames = System.currentTimeMillis() + 5 * MINUTE;
        new Thread(() -> {
            List<Data.Match> got = null;
            try {
                got = Data.today();
                for (Data.Match m : got) {   // warm the logo cache before drawing
                    Data.logo(m.homeLogo);
                    Data.logo(m.awayLogo);
                }
            } catch (Exception e) {
                Log.w(Data.TAG, "games: " + e);
            }
            final List<Data.Match> result = got;
            main.post(() -> {
                loadingGames = false;
                if (result == null) {
                    gamesNote.setText("تعذّر التحديث، بنحاول بعد شوي");
                    nextGames = System.currentTimeMillis() + MINUTE;
                    return;
                }
                matches = result;
                boolean live = false;
                for (Data.Match m : result) live |= m.live();
                nextGames = System.currentTimeMillis() + (live ? MINUTE : 5 * MINUTE);
                renderGames();
            });
        }).start();
    }

    private void restoreCache() {
        String times = prefs.getString("times", null);
        if (times == null || !Data.day(0).equals(prefs.getString("day", ""))) return;
        String[] t = times.split(",");
        if (t.length != 6) return;
        Data.Prayers p = new Data.Prayers();
        for (int i = 0; i < 6; i++) {
            p.times[i] = t[i];
            p.minutes[i] = Integer.parseInt(t[i].substring(0, 2)) * 60 + Integer.parseInt(t[i].substring(3, 5));
        }
        p.hijri = prefs.getString("hijri", "");
        p.day = Data.day(0);
        prayers = p;
        renderClock();
    }

    // --- drawing ---

    private void renderClock() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", arabic).format(now));
        String g = new SimpleDateFormat("EEEE d MMMM", arabic).format(now);
        date.setText(prayers != null && !prayers.hijri.isEmpty() ? g + "   ·   " + prayers.hijri : g);
        if (prayers == null) {
            nextName.setText("");
            nextIn.setText("");
            return;
        }
        Calendar c = Calendar.getInstance();
        int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        // The tile to light: the adhan just called (for half an hour), otherwise the next one.
        int next = -1;
        for (int i = 5; i >= 0 && next < 0; i--) {
            if (i != 1 && m - prayers.minutes[i] >= 0 && m - prayers.minutes[i] < Data.AFTER_ADHAN) next = i;
        }
        for (int i = 0; i < 6 && next < 0; i++) {
            if (i != 1 && prayers.minutes[i] > m) next = i;   // sunrise is shown, but it has no adhan
        }
        String line = Data.prayerLine(prayers.minutes, m);
        int split = line.indexOf(" على ");
        nextName.setText(split > 0 ? line.substring(split + 5) : line);   // "أذان العشاء"
        nextIn.setText(split > 0 ? line.substring(0, split) : "");          // "باقي ساعة ونصف"
        for (int i = 0; i < 6; i++) {
            boolean hl = i == next || (next < 0 && i == 0);
            prayerTime[i].setText(toArabicTime(prayers.times[i]));
            prayerTile[i].setBackground(hl
                    ? gradient(dp(18), new int[] {0xFF6366F1, 0xFF06B6D4})
                    : glass(dp(18), i == 1 ? 0x0DFFFFFF : 0x1AFFFFFF, 0x26FFFFFF));
            prayerTile[i].setElevation(hl ? dp(8) : 0);
            int fg = hl ? 0xFFFFFFFF : (i == 1 ? 0x80FFFFFF : 0xFFFFFFFF);
            prayerName[i].setTextColor(hl ? 0xE6FFFFFF : (i == 1 ? 0x80FFFFFF : 0xDDFFFFFF));
            prayerTime[i].setTextColor(fg);
            prayerTime[i].setTypeface(Typeface.create(hl ? "sans-serif-medium" : "sans-serif-light", Typeface.NORMAL));
        }
    }

    private void renderGames() {
        games.removeAllViews();
        int live = 0;
        for (Data.Match m : matches) if (m.live()) live++;
        gamesNote.setText(matches.isEmpty() ? "" : fmt(matches.size()) + " مباراة" + (live > 0 ? "  ·  " + fmt(live) + " مباشر الآن" : ""));
        if (matches.isEmpty()) {
            LinearLayout empty = new LinearLayout(getContext());
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(36), dp(16), dp(36), dp(16));
            empty.setBackground(glass(dp(22), 0x1FFFFFFF, 0x33FFFFFF));
            TextView a = text(22, "sans-serif-medium", Typeface.NORMAL, 0xFFFFFFFF);
            a.setText("لا توجد مباريات اليوم");
            a.setGravity(Gravity.CENTER);
            empty.addView(a);
            TextView b = text(14, "sans-serif-light", Typeface.NORMAL, 0xB3FFFFFF);
            b.setText(interactive ? "اضغط لعرض مباريات الأيام القادمة" : "افتح «مباريات اليوم» لعرض الأيام القادمة");
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, dp(6), 0, 0);
            empty.addView(b);
            if (interactive) {
                focusable(empty, dp(22));
                empty.setOnClickListener(v -> openSchedule());
            }
            games.addView(empty, new LinearLayout.LayoutParams(dp(420), -1));
            if (interactive && !hasFocusInside()) empty.requestFocus();
            return;
        }
        View first = null;
        for (Data.Match m : matches) {
            View card = card(m);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(212), -1);
            lp.setMarginStart(dp(12));
            games.addView(card, lp);
            if (first == null) first = card;
        }
        final View firstCard = first;
        // RTL: the first card sits at the right end of the row, but a scroll view starts at the left.
        gamesScroll.post(() -> {
            gamesScroll.scrollTo(Math.max(0, games.getWidth() - gamesScroll.getWidth()), 0);
            if (interactive && !hasFocusInside()) firstCard.requestFocus();
        });
    }

    private boolean hasFocusInside() {
        View f = findFocus();
        return f != null && f.getParent() == games;
    }

    private View card(Data.Match m) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        int tint = m.group == 1 ? 0x2A2ED47A : m.group == 0 ? 0x1FFFFFFF : 0x17FFFFFF;
        box.setBackground(glass(dp(20), m.live() ? 0x40E53935 : tint, m.live() ? 0x80FF6B6B : 0x2EFFFFFF));

        TextView comp = text(11, "sans-serif-medium", Typeface.NORMAL, m.group == 1 ? GREEN : 0xB3FFFFFF);
        comp.setGravity(Gravity.CENTER);
        comp.setText(m.group == 1 ? "المنتخب  ·  " + m.comp : m.comp);
        comp.setMaxLines(1);
        comp.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(comp, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout line = new LinearLayout(getContext());
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(team(m.home, m.homeLogo), new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout mid = new LinearLayout(getContext());
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        TextView big = text(m.done() || m.live() ? 26 : 22, "sans-serif-medium", Typeface.NORMAL, 0xFFFFFFFF);
        big.setTextDirection(TEXT_DIRECTION_LTR);
        big.setGravity(Gravity.CENTER);
        big.setSingleLine(true);
        big.setMinWidth(dp(66));
        if ("pre".equals(m.state)) big.setText(new SimpleDateFormat("HH:mm", arabic).format(new Date(m.time)));
        else big.setText(Data.num(m.awayScore) + "\u200E - \u200E" + Data.num(m.homeScore));   // LTR in an RTL row: home on the right
        mid.addView(big);
        TextView st = text(10, "sans-serif-medium", Typeface.NORMAL, 0xFFFFFFFF);
        st.setGravity(Gravity.CENTER);
        if (m.live()) {
            st.setText("استراحة".equals(m.clock) ? "● استراحة" : "● مباشر " + (m.clock == null ? "" : m.clock));
            st.setSingleLine(true);
            st.setPadding(dp(8), dp(2), dp(8), dp(2));
            st.setBackground(gradient(dp(10), new int[] {0xFFFF5252, 0xFFD32F2F}));
        } else {
            st.setText(m.done() ? "انتهت" : "اليوم");
            st.setTextColor(0x99FFFFFF);
        }
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = dp(4);
        mid.addView(st, slp);
        // A fixed width: wrap_content between two weighted columns came out too narrow for "٢٢:٤٥".
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(74), -2);
        mlp.setMargins(dp(2), 0, dp(2), 0);
        line.addView(mid, mlp);

        line.addView(team(m.away, m.awayLogo), new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, 0, 1f);
        llp.topMargin = dp(6);
        box.addView(line, llp);

        if (interactive) {
            focusable(box, dp(20));
            box.setOnClickListener(v -> openSchedule());
        }
        return box;
    }

    private View team(String name, String logoUrl) {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        ImageView logo = new ImageView(getContext());
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap b = Data.cachedLogo(logoUrl);   // loadGames fetched it off the main thread
        if (b != null) logo.setImageBitmap(b);
        else {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(0x26FFFFFF);
            logo.setImageDrawable(dot);
        }
        col.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView n = text(13, "sans-serif-medium", Typeface.NORMAL, "Saudi Arabia".equals(name) ? GREEN : 0xFFFFFFFF);
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        n.setText(Data.ar(name));
        n.setPadding(0, dp(6), 0, 0);
        col.addView(n, new LinearLayout.LayoutParams(-1, -2));
        return col;
    }

    private void openSchedule() {
        getContext().startActivity(new Intent(getContext(), MainActivity.class));
    }

    // --- helpers ---

    private void focusable(View v, int radius) {
        v.setFocusable(true);
        v.setClickable(true);
        final android.graphics.drawable.Drawable normal = v.getBackground();
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? 1.07f : 1f).scaleY(has ? 1.07f : 1f).setDuration(140).start();
            view.setElevation(has ? dp(12) : 0);
            if (has) {
                GradientDrawable f = glass(radius, 0x40FFFFFF, 0xFFFFFFFF);
                f.setStroke(dp(2), 0xE6FFFFFF);
                view.setBackground(f);
            } else {
                view.setBackground(normal);
            }
            if (has && view.getParent() == games) {
                int x = view.getLeft() - dp(40);
                gamesScroll.smoothScrollTo(Math.max(0, x), 0);
            }
        });
    }

    private GradientDrawable glass(int radius, int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radius);
        d.setColor(fill);
        d.setStroke(Math.max(1, dp(1)), stroke);
        return d;
    }

    private GradientDrawable gradient(int radius, int[] colors) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(radius);
        return d;
    }

    private String fmt(int n) {
        return String.format(arabic, "%d", n);
    }

    /** "17:47" with Arabic-Indic digits, kept left to right. */
    private String toArabicTime(String hhmm) {
        try {
            int h = Integer.parseInt(hhmm.substring(0, 2)), m = Integer.parseInt(hhmm.substring(3, 5));
            return String.format(arabic, "%02d:%02d", h, m);
        } catch (Exception e) {
            return hhmm;
        }
    }

    private String hijriDigits(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(ch >= '0' && ch <= '9' ? (char) ('٠' + (ch - '0')) : ch);
        return b.toString();
    }

    private TextView text(float sp, String family, int style, int color) {
        TextView t = new LatinText(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(family, style));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
