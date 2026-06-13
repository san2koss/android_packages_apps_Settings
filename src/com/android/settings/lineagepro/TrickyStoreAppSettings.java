/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.android.settings.lineagepro;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class TrickyStoreAppSettings extends SettingsPreferenceFragment {

    private static final String TARGET_KEY = TrickyStore.TARGET_KEY;

    private static final String KEY_SHOW_SYSTEM = "ts_show_system_apps";
    private static final String KEY_SELECT_ALL = "ts_select_all";
    private static final String KEY_ADD_INSTALLED = "ts_add_installed";
    private static final String KEY_RESET_TARGETS = "ts_reset_targets";
    private static final String KEY_APPS_CATEGORY = "ts_apps_category";

    private static final Set<String> DEFAULT_TARGETS = new HashSet<>(Arrays.asList(
            "android",
            "com.android.vending",
            "com.google.android.gsf",
            "com.google.android.gms",
            "com.google.android.contactkeys",
            "com.google.android.ims",
            "com.google.android.safetycore",
            "com.google.android.apps.walletnfcrel",
            "com.google.android.apps.nbu.paisa.user"
    ));

    private static final Map<String, TargetMode> DEFAULT_TARGET_MODES = new LinkedHashMap<>();
    static {
        DEFAULT_TARGET_MODES.put("com.revolut.revolut", TargetMode.CERT);
        DEFAULT_TARGET_MODES.put("io.github.qwq233.keyattestation", TargetMode.LEAF);
        DEFAULT_TARGET_MODES.put("com.eltavine.duckdetector", TargetMode.LEAF);
        DEFAULT_TARGET_MODES.put("com.rem01gaming.disclosure", TargetMode.LEAF);
        DEFAULT_TARGET_MODES.put("wu.keyChain.test", TargetMode.LEAF);
        DEFAULT_TARGET_MODES.put("com.kikyps.crackme", TargetMode.LEAF);
        DEFAULT_TARGET_MODES.put("com.chunqiunativecheck", TargetMode.LEAF);
    }

    private static final Set<String> DETECTOR_PACKAGES = new HashSet<>(Arrays.asList(
            "icu.nullptr.nativetest",
            "icu.nullptr.applistdetector",
            "io.github.vvb2060.keyattestation",
            "io.github.vvb2060.mahoshojo",
            "com.scottyab.rootbeer",
            "com.topjohnwu.magisk.detector",
            "com.zhenxi.hunter",
            "rikka.safetynetchecker",
            "com.reveny.nativechecker",
            "com.reveny.environmentchecker",
            "com.reveny.rootchecker",
            "io.github.a13e300.tricky_store",
            "io.github.a13e300.tricky_store.debug",
            "com.topjohnwu.magisk",
            "me.weishu.kernelsu",
            "moe.shizuku.privileged.api"
    ));

    private static final List<String> EXCLUDED_SUFFIXES = Arrays.asList(
            ".auto_generated", ".appsearch", ".backup", ".carrier",
            ".cellbroadcast", ".cts", ".federated", ".ims", ".overlay",
            ".qti", ".qualcomm", ".resources", ".systemui.clocks",
            ".systemui.plugin", ".theme", ".iconpack"
    );

    private PreferenceCategory mAppsCategory;
    private SwitchPreferenceCompat mShowSystem;
    private Map<String, TargetMode> mTargets = new LinkedHashMap<>();

    private enum TargetMode {
        AUTO(""), LEAF("?"), CERT("!");

        final String symbol;

        TargetMode(String symbol) {
            this.symbol = symbol;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.tricky_store_app_settings);
        requireActivity().setTitle(R.string.ts_manage_target_apps);

        mAppsCategory = findPreference(KEY_APPS_CATEGORY);
        mShowSystem = findPreference(KEY_SHOW_SYSTEM);

        if (mShowSystem != null) {
            mShowSystem.setOnPreferenceChangeListener((preference, newValue) -> {
                mShowSystem.setChecked((Boolean) newValue);
                populateApps();
                return false;
            });
        }

        Preference selectAll = findPreference(KEY_SELECT_ALL);
        if (selectAll != null) {
            selectAll.setOnPreferenceClickListener(preference -> {
                for (ApplicationInfo app : getDisplayApps()) {
                    mTargets.put(app.packageName, modeOrDefault(app.packageName));
                }
                writeTargets();
                populateApps();
                return true;
            });
        }

        Preference addInstalled = findPreference(KEY_ADD_INSTALLED);
        if (addInstalled != null) {
            addInstalled.setOnPreferenceClickListener(preference -> {
                PackageManager pm = requireContext().getPackageManager();
                for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
                    boolean isSystem = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    if (!isSystem && !DETECTOR_PACKAGES.contains(app.packageName)) {
                        mTargets.put(app.packageName, modeOrDefault(app.packageName));
                    }
                }
                writeTargets();
                populateApps();
                return true;
            });
        }

        Preference reset = findPreference(KEY_RESET_TARGETS);
        if (reset != null) {
            reset.setOnPreferenceClickListener(preference -> {
                resetTargets();
                populateApps();
                return true;
            });
        }

        seedDefaultsIfEmpty();
        refreshState();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshState();
    }

    private void refreshState() {
        mTargets = readTargets();
        populateApps();
    }

    private void populateApps() {
        if (mAppsCategory == null) {
            return;
        }
        mAppsCategory.removeAll();
        mAppsCategory.setTitle(getString(R.string.ts_target_apps_count, mTargets.size()));
        List<ApplicationInfo> apps = getDisplayApps();
        if (apps.isEmpty()) {
            Preference empty = new Preference(requireContext());
            empty.setTitle(R.string.ts_no_targets);
            empty.setSelectable(false);
            mAppsCategory.addPreference(empty);
            return;
        }

        for (ApplicationInfo app : apps) {
            SwitchPreferenceCompat pref = new SwitchPreferenceCompat(requireContext());
            pref.setKey("ts_target_" + app.packageName);
            pref.setTitle(getAppLabel(app));
            pref.setSummary(buildSummary(app.packageName));
            pref.setIcon(loadIcon(app));
            pref.setChecked(mTargets.containsKey(app.packageName));
            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                if ((Boolean) newValue) {
                    mTargets.put(app.packageName, modeOrDefault(app.packageName));
                    showModeDialog(app.packageName);
                } else {
                    mTargets.remove(app.packageName);
                    stopPackage(app.packageName);
                }
                writeTargets();
                populateApps();
                return true;
            });
            pref.setOnPreferenceClickListener(preference -> {
                if (mTargets.containsKey(app.packageName)) {
                    showModeDialog(app.packageName);
                    return true;
                }
                return false;
            });
            mAppsCategory.addPreference(pref);
        }
    }

    private void showModeDialog(String packageName) {
        String[] entries = getResources().getStringArray(R.array.ts_target_mode_entries);
        TargetMode current = mTargets.getOrDefault(packageName, TargetMode.AUTO);
        int checked = current == TargetMode.LEAF ? 1 : current == TargetMode.CERT ? 2 : 0;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ts_target_mode)
                .setSingleChoiceItems(entries, checked, (dialog, which) -> {
                    TargetMode mode = which == 1 ? TargetMode.LEAF
                            : which == 2 ? TargetMode.CERT : TargetMode.AUTO;
                    mTargets.put(packageName, mode);
                    writeTargets();
                    populateApps();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private List<ApplicationInfo> getDisplayApps() {
        PackageManager pm = requireContext().getPackageManager();
        boolean showSystem = mShowSystem != null && mShowSystem.isChecked();
        List<ApplicationInfo> apps = new ArrayList<>();
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            boolean isSystem = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean targeted = mTargets.containsKey(app.packageName);
            if (DETECTOR_PACKAGES.contains(app.packageName)) {
                continue;
            }
            if (isSystem && hasExcludedSuffix(app.packageName) && !targeted) {
                continue;
            }
            if (isSystem && !showSystem && !targeted) {
                continue;
            }
            apps.add(app);
        }
        Collections.sort(apps, (left, right) -> {
            boolean leftTargeted = mTargets.containsKey(left.packageName);
            boolean rightTargeted = mTargets.containsKey(right.packageName);
            if (leftTargeted != rightTargeted) {
                return leftTargeted ? -1 : 1;
            }
            return getAppLabel(left).toString().toLowerCase(Locale.ROOT)
                    .compareTo(getAppLabel(right).toString().toLowerCase(Locale.ROOT));
        });
        return apps;
    }

    private boolean hasExcludedSuffix(String packageName) {
        for (String suffix : EXCLUDED_SUFFIXES) {
            if (packageName.contains(suffix)) {
                return true;
            }
        }
        return false;
    }

    private void seedDefaultsIfEmpty() {
        String raw = Settings.Secure.getString(requireContext().getContentResolver(), TARGET_KEY);
        if (raw != null && !raw.trim().isEmpty()) {
            return;
        }
        resetTargets();
    }

    private void resetTargets() {
        mTargets.clear();
        for (String pkg : DEFAULT_TARGETS) {
            mTargets.put(pkg, TargetMode.AUTO);
        }
        mTargets.putAll(DEFAULT_TARGET_MODES);
        writeTargets();
    }

    private Map<String, TargetMode> readTargets() {
        Map<String, TargetMode> targets = new LinkedHashMap<>();
        String raw = Settings.Secure.getString(requireContext().getContentResolver(), TARGET_KEY);
        if (raw == null || raw.trim().isEmpty()) {
            return targets;
        }
        for (String line : raw.split("\\R")) {
            String value = line.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.endsWith("?")) {
                targets.put(value.substring(0, value.length() - 1), TargetMode.LEAF);
            } else if (value.endsWith("!")) {
                targets.put(value.substring(0, value.length() - 1), TargetMode.CERT);
            } else {
                targets.put(value, TargetMode.AUTO);
            }
        }
        return targets;
    }

    private void writeTargets() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, TargetMode> entry : mTargets.entrySet()) {
            lines.add(entry.getKey() + entry.getValue().symbol);
        }
        Settings.Secure.putString(requireContext().getContentResolver(), TARGET_KEY,
                String.join("\n", lines));
        Toast.makeText(requireContext(), R.string.ts_targets_saved, Toast.LENGTH_SHORT).show();
    }

    private TargetMode modeOrDefault(String packageName) {
        return DEFAULT_TARGET_MODES.getOrDefault(packageName, TargetMode.AUTO);
    }

    private String buildSummary(String packageName) {
        TargetMode mode = mTargets.get(packageName);
        if (mode == null) {
            return packageName;
        }
        int modeRes = mode == TargetMode.LEAF ? R.string.ts_target_mode_leaf
                : mode == TargetMode.CERT ? R.string.ts_target_mode_cert
                : R.string.ts_target_mode_auto;
        return packageName + "\n" + getString(modeRes);
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
            new BaseSearchIndexProvider(R.xml.tricky_store_app_settings);
}
