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
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

public class LineageProSecureSettingPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    private final String mPreferenceKey;
    private final int mDefaultValue;

    public LineageProSecureSettingPreferenceController(Context context, String preferenceKey,
            boolean defaultValue) {
        super(context);
        mPreferenceKey = preferenceKey;
        mDefaultValue = defaultValue ? 1 : 0;
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
        if (preference instanceof TwoStatePreference) {
            final boolean checked = Settings.Secure.getIntForUser(
                    mContext.getContentResolver(), mPreferenceKey, mDefaultValue,
                    UserHandle.USER_CURRENT) != 0;
            ((TwoStatePreference) preference).setChecked(checked);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Settings.Secure.putIntForUser(mContext.getContentResolver(), mPreferenceKey,
                (Boolean) newValue ? 1 : 0, UserHandle.USER_CURRENT);
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
}
