# مباريات اليوم (Saudi Matches)

A home page and screensaver for one TV: the clock, the Gregorian and Hijri date, today's prayer times for Taif
(Umm al-Qura) with the next prayer, and today's games as glass cards with team logos: the Saudi Pro League and
King's Cup, the Saudi national team, and the big games of the Champions League, AFC Champions League Elite and the
top five European leagues. Live games come first with the score and minute. OK on a card, or on «الجدول الكامل»,
opens the full schedule: today, tomorrow and the next round, in three columns.

For the [Clean Your TV](../../README.md) launcher it declares a module card ("Saudi Matches"); it is also the TV's
screensaver (`MatchesDream`), and the remote's Prime Video button opens it through the launcher's `app_keys`.

- **Data:** ESPN's public scoreboard (no key) for games and logos, Aladhan (no key) for prayer times. Only the
  league, the date and Taif's coordinates are sent.
- **Refresh:** only while on screen: games every minute while one is live, otherwise every five minutes; prayer
  times once a day, cached for offline.
- **Change the city:** `PRAYER_URL` in `Data.java` (latitude, longitude, method 4 = Umm al-Qura), then rebuild.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | ESPN and Aladhan, as above. |

No account, no telemetry, no background service.

## Build and install (Windows)

```powershell
cd addons\saudi-matches
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat assembleRelease
adb push app\build\outputs\apk\release\app-release.apk /data/local/tmp/addon.apk
adb shell "pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk"
adb shell settings put secure screensaver_components com.saad.tv.matches/.MatchesDream
```

Undo the screensaver: `adb shell settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop`.
