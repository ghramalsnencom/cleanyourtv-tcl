package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextClock;

/**
 * A TextClock that always shows Western digits (18:42, 25), also when the TV's language is Arabic or Persian,
 * whose locales format with their own digits. Every text TextClock sets passes through setText.
 */
public class LatinTextClock extends TextClock {
    public LatinTextClock(Context context) {
        super(context);
    }

    public LatinTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LatinTextClock(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(latin(text), type);
    }

    /** Arabic-Indic (٠-٩) and Extended Arabic-Indic (۰-۹) digits to 0-9; anything else is kept. */
    static CharSequence latin(CharSequence s) {
        if (s == null) return null;
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char d = c >= '٠' && c <= '٩' ? (char) ('0' + c - '٠')
                    : c >= '۰' && c <= '۹' ? (char) ('0' + c - '۰') : c;
            if (d != c) {
                if (b == null) b = new StringBuilder(s);
                b.setCharAt(i, d);
            }
        }
        return b == null ? s : b.toString();
    }
}
