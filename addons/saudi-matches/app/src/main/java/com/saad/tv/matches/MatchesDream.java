package com.saad.tv.matches;

import android.service.dreams.DreamService;

/** The TV's screensaver: time, prayer times and today's games (StandbyView), slowly drifting. */
public class MatchesDream extends DreamService {
    private StandbyView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        view = new StandbyView(this);
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (view != null) view.start();
    }

    @Override
    public void onDreamingStopped() {
        if (view != null) view.stop();
        super.onDreamingStopped();
    }
}
