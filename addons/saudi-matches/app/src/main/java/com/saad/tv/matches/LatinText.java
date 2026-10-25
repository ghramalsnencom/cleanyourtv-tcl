package com.saad.tv.matches;

import android.content.Context;
import android.widget.TextView;

/** A TextView that always shows Western digits (18:45, 2 - 0), whatever the Arabic formatting gave it. */
final class LatinText extends TextView {
    LatinText(Context c) {
        super(c);
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
