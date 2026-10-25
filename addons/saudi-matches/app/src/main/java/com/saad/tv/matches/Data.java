package com.saad.tv.matches;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** ESPN's public soccer scoreboard and Aladhan's prayer times: fetching, parsing and Arabic names. */
final class Data {
    static final String TAG = "SaudiMatches";
    private static final String API = "https://site.api.espn.com/apis/site/v2/sports/soccer/";
    static final String SAUDI_TEAM = "655";
    static final String[] SAUDI_LEAGUES = {"ksa.1", "ksa.kings.cup"};
    static final String[] WORLD_LEAGUES = {"uefa.champions", "afc.champions", "eng.1", "esp.1", "ita.1",
            "ger.1", "fra.1", "uefa.europa"};
    /** Competitions whose every game counts as a big one; elsewhere one of BIG_CLUBS has to play. */
    private static final String[] ALL_GAMES = {"uefa.champions", "afc.champions"};

    /** Taif, Umm al-Qura. */
    private static final String PRAYER_URL = "https://api.aladhan.com/v1/timings/%s?latitude=21.2703&longitude=40.4158&method=4";
    static final String[] PRAYER_KEYS = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    static final String[] PRAYER_AR = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};

    private static final Map<String, String> AR = new HashMap<>();
    private static final Map<String, String> COMP = new HashMap<>();
    private static final List<String> BIG_CLUBS = new ArrayList<>();
    private static final LruCache<String, Bitmap> LOGOS = new LruCache<>(80);

    static {
        String[] names = {
            // Saudi clubs
            "Al Hilal", "الهلال", "Al Nassr", "النصر", "Al Ittihad", "الاتحاد", "Al Ahli", "الأهلي",
            "Al Shabab", "الشباب", "Al Ettifaq", "الاتفاق", "Al Taawoun", "التعاون", "Al Fateh", "الفتح",
            "Al Fayha", "الفيحاء", "Al Khaleej", "الخليج", "Al Riyadh", "الرياض", "Al Kholood", "الخلود",
            "Al Okhdood", "الأخدود", "Al Qadsiah", "القادسية", "Al Qadisiyah", "القادسية", "Damac", "ضمك",
            "Al Najma", "النجمة", "Al Hazem", "الحزم", "NEOM", "نيوم", "Neom", "نيوم", "Al Faisaly", "الفيصلي",
            "Abha", "أبها", "Al Wehda", "الوحدة", "Al Raed", "الرائد", "Al Orobah", "العروبة", "Al Tai", "الطائي",
            "Al Batin", "الباطن", "Al Jabalain", "الجبلين", "Al Adalah", "العدالة", "Al Ain", "العين",
            "Al Akhdoud", "الأخدود", "Al Bukiryah", "البكيرية", "Al Zulfi", "الزلفي", "Al Jubail", "الجبيل",
            "Al Arabi", "العربي", "Al Diriyah", "الدرعية", "Al Ula", "العلا", "Al Anwar", "الأنوار",
            "Jeddah", "جدة", "Ohod", "أحد", "Al Safa", "الصفا", "Al Taee", "الطائي", "Wej", "وج",
            // national teams
            "Saudi Arabia", "السعودية", "South Korea", "كوريا الجنوبية", "Korea Republic", "كوريا الجنوبية",
            "Japan", "اليابان", "Australia", "أستراليا", "Iran", "إيران", "Iraq", "العراق", "Qatar", "قطر",
            "Jordan", "الأردن", "United Arab Emirates", "الإمارات", "UAE", "الإمارات", "Kuwait", "الكويت",
            "Bahrain", "البحرين", "Oman", "عُمان", "Yemen", "اليمن", "Syria", "سوريا", "Lebanon", "لبنان",
            "Palestine", "فلسطين", "Uzbekistan", "أوزبكستان", "China PR", "الصين", "China", "الصين",
            "Indonesia", "إندونيسيا", "Egypt", "مصر", "Morocco", "المغرب", "Algeria", "الجزائر", "Tunisia", "تونس",
            "Brazil", "البرازيل", "Argentina", "الأرجنتين", "France", "فرنسا", "Spain", "إسبانيا",
            "Germany", "ألمانيا", "England", "إنجلترا", "Portugal", "البرتغال", "Italy", "إيطاليا",
            "Netherlands", "هولندا", "Belgium", "بلجيكا", "Croatia", "كرواتيا", "Mexico", "المكسيك",
            "United States", "الولايات المتحدة", "USA", "الولايات المتحدة", "Haiti", "هايتي",
            // big clubs
            "Real Madrid", "ريال مدريد", "Barcelona", "برشلونة", "Atletico Madrid", "أتلتيكو مدريد",
            "Atlético Madrid", "أتلتيكو مدريد", "Manchester City", "مان سيتي",
            "Manchester United", "مان يونايتد", "Liverpool", "ليفربول", "Arsenal", "آرسنال",
            "Chelsea", "تشيلسي", "Tottenham Hotspur", "توتنهام", "Newcastle United", "نيوكاسل",
            "Bayern Munich", "بايرن ميونخ", "Borussia Dortmund", "دورتموند",
            "Bayer Leverkusen", "ليفركوزن", "Paris Saint-Germain", "باريس سان جيرمان",
            "Juventus", "يوفنتوس", "Internazionale", "إنتر ميلان", "Inter Milan", "إنتر ميلان",
            "AC Milan", "ميلان", "Napoli", "نابولي", "AS Roma", "روما", "Benfica", "بنفيكا", "Porto", "بورتو",
            "Sporting CP", "سبورتينغ", "Ajax", "أياكس", "Marseille", "مارسيليا", "Monaco", "موناكو",
            "Al Sadd", "السد", "Al Duhail", "الدحيل", "Al Wahda", "الوحدة", "Shabab Al Ahli", "شباب الأهلي",
            "Persepolis", "برسبوليس", "Esteghlal", "استقلال طهران", "Sepahan", "سباهان",
            "Aston Villa", "أستون فيلا", "Brighton & Hove Albion", "برايتون", "AFC Bournemouth", "بورنموث",
            "Brentford", "برينتفورد", "Crystal Palace", "كريستال بالاس", "Everton", "إيفرتون", "Fulham", "فولهام",
            "Nottingham Forest", "نوتنغهام", "West Ham United", "وست هام", "Wolverhampton Wanderers", "وولفرهامبتون",
            "Leeds United", "ليدز", "Burnley", "بيرنلي", "Sunderland", "سندرلاند", "Leicester City", "ليستر سيتي",
            "Athletic Club", "أتلتيك بلباو", "Real Sociedad", "ريال سوسيداد", "Real Betis", "ريال بيتيس",
            "Villarreal", "فياريال", "Sevilla", "إشبيلية", "Valencia", "فالنسيا", "Girona", "جيرونا",
            "Celta Vigo", "سيلتا فيغو", "Osasuna", "أوساسونا", "Getafe", "خيتافي", "Mallorca", "مايوركا",
            "Rayo Vallecano", "رايو فاييكانو", "Espanyol", "إسبانيول", "Alavés", "ألافيس", "Elche", "إلتشي",
            "Levante", "ليفانتي", "Real Oviedo", "ريال أوفييدو",
            "Fiorentina", "فيورنتينا", "Lazio", "لاتسيو", "Atalanta", "أتالانتا", "Bologna", "بولونيا",
            "Torino", "تورينو", "Genoa", "جنوى", "Udinese", "أودينيزي", "Como", "كومو", "Cagliari", "كالياري",
            "Lecce", "ليتشي", "Parma", "بارما", "Hellas Verona", "فيرونا", "Sassuolo", "ساسولو", "Pisa", "بيزا",
            "Cremonese", "كريمونيزي",
            "RB Leipzig", "لايبزيغ", "VfB Stuttgart", "شتوتغارت", "Eintracht Frankfurt", "فرانكفورت",
            "VfL Wolfsburg", "فولفسبورغ", "SC Freiburg", "فرايبورغ", "Borussia Monchengladbach", "مونشنغلادباخ",
            "Werder Bremen", "فيردر بريمن", "Union Berlin", "يونيون برلين", "Mainz", "ماينز", "Hoffenheim", "هوفنهايم",
            "Augsburg", "أوغسبورغ", "FC Cologne", "كولن", "Hamburg SV", "هامبورغ",
            "Lyon", "ليون", "Lille", "ليل", "Nice", "نيس", "Lens", "لانس", "Rennes", "رين", "Strasbourg", "ستراسبورغ",
            "Nantes", "نانت", "Toulouse", "تولوز", "Brest", "بريست", "Auxerre", "أوكسير", "Le Havre", "لوهافر",
            "Galatasaray", "غلطة سراي", "Fenerbahce", "فنربخشة", "Celtic", "سلتيك", "PSV Eindhoven", "آيندهوفن",
            "Club Brugge", "كلوب بروج", "Feyenoord", "فينورد",
        };
        for (int i = 0; i + 1 < names.length; i += 2) AR.put(names[i], names[i + 1]);
        Collections.addAll(BIG_CLUBS, "Real Madrid", "Barcelona", "Atletico Madrid", "Atlético Madrid",
                "Manchester City", "Manchester United", "Liverpool", "Arsenal", "Chelsea", "Tottenham Hotspur",
                "Bayern Munich", "Borussia Dortmund", "Paris Saint-Germain", "Juventus", "Internazionale",
                "Inter Milan", "AC Milan", "Napoli");
        String[] comps = {
            "ksa.1", "دوري روشن", "ksa.kings.cup", "كأس الملك", "uefa.champions", "دوري أبطال أوروبا",
            "afc.champions", "أبطال آسيا للنخبة", "eng.1", "الدوري الإنجليزي", "esp.1", "الدوري الإسباني",
            "ita.1", "الدوري الإيطالي", "ger.1", "الدوري الألماني", "fra.1", "الدوري الفرنسي",
            "uefa.europa", "الدوري الأوروبي",
            "International Friendly", "مباراة ودية", "FIFA World Cup", "كأس العالم",
            "FIFA World Cup Qualifying - AFC", "تصفيات كأس العالم", "AFC Asian Cup", "كأس آسيا",
            "Arab Cup", "كأس العرب", "FIFA Arab Cup", "كأس العرب", "Gulf Cup", "كأس الخليج",
            "Arabian Gulf Cup", "كأس الخليج", "Concacaf Gold Cup", "الكأس الذهبية",
        };
        for (int i = 0; i + 1 < comps.length; i += 2) COMP.put(comps[i], comps[i + 1]);
    }

    /** One game, times in UTC millis. */
    static final class Match {
        long time;
        String home, away, homeScore, awayScore, homeLogo, awayLogo, clock, comp;
        /** pre, in or post, as ESPN says it. */
        String state;
        /** 0 Saudi league or cup, 1 national team, 2 world. */
        int group;

        boolean live() { return "in".equals(state); }
        boolean done() { return "post".equals(state); }
    }

    /** Today's prayer times: minutes after midnight and "HH:mm", in PRAYER_KEYS order, plus the Hijri date. */
    static final class Prayers {
        int[] minutes = new int[PRAYER_KEYS.length];
        String[] times = new String[PRAYER_KEYS.length];
        String hijri = "";
        String day = "";
    }

    private Data() {}

    // --- matches ---

    /** Today's games: Saudi league and cup, the national team, then the big games elsewhere. */
    static List<Match> today() throws Exception {
        List<Match> saudi = new ArrayList<>(), national = new ArrayList<>(), world = new ArrayList<>();
        String d0 = day(0);
        for (String slug : SAUDI_LEAGUES) {
            for (Match m : safeScoreboard(slug, d0, slug.equals("ksa.1"))) {
                if (localDay(m.time).equals(d0) || m.live()) { m.group = 0; addOnce(saudi, m); }
            }
        }
        for (Match m : teamSchedule(true)) {
            if (localDay(m.time).equals(d0) || m.live()) { m.group = 1; addOnce(national, m); }
        }
        for (String slug : WORLD_LEAGUES) {
            boolean all = false;
            for (String a : ALL_GAMES) all |= a.equals(slug);
            for (Match m : safeScoreboard(slug, d0, false)) {
                if (!all && !BIG_CLUBS.contains(m.home) && !BIG_CLUBS.contains(m.away)) continue;
                if (localDay(m.time).equals(d0) || m.live()) { m.group = 2; addOnce(world, m); }
            }
        }
        sort(saudi);
        sort(national);
        sort(world);
        List<Match> out = new ArrayList<>(national);
        out.addAll(saudi);
        out.addAll(world);
        // Live games first, finished ones last; within each, the order above (national, Saudi, world).
        List<Match> ranked = new ArrayList<>();
        for (Match m : out) if (m.live()) ranked.add(m);
        for (Match m : out) if (!m.live() && !m.done()) ranked.add(m);
        for (Match m : out) if (m.done()) ranked.add(m);
        return ranked;
    }

    static List<Match> safeScoreboard(String slug, String date, boolean required) throws Exception {
        try {
            return scoreboard(slug, date);
        } catch (Exception e) {
            if (required) throw e;
            Log.w(TAG, slug + ": " + e);
            return new ArrayList<>();
        }
    }

    static List<Match> scoreboard(String slug, String date) throws Exception {
        String url = API + slug + "/scoreboard" + (date == null ? "" : "?dates=" + date);
        JSONObject json = new JSONObject(fetch(url));
        List<Match> out = new ArrayList<>();
        JSONArray events = json.optJSONArray("events");
        if (events == null) return out;
        for (int i = 0; i < events.length(); i++) {
            Match m = parse(events.getJSONObject(i), slug);
            if (m != null) out.add(m);
        }
        return out;
    }

    static List<Match> teamSchedule(boolean fixtures) throws Exception {
        String url = API + "all/teams/" + SAUDI_TEAM + "/schedule" + (fixtures ? "?fixture=true" : "");
        JSONObject json = new JSONObject(fetch(url));
        List<Match> out = new ArrayList<>();
        JSONArray events = json.optJSONArray("events");
        if (events == null) return out;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.getJSONObject(i);
            Match m = parse(e, null);
            if (m == null) continue;
            JSONObject league = e.optJSONObject("league");
            if (league != null) m.comp = comp(league.optString("name", ""));
            m.group = 1;
            out.add(m);
        }
        return out;
    }

    private static Match parse(JSONObject e, String slug) {
        try {
            JSONObject comp = e.getJSONArray("competitions").getJSONObject(0);
            Match m = new Match();
            m.time = parseTime(e.optString("date", comp.optString("date")));
            JSONObject st = comp.optJSONObject("status");
            if (st == null) st = e.optJSONObject("status");
            JSONObject type = st != null ? st.optJSONObject("type") : null;
            m.state = type != null ? type.optString("state", "pre") : "pre";
            m.clock = st != null ? st.optString("displayClock", "") : "";
            if (type != null && m.live()) {
                String detail = type.optString("shortDetail", "");
                if (detail.equalsIgnoreCase("HT") || detail.toLowerCase(Locale.ROOT).contains("half")) m.clock = "استراحة";
            }
            m.comp = slug != null ? comp(slug) : "";
            JSONArray teams = comp.getJSONArray("competitors");
            for (int i = 0; i < teams.length(); i++) {
                JSONObject t = teams.getJSONObject(i);
                JSONObject team = t.getJSONObject("team");
                String name = team.optString("displayName", "?");
                String logo = team.optString("logo", "");
                if (logo.isEmpty()) {
                    JSONArray logos = team.optJSONArray("logos");
                    if (logos != null && logos.length() > 0) logo = logos.getJSONObject(0).optString("href", "");
                }
                String score;
                Object s = t.opt("score");
                if (s instanceof JSONObject) score = ((JSONObject) s).optString("displayValue", "");
                else score = s == null ? "" : String.valueOf(s);
                if ("home".equals(t.optString("homeAway"))) { m.home = name; m.homeScore = score; m.homeLogo = logo; }
                else { m.away = name; m.awayScore = score; m.awayLogo = logo; }
            }
            if (m.home == null || m.away == null) return null;
            return m;
        } catch (Exception ex) {
            Log.w(TAG, "parse: " + ex);
            return null;
        }
    }

    // --- prayer times ---

    static Prayers prayers() throws Exception {
        String date = new SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).format(new Date());
        JSONObject data = new JSONObject(fetch(String.format(Locale.ROOT, PRAYER_URL, date))).getJSONObject("data");
        JSONObject t = data.getJSONObject("timings");
        Prayers p = new Prayers();
        for (int i = 0; i < PRAYER_KEYS.length; i++) {
            String v = t.getString(PRAYER_KEYS[i]).trim();
            int sp = v.indexOf(' ');
            if (sp > 0) v = v.substring(0, sp);
            p.times[i] = v;
            p.minutes[i] = Integer.parseInt(v.substring(0, 2)) * 60 + Integer.parseInt(v.substring(3, 5));
        }
        JSONObject hijri = data.getJSONObject("date").getJSONObject("hijri");
        p.hijri = hijri.getString("day") + " " + hijri.getJSONObject("month").getString("ar") + " " + hijri.getString("year") + " هـ";
        p.day = day(0);
        return p;
    }

    // --- logos ---

    /** The logo if it is already in memory; safe on the main thread. */
    static Bitmap cachedLogo(String url) {
        return url == null || url.isEmpty() ? null : LOGOS.get(url);
    }

    /** A small team logo, from memory or ESPN's resizing proxy; null when there is none. Call off the main thread. */
    static Bitmap logo(String url) {
        if (url == null || url.isEmpty()) return null;
        Bitmap b = LOGOS.get(url);
        if (b != null) return b;
        try {
            String small = url.startsWith("https://a.espncdn.com/i/")
                    ? "https://a.espncdn.com/combiner/i?img=" + url.substring("https://a.espncdn.com".length()) + "&w=96&h=96"
                    : url;
            byte[] bytes = fetchBytes(small);
            b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (b != null) LOGOS.put(url, b);
            return b;
        } catch (Exception e) {
            Log.w(TAG, "logo: " + e);
            return null;
        }
    }

    // --- the prayer line ---

    /** Minutes an adhan stays "just called" before the line moves on to the next one. */
    static final int AFTER_ADHAN = 30;

    /**
     * "باقي ساعة ونصف على أذان العشاء", or for half an hour after an adhan "مرّ ١٠ دقائق على أذان المغرب".
     * minutes are today's times in PRAYER_KEYS order; now is minutes after midnight.
     */
    static String prayerLine(int[] minutes, int now) {
        int[] prayers = {0, 2, 3, 4, 5};   // sunrise has no adhan
        for (int i = prayers.length - 1; i >= 0; i--) {
            int p = prayers[i], since = now - minutes[p];
            if (since >= 0 && since < AFTER_ADHAN) {
                return since == 0 ? "حان الآن أذان " + PRAYER_AR[p]
                        : "مرّ " + duration(since) + " على أذان " + PRAYER_AR[p];
            }
        }
        for (int p : prayers) {
            if (minutes[p] > now) return "باقي " + duration(minutes[p] - now) + " على أذان " + PRAYER_AR[p];
        }
        return "باقي " + duration(24 * 60 - now + minutes[0]) + " على أذان الفجر";
    }

    /** 90 → "ساعة ونصف", 135 → "ساعتين وربع", 7 → "٧ دقائق". */
    static String duration(int total) {
        int h = total / 60, m = total % 60;
        String hours = h == 0 ? "" : h == 1 ? "ساعة" : h == 2 ? "ساعتين" : h <= 10 ? digits(h) + " ساعات" : digits(h) + " ساعة";
        String mins = m == 0 ? "" : m == 1 ? "دقيقة" : m == 2 ? "دقيقتين" : m <= 10 ? digits(m) + " دقائق" : digits(m) + " دقيقة";
        if (h == 0) return mins;
        if (m == 0) return hours;
        if (m == 30) return hours + " ونصف";
        if (m == 15) return hours + " وربع";
        return hours + " و" + mins;
    }

    static String digits(int n) {
        return String.valueOf(n);
    }

    // --- names ---

    static String ar(String name) {
        if (name == null) return "?";
        String v = AR.get(name);
        if (v == null) v = AR.get(name.replace("Al-", "Al ").replace(" FC", "").replace(" SC", "").trim());
        return v != null ? v : name;
    }

    static boolean isBig(String team) {
        return BIG_CLUBS.contains(team);
    }

    static String comp(String key) {
        String v = COMP.get(key);
        return v != null ? v : key;
    }

    static String num(String s) {
        if (s == null || s.isEmpty()) return "-";
        try {
            return String.valueOf(Integer.parseInt(s.trim()));
        } catch (NumberFormatException e) {
            return s;
        }
    }

    // --- dates ---

    static String day(int plus) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, plus);
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(c.getTime());
    }

    static String localDay(long t) {
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date(t));
    }

    private static long parseTime(String s) throws Exception {
        String[] fmts = {"yyyy-MM-dd'T'HH:mmX", "yyyy-MM-dd'T'HH:mm:ssX"};
        for (String f : fmts) {
            try {
                SimpleDateFormat p = new SimpleDateFormat(f, Locale.ROOT);
                p.setTimeZone(TimeZone.getTimeZone("UTC"));
                return p.parse(s).getTime();
            } catch (Exception ignored) {
            }
        }
        throw new Exception("bad date " + s);
    }

    static void sort(List<Match> l) {
        l.sort((a, b) -> Long.compare(a.time, b.time));
    }

    static void addOnce(List<Match> l, Match m) {
        for (Match x : l) if (x.time == m.time && x.home.equals(m.home)) return;
        l.add(m);
    }

    // --- network ---

    static String fetch(String url) throws Exception {
        return new String(fetchBytes(url), "UTF-8");
    }

    private static byte[] fetchBytes(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }
}
