package com.saad.tv.matches;

import android.content.Context;
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
 * The screensaver: on the right the time, both dates, the next prayer with its countdown and today's six times; on
 * the left today's games in two lists, live and still to come, then finished. Lists longer than the screen turn
 * pages every few seconds. Nothing takes focus; the whole page drifts slowly so nothing sits still for hours.
 */
final class StandbyView extends FrameLayout {
    private static final long MINUTE = 60_000L;
    private static final long PAGE_MS = 10_000L;
    private static final int UPCOMING_ROWS = 4, FINISHED_ROWS = 3;
    private static final int SKY = 0xFF8BD8FF, RED = 0xFFFF5252;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Locale arabic = new Locale("ar");
    private final SharedPreferences prefs;
    private final Backdrop backdrop;
    private final LinearLayout content;
    private TextView clock, date, hijri, nextKicker, nextName, nextIn, nextAt, upcomingTitle, finishedTitle, footer;
    private LinearLayout prayerList, upcomingList, finishedList;

    private Data.Prayers prayers;
    private final List<Data.Match> upcoming = new ArrayList<>(), finished = new ArrayList<>();
    private boolean running, loadingGames, loadingPrayers, haveGames;
    private long nextGames;
    private int page, drift;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            renderClock();
            if (prayers == null || !Data.day(0).equals(prayers.day)) loadPrayers();
            if (System.currentTimeMillis() >= nextGames) loadGames();
            drift++;
            backdrop.drift((drift % 180) / 180f);
            float d = getResources().getDisplayMetrics().density;
            content.setTranslationX((float) Math.sin(drift / 7.0) * 8 * d);
            content.setTranslationY((float) Math.cos(drift / 9.0) * 6 * d);
            main.postDelayed(this, 20_000L);
        }
    };

    private final Runnable turnPage = new Runnable() {
        @Override
        public void run() {
            page++;
            renderGames(true);
            main.postDelayed(this, PAGE_MS);
        }
    };

    StandbyView(Context c) {
        super(c);
        prefs = c.getSharedPreferences("cache", Context.MODE_PRIVATE);
        setLayoutDirection(LAYOUT_DIRECTION_RTL);
        backdrop = new Backdrop(c);
        addView(backdrop, new LayoutParams(-1, -1));

        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(dp(48), dp(36), dp(48), dp(30));
        addView(content, new LayoutParams(-1, -1));

        content.addView(buildTimeColumn(), new LinearLayout.LayoutParams(0, -1, 0.40f));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0, -1, 0.60f);
        glp.setMarginStart(dp(32));
        content.addView(buildGamesColumn(), glp);
        restoreCache();
    }

    void start() {
        if (running) return;
        running = true;
        nextGames = 0;
        main.post(tick);
        main.postDelayed(turnPage, PAGE_MS);
    }

    void stop() {
        running = false;
        main.removeCallbacks(tick);
        main.removeCallbacks(turnPage);
    }

    // --- layout ---

    private View buildTimeColumn() {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);

        clock = text(80, "sans-serif-thin", 0xFFFFFFFF);
        clock.setGravity(Gravity.RIGHT);   // the RTL column starts at the right; LTR digits would otherwise sit left
        clock.setTextDirection(TEXT_DIRECTION_LTR);
        col.addView(clock, new LinearLayout.LayoutParams(-1, -2));
        date = text(18, "sans-serif-light", 0xE6FFFFFF);
        date.setPadding(0, dp(2), dp(4), 0);
        col.addView(date, new LinearLayout.LayoutParams(-1, -2));
        hijri = text(14, "sans-serif-light", 0x99FFFFFF);
        hijri.setPadding(0, dp(4), dp(4), 0);
        col.addView(hijri, new LinearLayout.LayoutParams(-1, -2));

        // The next prayer, on a gradient card.
        LinearLayout next = new LinearLayout(getContext());
        next.setOrientation(LinearLayout.VERTICAL);
        next.setPadding(dp(20), dp(14), dp(20), dp(14));
        next.setBackground(gradient(dp(24), new int[] {0xFF6366F1, 0xFF0891B2}));
        next.setElevation(dp(8));
        nextKicker = text(12, "sans-serif-medium", 0xCCFFFFFF);
        nextKicker.setLetterSpacing(0.05f);
        next.addView(nextKicker);
        LinearLayout nameRow = new LinearLayout(getContext());
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.BOTTOM);
        nextName = text(32, "sans-serif-medium", 0xFFFFFFFF);
        nameRow.addView(nextName, new LinearLayout.LayoutParams(0, -2, 1f));
        nextAt = text(22, "sans-serif-light", 0xE6FFFFFF);
        nextAt.setTextDirection(TEXT_DIRECTION_LTR);
        nameRow.addView(nextAt);
        LinearLayout.LayoutParams nrlp = new LinearLayout.LayoutParams(-1, -2);
        nrlp.topMargin = dp(4);
        next.addView(nameRow, nrlp);
        nextIn = text(16, "sans-serif", 0xF2FFFFFF);
        nextIn.setPadding(0, dp(4), 0, 0);
        next.addView(nextIn);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = dp(18);
        col.addView(next, nlp);

        // Today's six times, the passed ones faded.
        prayerList = new LinearLayout(getContext());
        prayerList.setOrientation(LinearLayout.VERTICAL);
        prayerList.setPadding(dp(18), dp(10), dp(18), dp(10));
        prayerList.setBackground(glass(dp(20), 0x14FFFFFF, 0x24FFFFFF));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.topMargin = dp(12);
        col.addView(prayerList, plp);
        return col;
    }

    private View buildGamesColumn() {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);

        upcomingTitle = sectionTitle(col, RED, 0);
        upcomingList = new LinearLayout(getContext());
        upcomingList.setOrientation(LinearLayout.VERTICAL);
        col.addView(upcomingList, new LinearLayout.LayoutParams(-1, -2));

        finishedTitle = sectionTitle(col, 0x99FFFFFF, dp(16));
        finishedList = new LinearLayout(getContext());
        finishedList.setOrientation(LinearLayout.VERTICAL);
        col.addView(finishedList, new LinearLayout.LayoutParams(-1, -2));

        View spacer = new View(getContext());
        col.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));
        footer = text(12, "sans-serif", 0x80FFFFFF);
        col.addView(footer);
        return col;
    }

    private TextView sectionTitle(LinearLayout col, int color, int top) {
        LinearLayout head = new LinearLayout(getContext());
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View bar = new View(getContext());
        bar.setBackground(glass(dp(2), color, color));
        head.addView(bar, new LinearLayout.LayoutParams(dp(4), dp(16)));
        TextView t = text(17, "sans-serif-medium", 0xFFFFFFFF);
        t.setPaddingRelative(dp(10), 0, 0, 0);
        head.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = top;
        lp.bottomMargin = dp(8);
        col.addView(head, lp);
        return t;
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
                Log.w(Data.TAG, "standby prayers: " + e);
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
                for (Data.Match m : got) {
                    Data.logo(m.homeLogo);
                    Data.logo(m.awayLogo);
                }
            } catch (Exception e) {
                Log.w(Data.TAG, "standby games: " + e);
            }
            final List<Data.Match> result = got;
            main.post(() -> {
                loadingGames = false;
                if (result == null) {
                    nextGames = System.currentTimeMillis() + MINUTE;
                    footer.setText("تعذّر التحديث، بنحاول بعد دقيقة");
                    return;
                }
                upcoming.clear();
                finished.clear();
                boolean live = false;
                for (Data.Match m : result) {
                    if (m.done()) finished.add(m);
                    else upcoming.add(m);   // Data.today() already puts live games first
                    live |= m.live();
                }
                // Latest result first.
                finished.sort((a, b) -> Long.compare(b.time, a.time));
                haveGames = true;
                nextGames = System.currentTimeMillis() + (live ? MINUTE : 5 * MINUTE);
                footer.setText("آخر تحديث " + new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date()) + "  ·  ESPN");
                renderGames(false);
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
            String v = LatinText.latin(t[i]).toString();
            p.times[i] = v;
            p.minutes[i] = Integer.parseInt(v.substring(0, 2)) * 60 + Integer.parseInt(v.substring(3, 5));
        }
        p.hijri = prefs.getString("hijri", "");
        p.day = Data.day(0);
        prayers = p;
        renderClock();
    }

    // --- drawing ---

    private void renderClock() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", Locale.ROOT).format(now));
        date.setText(new SimpleDateFormat("EEEE d MMMM yyyy", arabic).format(now));
        if (prayers == null) {
            hijri.setText("");
            nextKicker.setText("أوقات الصلاة · الطائف");
            nextName.setText("…");
            return;
        }
        hijri.setText(prayers.hijri);
        Calendar c = Calendar.getInstance();
        int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);

        // The adhan just called (for half an hour), otherwise the next one; sunrise has no adhan.
        int just = -1, next = -1;
        for (int i = 5; i >= 0 && just < 0; i--) {
            if (i != 1 && m - prayers.minutes[i] >= 0 && m - prayers.minutes[i] < Data.AFTER_ADHAN) just = i;
        }
        for (int i = 0; i < 6 && next < 0; i++) if (i != 1 && prayers.minutes[i] > m) next = i;
        int shown = just >= 0 ? just : next >= 0 ? next : 0;
        String line = Data.prayerLine(prayers.minutes, m);
        int split = line.indexOf(" على ");
        nextKicker.setText(just >= 0 ? "الأذان الأخير  ·  الطائف" : "الصلاة القادمة  ·  الطائف");
        nextName.setText(Data.PRAYER_AR[shown]);
        nextAt.setText(prayers.times[shown]);
        nextIn.setText(split > 0 ? line.substring(0, split) : line);

        prayerList.removeAllViews();
        for (int i = 0; i < 6; i++) {
            boolean passed = prayers.minutes[i] <= m && i != shown, hl = i == shown;
            LinearLayout r = new LinearLayout(getContext());
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(3), 0, dp(3));
            View dot = new View(getContext());
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(hl ? SKY : passed ? 0x33FFFFFF : 0x80FFFFFF);
            dot.setBackground(d);
            r.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
            TextView n = text(15, hl ? "sans-serif-medium" : "sans-serif", hl ? 0xFFFFFFFF : passed ? 0x66FFFFFF : 0xD9FFFFFF);
            n.setText(Data.PRAYER_AR[i]);
            n.setPaddingRelative(dp(10), 0, 0, 0);
            r.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView t = text(15, hl ? "sans-serif-medium" : "sans-serif-light", hl ? SKY : passed ? 0x66FFFFFF : 0xD9FFFFFF);
            t.setTextDirection(TEXT_DIRECTION_LTR);
            t.setText(prayers.times[i]);
            r.addView(t);
            prayerList.addView(r, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void renderGames(boolean turning) {
        if (!haveGames) return;
        int live = 0;
        for (Data.Match m : upcoming) if (m.live()) live++;
        upcomingTitle.setText(live > 0 ? "مباشر الآن والقادمة  ·  " + upcoming.size() : "القادمة اليوم  ·  " + upcoming.size());
        finishedTitle.setText("انتهت  ·  " + finished.size());
        boolean changedUp = fill(upcomingList, upcoming, UPCOMING_ROWS, "لا توجد مباريات قادمة اليوم");
        boolean changedDone = fill(finishedList, finished, FINISHED_ROWS, "لا توجد نتائج بعد");
        if (turning && (changedUp || changedDone)) {
            if (changedUp) fadeIn(upcomingList);
            if (changedDone) fadeIn(finishedList);
        }
    }

    /** Fills one list with the current page of games; true when the list has more than one page. */
    private boolean fill(LinearLayout list, List<Data.Match> games, int rows, String empty) {
        list.removeAllViews();
        if (games.isEmpty()) {
            TextView t = text(14, "sans-serif", 0x80FFFFFF);
            t.setText(empty);
            t.setPadding(dp(16), dp(12), dp(16), dp(12));
            t.setBackground(glass(dp(16), 0x0DFFFFFF, 0x1AFFFFFF));
            list.addView(t, new LinearLayout.LayoutParams(-1, -2));
            return false;
        }
        int pages = (games.size() + rows - 1) / rows;
        int start = (page % pages) * rows;
        for (int i = start; i < Math.min(games.size(), start + rows); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
            lp.bottomMargin = dp(6);
            list.addView(row(games.get(i)), lp);
        }
        if (pages > 1) {
            TextView p = text(11, "sans-serif", 0x80FFFFFF);
            p.setText((page % pages + 1) + " / " + pages);
            p.setTextDirection(TEXT_DIRECTION_LTR);
            p.setGravity(Gravity.CENTER);
            list.addView(p, new LinearLayout.LayoutParams(-1, -2));
        }
        return pages > 1;
    }

    private void fadeIn(View v) {
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(500).start();
    }

    /** competition · home logo name · score or time · name logo away, home on the right. */
    private View row(Data.Match m) {
        boolean live = m.live(), done = m.done();
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), 0, dp(14), 0);
        r.setBackground(live ? glass(dp(16), 0x33E53935, 0x80FF6B6B)
                : glass(dp(16), done ? 0x0FFFFFFF : 0x17FFFFFF, 0x24FFFFFF));

        r.addView(team(m.home, m.homeLogo, true), new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout mid = new LinearLayout(getContext());
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        TextView big = text(live || done ? 19 : 16, "sans-serif-medium", 0xFFFFFFFF);
        big.setSingleLine(true);
        big.setGravity(Gravity.CENTER);
        big.setTextDirection(TEXT_DIRECTION_LTR);
        // LTR digits in an RTL row: away first, so the home score sits on the home (right) side.
        big.setText(live || done ? Data.num(m.awayScore) + " - " + Data.num(m.homeScore)
                : new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(m.time)));
        mid.addView(big);
        TextView st = text(10, "sans-serif-medium", live ? 0xFFFF8A80 : 0x8CFFFFFF);
        st.setSingleLine(true);
        st.setEllipsize(TextUtils.TruncateAt.END);
        st.setGravity(Gravity.CENTER);
        st.setText(live ? ("استراحة".equals(m.clock) ? "● استراحة" : "● " + m.clock) : m.comp);
        mid.addView(st, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(110), -2);
        mlp.setMargins(dp(8), 0, dp(8), 0);
        r.addView(mid, mlp);

        r.addView(team(m.away, m.awayLogo, false), new LinearLayout.LayoutParams(0, -1, 1f));
        return r;
    }

    private View team(String name, String logoUrl, boolean home) {
        LinearLayout s = new LinearLayout(getContext());
        s.setOrientation(LinearLayout.HORIZONTAL);
        s.setGravity((home ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(getContext());
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap b = Data.cachedLogo(logoUrl);
        if (b != null) logo.setImageBitmap(b);
        else {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(0x26FFFFFF);
            logo.setImageDrawable(dot);
        }
        TextView n = text(15, "sans-serif-medium", "Saudi Arabia".equals(name) ? 0xFF2ED47A : 0xFFFFFFFF);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        n.setText(Data.ar(name));
        n.setGravity((home ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(26), dp(26));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, -2, 1f);
        if (home) {
            s.addView(logo, ilp);
            nlp.setMarginStart(dp(8));
            s.addView(n, nlp);
        } else {
            nlp.setMarginEnd(dp(8));
            s.addView(n, nlp);
            s.addView(logo, ilp);
        }
        return s;
    }

    // --- helpers ---

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

    private TextView text(float sp, String family, int color) {
        TextView t = new LatinText(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
