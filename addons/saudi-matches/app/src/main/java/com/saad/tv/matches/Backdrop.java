package com.saad.tv.matches;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * The page background: a dark mesh gradient, three soft colour fields (indigo, teal and a little magenta) over
 * near-black, and one faint diagonal light band. drift() moves the fields a little for the screensaver.
 */
final class Backdrop extends View {
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase;

    Backdrop(Context c) {
        super(c);
    }

    /** 0..1, where the colour fields sit; the screensaver moves it slowly. */
    void drift(float p) {
        phase = p;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        base.setShader(new LinearGradient(0, 0, w, h, new int[] {0xFF0B1022, 0xFF070912, 0xFF04060A},
                new float[] {0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        band.setShader(new LinearGradient(w * 0.35f, 0, w * 0.75f, h, new int[] {0x00FFFFFF, 0x0BFFFFFF, 0x00FFFFFF},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, base);
        double t = phase * Math.PI * 2;
        field(c, w * (0.85f + 0.05f * (float) Math.sin(t)), h * (0.10f + 0.06f * (float) Math.cos(t)), h * 0.95f, 0x5C4F46E5);
        field(c, w * (0.08f + 0.05f * (float) Math.cos(t)), h * (0.95f + 0.04f * (float) Math.sin(t)), h * 0.85f, 0x4D0EA5A0);
        field(c, w * (0.55f + 0.04f * (float) Math.sin(t * 2)), h * (0.55f + 0.05f * (float) Math.cos(t)), h * 0.55f, 0x26DB2777);
        c.drawRect(0, 0, w, h, band);
    }

    private void field(Canvas c, float x, float y, float r, int color) {
        blob.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, blob);
    }
}
