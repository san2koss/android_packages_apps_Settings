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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class LineageProSettings extends DashboardFragment {

    private static final String TAG = "LineageProSettings";

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
        return R.xml.lineagepro_settings;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context);
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();

        controllers.add(new LineageProPropertyPreferenceController(context,
                "lineagepro_fake_sim_enabled",
                "persist.lineagepro.fake_sim.enabled",
                LineageProPropertyPreferenceController.TYPE_SWITCH,
                "false"));
        controllers.add(new LineageProPropertyPreferenceController(context,
                "lineagepro_fake_sim_operator_name",
                "persist.lineagepro.fake_sim.name",
                LineageProPropertyPreferenceController.TYPE_TEXT,
                "Viettel"));
        controllers.add(new LineageProPropertyPreferenceController(context,
                "lineagepro_fake_sim_operator_numeric",
                "persist.lineagepro.fake_sim.operator",
                LineageProPropertyPreferenceController.TYPE_TEXT,
                "45204"));
        controllers.add(new LineageProPropertyPreferenceController(context,
                "lineagepro_fake_sim_country_iso",
                "persist.lineagepro.fake_sim.country",
                LineageProPropertyPreferenceController.TYPE_TEXT,
                "vn"));
        controllers.add(new LineageProPropertyPreferenceController(context,
                "lineagepro_fake_sim_iccid",
                "persist.lineagepro.fake_sim.iccid",
                LineageProPropertyPreferenceController.TYPE_TEXT,
                "8984000000000000000"));

        controllers.add(new LineageProPropertyPreferenceController(context,
                "lineagepro_virtual_camera_enabled",
                "persist.lineagepro.virtual_camera.enabled",
                LineageProPropertyPreferenceController.TYPE_SWITCH,
                "false"));
        controllers.add(new LineageProPropertyPreferenceController(context,
                "lineagepro_virtual_camera_mode",
                "persist.lineagepro.virtual_camera.mode",
                LineageProPropertyPreferenceController.TYPE_LIST,
                "pattern"));
        controllers.add(new LineageProPropertyPreferenceController(context,
                "lineagepro_virtual_camera_path",
                "persist.lineagepro.virtual_camera.path",
                LineageProPropertyPreferenceController.TYPE_TEXT,
                ""));
        controllers.add(new LineageProPropertyPreferenceController(context,
                "lineagepro_virtual_camera_width",
                "persist.lineagepro.virtual_camera.width",
                LineageProPropertyPreferenceController.TYPE_TEXT,
                "1280"));
        controllers.add(new LineageProPropertyPreferenceController(context,
                "lineagepro_virtual_camera_height",
                "persist.lineagepro.virtual_camera.height",
                LineageProPropertyPreferenceController.TYPE_TEXT,
                "720"));
        controllers.add(new LineageProPropertyPreferenceController(context,
                "lineagepro_virtual_camera_format",
                "persist.lineagepro.virtual_camera.format",
                LineageProPropertyPreferenceController.TYPE_LIST,
                "rgba"));
        controllers.add(new LineageProPropertyPreferenceController(context,
                "lineagepro_virtual_camera_loop",
                "persist.lineagepro.virtual_camera.loop",
                LineageProPropertyPreferenceController.TYPE_SWITCH,
                "true"));

        return controllers;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.lineagepro_settings) {
                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return buildPreferenceControllers(context);
                }
            };

    @Override
    public @Nullable String getPreferenceScreenBindingKey(@NonNull Context context) {
        return null;
    }
}
