/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.muzei.quicksettings;

import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.RequiresApi;
import android.widget.Toast;
import com.google.android.apps.muzei.MuzeiWallpaperService;
import com.google.android.apps.muzei.event.WallpaperActiveStateChangedEvent;
import com.google.firebase.analytics.FirebaseAnalytics;
import net.nurik.roman.muzei.R;
import org.greenrobot.eventbus.EventBus;

/**
 * Quick Settings Tile which allows users to turn dark notification bar, if supported.
 * In cases where Muzei is not activated, the tile also allows users to activate Muzei directly
 * from the tile
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class DarkBarTileService extends TileService {
    private boolean mWallpaperActive = false;
    private SharedPreferences mPref;
    public static final String PREF_DARK_BAR = "dark_notification_bar";

    @Override
    public void onCreate() {
        super.onCreate();
        mPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    @Override
    public void onTileAdded() {
        updateTile();
    }

    @Override
    public void onStartListening() {
        WallpaperActiveStateChangedEvent e = EventBus.getDefault().getStickyEvent(
                WallpaperActiveStateChangedEvent.class);
        mWallpaperActive = e != null && e.isActive();
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            // We're outside of the onStartListening / onStopListening window
            // We'll update the tile next time onStartListening is called.
            return;
        }
        if (!mWallpaperActive && tile.getState() != Tile.STATE_INACTIVE) {
            // If the wallpaper isn't active, the quick tile will activate it
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.action_activate));
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_stat_muzei));
            tile.updateTile();
            return;
        }

        boolean darkBar = mPref.getBoolean(PREF_DARK_BAR, false);

        if (darkBar) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(getString(R.string.quick_tile_dark_title));
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_circle_filled_24dp));
        } else {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(getString(R.string.quick_tile_light_title));
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_circle_empty_24dp));
        }
        tile.updateTile();
    }

    @Override
    public void onClick() {
        Tile tile = getQsTile();
        if (tile == null) {
            // We're outside of the onStartListening / onStopListening window,
            // ignore late arriving clicks
            return;
        }
        if (tile.getState() == Tile.STATE_ACTIVE) {
            FirebaseAnalytics.getInstance(DarkBarTileService.this).logEvent(
                    "tile_next_artwork_click", null);

            boolean darkBar = mPref.getBoolean(PREF_DARK_BAR, false);
            mPref.edit().putBoolean(PREF_DARK_BAR, !darkBar).apply();


            updateTile();
        } else {
            // Inactive means we attempt to activate Muzei
            unlockAndRun(new Runnable() {
                @Override
                public void run() {
                    FirebaseAnalytics.getInstance(DarkBarTileService.this).logEvent(
                            "tile_next_artwork_activate", null);
                    try {
                        startActivityAndCollapse(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                        new ComponentName(DarkBarTileService.this,
                                                MuzeiWallpaperService.class))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (ActivityNotFoundException e) {
                        try {
                            startActivityAndCollapse(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (ActivityNotFoundException e2) {
                            Toast.makeText(DarkBarTileService.this, R.string.error_wallpaper_chooser,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }
            });
        }
    }

    @Override
    public void onStopListening() {
    }

    @Override
    public void onTileRemoved() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
