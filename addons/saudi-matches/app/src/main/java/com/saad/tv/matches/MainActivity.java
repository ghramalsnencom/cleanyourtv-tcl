package com.saad.tv.matches;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The matches page: four day tabs (yesterday to the day after tomorrow), then "live now" on top and one section per
 * competition, Saudi first, each a three-column grid of cards. Opened from the home screen's game cards.
 */
public class MainActivity extends Activity {
    /** Competitions in the order they are shown; "team" is the Saudi national team's own schedule. */
    private static final String[] ORDER = {"team", "ksa.1", "ksa.kings.cup", "afc.champions", "uefa.champions",
            "eng.1", "esp.1", "ita.1", "ger.1", "fra.1", "uefa.europa"};
    /** In these only the big clubs' games are listed; everywhere else every game. */
    private static final String[] BIG_ONLY = {"eng.1", "esp.1", "ita.1", "ger.1", "fra.1", "uefa.europa"};
    private static final String[] TABS = {"أمس", "اليوم", "غدًا", "بعد غد"};
    private static final int COLUMNS = 3;
    private static final int ACCENT = 0xFF8BD8FF, GREEN = 0xFF2ED47A, RED = 0xFFFF5252;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Locale arabic = new Locale("ar");
    private final TextView[] tabs = new TextView[TABS.length];
    private TextView subtitle;
    private LinearLayout body;
    private ScrollView scroll;
    private int day = 1;   // index into TABS: today
    private int loadingFor = -1;
    private final Runnable refresh = () -> load(day);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.addView(new Backdrop(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(44), dp(28), dp(44), 0);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // Header: title and subtitle on the right, the day tabs on the left.
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(30, "sans-serif-medium", 0xFFFFFFFF);
        title.setText("المباريات");
        titles.addView(title);
        subtitle = text(13, "sans-serif", 0x99FFFFFF);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(dp(5), dp(5), dp(5), dp(5));
        tabRow.setBackground(round(dp(24), 0x14FFFFFF, 0x1FFFFFFF, 1));
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            TextView t = text(15, "sans-serif-medium", 0xCCFFFFFF);
            t.setText(TABS[i]);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(18), dp(8), dp(18), dp(8));
            t.setFocusable(true);
            t.setClickable(true);
            t.setOnClickListener(v -> select(index));
            // Moving along the tabs switches the day; no OK needed.
            t.setOnFocusChangeListener((v, has) -> {
                if (has && day != index) select(index);
                styleTabs();
            });
            tabs[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.setMarginStart(dp(4));
            tabRow.addView(t, lp);
        }
        head.addView(tabRow);
        page.addView(head);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(8), 0, dp(36));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        scroll.addView(body, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
        slp.topMargin = dp(12);
        page.addView(scroll, slp);

        setContentView(root);
        styleTabs();
        tabs[day].requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load(day);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(refresh);
    }

    private void select(int index) {
        day = index;
        styleTabs();
        load(index);
    }

    private void styleTabs() {
        for (int i = 0; i < tabs.length; i++) {
            boolean on = i == day, focused = tabs[i].isFocused();
            if (on) {
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF6366F1, 0xFF06B6D4});
                g.setCornerRadius(dp(20));
                if (focused) g.setStroke(dp(2), 0xFFFFFFFF);
                tabs[i].setBackground(g);
            } else {
                tabs[i].setBackground(focused ? round(dp(20), 0x33FFFFFF, 0xFFFFFFFF, dp(2)) : null);
            }
            tabs[i].setTextColor(on || focused ? 0xFFFFFFFF : 0xB3FFFFFF);
        }
    }

    // --- data ---

    private void load(int index) {
        main.removeCallbacks(refresh);
        if (loadingFor == index) return;
        loadingFor = index;
        if (body.getChildCount() == 0 || body.getTag() == null || (int) body.getTag() != index) message("جاري التحميل…", null);
        final String date = Data.day(index - 1);
        new Thread(() -> {
            Map<String, List<Data.Match>> byComp = null;
            try {
                byComp = fetch(date);
                for (List<Data.Match> l : byComp.values()) for (Data.Match m : l) {
                    Data.logo(m.homeLogo);
                    Data.logo(m.awayLogo);
                }
            } catch (Exception e) {
                Log.w(Data.TAG, "page: " + e);
            }
            final Map<String, List<Data.Match>> result = byComp;
            main.post(() -> {
                if (loadingFor == index) loadingFor = -1;
                if (index != day) return;   // the owner moved on to another tab meanwhile
                if (result == null) {
                    message("تعذّر الاتصال بالإنترنت", "بنحاول مرة ثانية بعد دقيقة");
                    main.postDelayed(refresh, 60_000L);
                    return;
                }
                boolean live = render(result, index);
                subtitle.setText(dayTitle(index) + "  ·  آخر تحديث " + new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date()));
                if (index == 1) main.postDelayed(refresh, live ? 60_000L : 5 * 60_000L);
            });
        }).start();
    }

    private Map<String, List<Data.Match>> fetch(String date) throws Exception {
        Map<String, List<Data.Match>> out = new LinkedHashMap<>();
        for (String key : ORDER) {
            List<Data.Match> games = new ArrayList<>();
            if (key.equals("team")) {
                List<Data.Match> all = new ArrayList<>(Data.teamSchedule(true));
                all.addAll(Data.teamSchedule(false));
                for (Data.Match m : all) if (Data.localDay(m.time).equals(date)) Data.addOnce(games, m);
            } else {
                boolean bigOnly = false;
                for (String b : BIG_ONLY) bigOnly |= b.equals(key);
                for (Data.Match m : Data.safeScoreboard(key, date, key.equals("ksa.1"))) {
                    if (bigOnly && !Data.isBig(m.home) && !Data.isBig(m.away)) continue;
                    Data.addOnce(games, m);
                }
            }
            Data.sort(games);
            out.put(key, games);
        }
        return out;
    }

    // --- drawing ---

    /** Draws the day; true when a game is live. */
    private boolean render(Map<String, List<Data.Match>> byComp, int index) {
        View focused = getCurrentFocus();
        boolean keepTabFocus = focused == null || focused.getParent() != null && isTab(focused);
        body.removeAllViews();
        body.setTag(index);
        List<Data.Match> live = new ArrayList<>();
        int total = 0;
        for (List<Data.Match> l : byComp.values()) {
            for (Data.Match m : l) if (m.live()) live.add(m);
            total += l.size();
        }
        if (total == 0) {
            message("لا توجد مباريات " + (index == 1 ? "اليوم" : index == 0 ? "أمس" : index == 2 ? "غدًا" : "بعد غد"),
                    "جرّب يومًا آخر من الأعلى");
            return false;
        }
        if (!live.isEmpty()) section("مباشر الآن", RED, live, true);
        for (Map.Entry<String, List<Data.Match>> e : byComp.entrySet()) {
            List<Data.Match> rest = new ArrayList<>();
            for (Data.Match m : e.getValue()) if (!m.live()) rest.add(m);
            if (rest.isEmpty()) continue;
            boolean team = e.getKey().equals("team");
            section(team ? "المنتخب السعودي" : Data.comp(e.getKey()), team ? GREEN : ACCENT, rest, team);
        }
        if (!keepTabFocus) tabs[day].requestFocus();
        return !live.isEmpty();
    }

    private boolean isTab(View v) {
        for (TextView t : tabs) if (t == v) return true;
        return false;
    }

    private void section(String name, int color, List<Data.Match> games, boolean showComp) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View bar = new View(this);
        bar.setBackground(round(dp(2), color, color, 0));
        head.addView(bar, new LinearLayout.LayoutParams(dp(4), dp(18)));
        TextView t = text(18, "sans-serif-medium", 0xFFFFFFFF);
        t.setText(name);
        t.setPaddingRelative(dp(10), 0, dp(10), 0);
        head.addView(t);
        TextView count = text(12, "sans-serif-medium", 0xCCFFFFFF);
        count.setText(String.valueOf(games.size()));
        count.setPadding(dp(8), dp(2), dp(8), dp(2));
        count.setBackground(round(dp(10), 0x1FFFFFFF, 0x00000000, 0));
        head.addView(count);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = dp(body.getChildCount() == 0 ? 6 : 22);
        hlp.bottomMargin = dp(10);
        body.addView(head, hlp);

        LinearLayout row = null;
        for (int i = 0; i < games.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(96));
                rlp.bottomMargin = dp(12);
                body.addView(row, rlp);
            }
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i % COLUMNS > 0) clp.setMarginStart(dp(12));
            row.addView(card(games.get(i), showComp), clp);
        }
        // Keep the last row's cards the same width as the full rows.
        int rest = games.size() % COLUMNS;
        for (int i = rest == 0 ? COLUMNS : rest; i < COLUMNS; i++) {
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -1, 1f);
            clp.setMarginStart(dp(12));
            row.addView(new View(this), clp);
        }
    }

    private View card(Data.Match m, boolean showComp) {
        boolean live = m.live(), done = m.done();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        Drawable normal = live ? round(dp(18), 0x38E53935, 0x80FF6B6B, 1) : round(dp(18), done ? 0x0FFFFFFF : 0x17FFFFFF, 0x24FFFFFF, 1);
        box.setBackground(normal);
        box.setFocusable(true);
        box.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(140).start();
            v.setElevation(has ? dp(10) : 0);
            v.setBackground(has ? round(dp(18), live ? 0x55E53935 : 0x2EFFFFFF, 0xF2FFFFFF, dp(2)) : normal);
        });

        // Status line: time, live minute or full time, and the competition where the section mixes them.
        TextView status = text(11, "sans-serif-medium", live ? 0xFFFF8A80 : 0x99FFFFFF);
        status.setGravity(Gravity.CENTER);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        String state = live ? ("استراحة".equals(m.clock) ? "● استراحة" : "● مباشر " + m.clock)
                : done ? "انتهت" : new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(m.time));
        status.setText(showComp && m.comp != null && !m.comp.isEmpty() ? state + "  ·  " + m.comp : state);
        box.addView(status, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(side(m.home, m.homeLogo, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView mid = text(done || live ? 24 : 16, "sans-serif-medium", done || live ? 0xFFFFFFFF : 0x80FFFFFF);
        mid.setGravity(Gravity.CENTER);
        mid.setSingleLine(true);
        mid.setTextDirection(View.TEXT_DIRECTION_LTR);
        // LTR digits in an RTL row: away first, so the home score sits on the home (right) side.
        mid.setText(done || live ? Data.num(m.awayScore) + " - " + Data.num(m.homeScore) : "vs");
        line.addView(mid, new LinearLayout.LayoutParams(dp(64), -2));
        line.addView(side(m.away, m.awayLogo, false), new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
        llp.topMargin = dp(8);
        box.addView(line, llp);
        return box;
    }

    /** Logo and name; the home side reads logo then name from the right edge, the away side from the left. */
    private View side(String team, String logoUrl, boolean home) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.HORIZONTAL);
        s.setGravity((home ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap b = Data.cachedLogo(logoUrl);
        if (b != null) logo.setImageBitmap(b);
        else {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(0x26FFFFFF);
            logo.setImageDrawable(dot);
        }
        TextView n = text(14, "sans-serif-medium", "Saudi Arabia".equals(team) ? GREEN : 0xFFFFFFFF);
        n.setText(Data.ar(team));
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        n.setGravity((home ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, -2, 1f);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(32), dp(32));
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

    private void message(String big, String small) {
        body.removeAllViews();
        body.setTag(null);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(40), dp(24), dp(40));
        box.setBackground(round(dp(24), 0x12FFFFFF, 0x1FFFFFFF, 1));
        TextView a = text(22, "sans-serif-medium", 0xFFFFFFFF);
        a.setText(big);
        a.setGravity(Gravity.CENTER);
        box.addView(a);
        if (small != null) {
            TextView b = text(14, "sans-serif", 0x99FFFFFF);
            b.setText(small);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, dp(8), 0, 0);
            box.addView(b);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(40);
        body.addView(box, lp);
    }

    private String dayTitle(int index) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, index - 1);
        return new SimpleDateFormat("EEEE d MMMM", arabic).format(c.getTime());
    }

    private GradientDrawable round(int radius, int fill, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radius);
        d.setColor(fill);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke);
        return d;
    }

    private TextView text(float sp, String family, int color) {
        TextView t = new LatinText(this);
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
