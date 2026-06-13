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
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.internal.util.lineage.PixelDeviceRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class PixelPropsSettings extends SettingsPreferenceFragment {

    private static final String PP_TARGETS_KEY = "pi_pp_targets";
    private static final String PP_MODEL_KEY = "pi_pp_model";
    private static final String PP_ENABLED_KEY = "pi_pp_spoof";

    private static final String KEY_ENABLED = "pp_enabled";
    private static final String KEY_MODEL = "pp_model";
    private static final String KEY_SHOW_SYSTEM = "pp_show_system";
    private static final String KEY_SELECT_ALL = "pp_select_all";
    private static final String KEY_RESET = "pp_reset";
    private static final String KEY_REFRESH_PROFILES = "pp_refresh_profiles";
    private static final String KEY_TARGETS = "pp_targets_category";

    private static final String DEFAULT_PHONE_PROFILE = "mustang";
    private static final String DEFAULT_TABLET_PROFILE = "tangorpro";

    private static final Set<String> DISPLAY_PROFILE_CODENAMES = new HashSet<>(Arrays.asList(
            "tokay", "caiman", "komodo", "comet", "tegu",
            "frankel", "blazer", "mustang", "rango", "stallion",
            "tangorpro"
    ));

    private static final Set<String> DEFAULT_PP_TARGETS = new HashSet<>(Arrays.asList(
            "com.amazon.avod.thirdpartyclient",
            "com.android.chrome",
            "com.breel.wallpapers20",
            "com.disney.disneyplus",
            "com.google.android.aicore",
            "com.google.android.apps.accessibility.magnifier",
            "com.google.android.apps.aiwallpapers",
            "com.google.android.apps.bard",
            "com.google.android.apps.customization.pixel",
            "com.google.android.apps.emojiwallpaper",
            "com.google.android.apps.pixel.agent",
            "com.google.android.apps.pixel.creativeassistant",
            "com.google.android.apps.pixel.nowplaying",
            "com.google.android.apps.pixel.psi",
            "com.google.android.apps.pixel.subzero",
            "com.google.android.apps.pixel.support",
            "com.google.android.apps.privacy.wildlife",
            "com.google.android.apps.subscriptions.red",
            "com.google.android.apps.wallpaper",
            "com.google.android.apps.wallpaper.pixel",
            "com.google.android.apps.weather",
            "com.google.android.googlequicksearchbox",
            "com.google.android.pcs",
            "com.google.android.wallpaper.effects",
            "com.google.pixel.livewallpaper",
            "com.microsoft.android.smsorganizer",
            "com.nhs.online.nhsonline",
            "com.nothing.smartcenter",
            "com.realme.link",
            "in.startv.hotstar",
            "jp.id_credit_sp2.android"
    ));

    private PreferenceCategory mTargetsCategory;
    private SwitchPreferenceCompat mEnabled;
    private SwitchPreferenceCompat mShowSystem;
    private ListPreference mModel;
    private Set<String> mTargets = new HashSet<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pixel_props_settings);
        requireActivity().setTitle(R.string.spoofing_pixel_props_title);

        mTargetsCategory = findPreference(KEY_TARGETS);
        mEnabled = findPreference(KEY_ENABLED);
        mShowSystem = findPreference(KEY_SHOW_SYSTEM);
        mModel = findPreference(KEY_MODEL);
        updateProfileEntries(PixelDeviceRepository.FALLBACK_PROFILES);

        if (mEnabled != null) {
            mEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                Settings.Secure.putInt(requireContext().getContentResolver(), PP_ENABLED_KEY,
                        (Boolean) newValue ? 1 : 0);
                killPackages(mTargets);
                refreshState();
                return true;
            });
        }

        if (mShowSystem != null) {
            mShowSystem.setOnPreferenceChangeListener((preference, newValue) -> {
                mShowSystem.setChecked((Boolean) newValue);
                populateTargets();
                return false;
            });
        }

        if (mModel != null) {
            mModel.setOnPreferenceChangeListener((preference, newValue) -> {
                String value = String.valueOf(newValue);
                Settings.Secure.putString(requireContext().getContentResolver(), PP_MODEL_KEY, value);
                updateModelSummary(value);
                killPackages(mTargets);
                return true;
            });
        }

        Preference selectAll = findPreference(KEY_SELECT_ALL);
        if (selectAll != null) {
            selectAll.setOnPreferenceClickListener(preference -> {
                for (ApplicationInfo app : getDisplayApps()) {
                    mTargets.add(app.packageName);
                }
                writeTargets();
                populateTargets();
                return true;
            });
        }

        Preference reset = findPreference(KEY_RESET);
        if (reset != null) {
            reset.setOnPreferenceClickListener(preference -> {
                mTargets.clear();
                mTargets.addAll(DEFAULT_PP_TARGETS);
                String profile = getDefaultProfile();
                Settings.Secure.putString(requireContext().getContentResolver(), PP_MODEL_KEY, profile);
                writeTargets();
                killPackages(mTargets);
                refreshState();
                populateTargets();
                return true;
            });
        }

        Preference refreshProfiles = findPreference(KEY_REFRESH_PROFILES);
        if (refreshProfiles != null) {
            refreshProfiles.setOnPreferenceClickListener(preference -> {
                Settings.Secure.putString(requireContext().getContentResolver(),
                        PixelDeviceRepository.CACHE_KEY, null);
                loadProfiles(true);
                return true;
            });
        }

        refreshState();
        populateTargets();
        loadProfiles(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshState();
        populateTargets();
    }

    private void refreshState() {
        mTargets = readTargets();
        boolean enabled = Settings.Secure.getInt(requireContext().getContentResolver(),
                PP_ENABLED_KEY, 1) != 0;
        String profile = Settings.Secure.getString(requireContext().getContentResolver(), PP_MODEL_KEY);
        if (profile == null || profile.isEmpty()) {
            profile = getDefaultProfile();
            Settings.Secure.putString(requireContext().getContentResolver(), PP_MODEL_KEY, profile);
        }
        if (mEnabled != null) {
            mEnabled.setChecked(enabled);
        }
        if (mModel != null) {
            mModel.setValue(profile);
            updateModelSummary(profile);
        }
    }

    private void loadProfiles(boolean forceRefresh) {
        Context appContext = requireContext().getApplicationContext();
        Preference refreshProfiles = findPreference(KEY_REFRESH_PROFILES);
        if (refreshProfiles != null) {
            refreshProfiles.setEnabled(false);
            refreshProfiles.setSummary(R.string.pp_profiles_refreshing);
        }
        mExecutor.execute(() -> {
            List<PixelDeviceRepository.PixelProfile> profiles =
                    PixelDeviceRepository.getProfiles(appContext, forceRefresh);
            if (profiles == null || profiles.isEmpty()) {
                profiles = PixelDeviceRepository.FALLBACK_PROFILES;
            }
            final List<PixelDeviceRepository.PixelProfile> resolved = profiles;
            mHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                updateProfileEntries(resolved);
                ensureSelectedProfile();
                if (refreshProfiles != null) {
                    refreshProfiles.setEnabled(true);
                    refreshProfiles.setSummary(R.string.pp_refresh_profiles_summary);
                }
                if (forceRefresh) {
                    Toast.makeText(requireContext(), R.string.pp_profiles_refreshed,
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void updateProfileEntries(List<PixelDeviceRepository.PixelProfile> profiles) {
        if (mModel == null) {
            return;
        }
        List<PixelDeviceRepository.PixelProfile> displayProfiles = new ArrayList<>();
        for (PixelDeviceRepository.PixelProfile profile : profiles) {
            if (profile != null && DISPLAY_PROFILE_CODENAMES.contains(profile.codename)) {
                displayProfiles.add(profile);
            }
        }
        if (displayProfiles.isEmpty()) {
            displayProfiles.addAll(PixelDeviceRepository.FALLBACK_PROFILES);
        }

        CharSequence[] entries = new CharSequence[displayProfiles.size()];
        CharSequence[] values = new CharSequence[displayProfiles.size()];
        for (int i = 0; i < displayProfiles.size(); i++) {
            PixelDeviceRepository.PixelProfile profile = displayProfiles.get(i);
            entries[i] = profile.model != null && !profile.model.isEmpty()
                    ? profile.model : getProfileLabel(profile.codename);
            values[i] = profile.codename;
        }
        mModel.setEntries(entries);
        mModel.setEntryValues(values);
    }

    private void ensureSelectedProfile() {
        String selected = Settings.Secure.getString(requireContext().getContentResolver(),
                PP_MODEL_KEY);
        if (selected == null || selected.isEmpty() || mModel.findIndexOfValue(selected) < 0) {
            selected = getDefaultProfile();
            Settings.Secure.putString(requireContext().getContentResolver(), PP_MODEL_KEY, selected);
        }
        mModel.setValue(selected);
        updateModelSummary(selected);
    }

    private void updateModelSummary(String value) {
        if (mModel == null) {
            return;
        }
        int index = mModel.findIndexOfValue(value);
        mModel.setSummary(index >= 0 ? mModel.getEntries()[index] : getProfileLabel(value));
    }

    private String getProfileLabel(String codename) {
        String label = PixelDeviceRepository.DEVICE_MODEL_MAP.get(codename);
        return label != null && !label.isEmpty() ? label : codename;
    }

    private void populateTargets() {
        if (mTargetsCategory == null) {
            return;
        }
        mTargetsCategory.removeAll();
        List<ApplicationInfo> apps = getDisplayApps();
        if (apps.isEmpty()) {
            Preference empty = new Preference(requireContext());
            empty.setTitle(R.string.ts_no_targets);
            empty.setSummary(R.string.pp_empty_description);
            empty.setSelectable(false);
            mTargetsCategory.addPreference(empty);
            return;
        }

        for (ApplicationInfo app : apps) {
            SwitchPreferenceCompat pref = new SwitchPreferenceCompat(requireContext());
            pref.setKey("pp_target_" + app.packageName);
            pref.setTitle(getAppLabel(app));
            pref.setSummary(app.packageName);
            pref.setIcon(loadIcon(app));
            pref.setChecked(mTargets.contains(app.packageName));
            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                if ((Boolean) newValue) {
                    mTargets.add(app.packageName);
                } else {
                    mTargets.remove(app.packageName);
                }
                writeTargets();
                stopPackage(app.packageName);
                return true;
            });
            mTargetsCategory.addPreference(pref);
        }
        mTargetsCategory.setTitle(getString(R.string.pp_target_count, mTargets.size()));
    }

    private List<ApplicationInfo> getDisplayApps() {
        PackageManager pm = requireContext().getPackageManager();
        boolean showSystem = mShowSystem != null && mShowSystem.isChecked();
        Set<String> overlays = getOverlayPackages(pm);
        List<ApplicationInfo> apps = new ArrayList<>();
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (overlays.contains(app.packageName)) {
                continue;
            }
            boolean isSystem = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (isSystem && !showSystem && !mTargets.contains(app.packageName)) {
                continue;
            }
            apps.add(app);
        }
        Collections.sort(apps, (left, right) -> {
            boolean leftTargeted = mTargets.contains(left.packageName);
            boolean rightTargeted = mTargets.contains(right.packageName);
            if (leftTargeted != rightTargeted) {
                return leftTargeted ? -1 : 1;
            }
            return getAppLabel(left).toString().toLowerCase(Locale.ROOT)
                    .compareTo(getAppLabel(right).toString().toLowerCase(Locale.ROOT));
        });
        return apps;
    }

    private Set<String> getOverlayPackages(PackageManager pm) {
        Set<String> overlays = new HashSet<>();
        for (PackageInfo info : pm.getInstalledPackages(0)) {
            if (info.overlayTarget != null) {
                overlays.add(info.packageName);
            }
        }
        return overlays;
    }

    private Set<String> readTargets() {
        String raw = Settings.Secure.getString(requireContext().getContentResolver(), PP_TARGETS_KEY);
        if (raw == null || raw.trim().isEmpty()) {
            return new HashSet<>(DEFAULT_PP_TARGETS);
        }
        Set<String> targets = new HashSet<>();
        for (String item : raw.split(",")) {
            String value = item.trim();
            if (!value.isEmpty()) {
                targets.add(value);
            }
        }
        return targets;
    }

    private void writeTargets() {
        Settings.Secure.putString(requireContext().getContentResolver(), PP_TARGETS_KEY,
                String.join(",", mTargets));
    }

    private String getDefaultProfile() {
        return requireContext().getResources().getConfiguration().smallestScreenWidthDp >= 600
                ? DEFAULT_TABLET_PROFILE : DEFAULT_PHONE_PROFILE;
    }

    private CharSequence getAppLabel(ApplicationInfo app) {
        return requireContext().getPackageManager().getApplicationLabel(app);
    }

    private android.graphics.drawable.Drawable loadIcon(ApplicationInfo app) {
        try {
            return requireContext().getPackageManager().getApplicationIcon(app);
        } catch (Exception e) {
            return null;
        }
    }

    private void killPackages(Set<String> packages) {
        for (String pkg : packages) {
            stopPackage(pkg);
        }
    }

    private void stopPackage(String packageName) {
        try {
            ActivityManager am = (ActivityManager) requireContext()
                    .getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.forceStopPackage(packageName);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.TESTING;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.pixel_props_settings);
}
