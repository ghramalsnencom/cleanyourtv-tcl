package com.saad.tv.matches;

import android.app.Activity;
import android.os.Bundle;

/** The page the tile, the card and the remote's Prime Video button open. */
public class DashboardActivity extends Activity {
    private DashboardView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        view = new DashboardView(this, true);
        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.stop();
    }
}
