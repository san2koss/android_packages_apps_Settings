/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.android.settings.lineagepro;

import android.app.settings.SettingsEnums;
import android.content.Context;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class LineageProMiscSettings extends DashboardFragment {

    private static final String TAG = "LineageProMisc";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.TESTING;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.lineagepro_misc_settings;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context);
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new LineageProSecureSettingPreferenceController(context,
                "pi_enable_spoof", true));
        controllers.add(new LineageProPropertyPreferenceController(context,
                "persist.sys.vbmeta.update",
                "persist.sys.vbmeta.update",
                LineageProPropertyPreferenceController.TYPE_SWITCH,
                "true"));
        controllers.add(new LineageProSecureSettingPreferenceController(context,
                "pi_gms_cert_chain", false));
        controllers.add(new LineageProSecureSettingPreferenceController(context,
                "pi_games_spoof", false));
        controllers.add(new LineageProSecureSettingPreferenceController(context,
                "pi_photos_spoof", true));
        controllers.add(new LineageProSecureSettingPreferenceController(context,
                "pi_netflix_spoof", false));
        return controllers;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.lineagepro_misc_settings) {
                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return buildPreferenceControllers(context);
                }
            };
}
