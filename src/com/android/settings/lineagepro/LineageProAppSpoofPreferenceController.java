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

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

public class LineageProAppSpoofPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final String mPreferenceKey;
    private final String mPackageName;
    private final int mDefaultValue;

    public LineageProAppSpoofPreferenceController(Context context, String preferenceKey,
            String packageName, boolean defaultValue) {
        super(context);
        mPreferenceKey = preferenceKey;
        mPackageName = packageName;
        mDefaultValue = defaultValue ? 1 : 0;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        Preference preference = screen.findPreference(mPreferenceKey);
        if (preference == null) {
            return;
        }
        if (!isAvailable()) {
            screen.removePreference(preference);
            return;
        }
        preference.setOnPreferenceChangeListener(this);
        updateState(preference);
    }

    @Override
    public void updateState(Preference preference) {
        if (preference instanceof TwoStatePreference) {
            boolean checked = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    mPreferenceKey, mDefaultValue, UserHandle.USER_CURRENT) != 0;
            ((TwoStatePreference) preference).setChecked(checked);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Settings.Secure.putIntForUser(mContext.getContentResolver(), mPreferenceKey,
                (Boolean) newValue ? 1 : 0, UserHandle.USER_CURRENT);
        Toast.makeText(mContext, R.string.spoofing_applying_changes, Toast.LENGTH_SHORT).show();
        mHandler.postDelayed(this::forceStopPackage, 500);
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return mPreferenceKey;
    }

    @Override
    public boolean isAvailable() {
        try {
            mContext.getPackageManager().getPackageInfo(mPackageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void forceStopPackage() {
        try {
            ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.forceStopPackage(mPackageName);
            }
        } catch (Exception ignored) {
        }
    }
}
