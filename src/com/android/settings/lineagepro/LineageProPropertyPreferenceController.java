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

import android.content.Context;
import android.os.SystemProperties;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

public class LineageProPropertyPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    public static final int TYPE_SWITCH = 0;
    public static final int TYPE_TEXT = 1;
    public static final int TYPE_LIST = 2;

    private final String mPreferenceKey;
    private final String mPropertyKey;
    private final int mType;
    private final String mDefaultValue;

    public LineageProPropertyPreferenceController(Context context, String preferenceKey,
            String propertyKey, int type, String defaultValue) {
        super(context);
        mPreferenceKey = preferenceKey;
        mPropertyKey = propertyKey;
        mType = type;
        mDefaultValue = defaultValue;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        final Preference preference = screen.findPreference(mPreferenceKey);
        if (preference == null) {
            return;
        }

        preference.setOnPreferenceChangeListener(this);
        updateState(preference);
    }

    @Override
    public void updateState(Preference preference) {
        final String value = SystemProperties.get(mPropertyKey, mDefaultValue);
        if (mType == TYPE_SWITCH && preference instanceof TwoStatePreference) {
            ((TwoStatePreference) preference).setChecked(parseBoolean(value));
        } else if (mType == TYPE_TEXT && preference instanceof EditTextPreference) {
            final EditTextPreference editTextPreference = (EditTextPreference) preference;
            editTextPreference.setText(value);
        } else if (mType == TYPE_LIST && preference instanceof ListPreference) {
            final ListPreference listPreference = (ListPreference) preference;
            listPreference.setValue(value);
            listPreference.setSummary(listPreference.getEntry());
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String value;
        if (mType == TYPE_SWITCH) {
            value = Boolean.toString((Boolean) newValue);
        } else {
            value = String.valueOf(newValue);
        }

        SystemProperties.set(mPropertyKey, value);

        if (mType == TYPE_LIST && preference instanceof ListPreference) {
            final ListPreference listPreference = (ListPreference) preference;
            final int index = listPreference.findIndexOfValue(value);
            if (index >= 0) {
                preference.setSummary(listPreference.getEntries()[index]);
            }
        }
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return mPreferenceKey;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private boolean parseBoolean(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }
}
