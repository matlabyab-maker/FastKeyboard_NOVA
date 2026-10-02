package com.fastkeyboard.nova;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings switch: shows/hides only the system mouse cursor and its touch field. */
public class MouseQuickTileService extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        MouseAccessibilityService s = MouseAccessibilityService.getInstance();
        if (s == null) {
            updateTile();
            return;
        }
        if (s.isMouseOverlayShown()) {
            s.hideMouseOverlay();
        } else {
            s.showMouseOverlay();
        }
        updateTile();
    }

    private void updateTile() {
        Tile t = getQsTile();
        if (t == null) return;
        MouseAccessibilityService s = MouseAccessibilityService.getInstance();
        boolean on = s != null && s.isMouseOverlayShown();
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            t.setIcon(Icon.createWithResource(this, com.fastkeyboard.nova.R.drawable.ic_launcher));
        }
        t.setLabel("موس Fast Keyboard Nova");
        t.updateTile();
    }
}
