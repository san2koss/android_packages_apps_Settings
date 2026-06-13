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

import android.app.Activity;
import android.app.ActivityManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.internal.util.lineage.PixelDeviceRepository;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class PlayIntegrityFix extends SettingsPreferenceFragment {

    private static final String TAG = "PlayIntegrityFix";
    private static final int REQUEST_IMPORT_CONFIG = 1001;

    private static final String PIF_CONFIG_KEY = "spoof_pif_config";
    private static final String PIF_CONFIG_NAME = "pif.json";
    private static final String PATCH_KEY = "ts_security_patch";
    private static final String FALLBACK_PIF_URL =
            "https://raw.githubusercontent.com/Evolution-X/.github/refs/heads/main/profile/pif.json";

    private static final String VENDING_PACKAGE = "com.android.vending";
    private static final String DROIDGUARD_PACKAGE = "com.google.android.gms.unstable";
    private static final String GMS_PACKAGE = "com.google.android.gms";
    private static final String GMS_PERSISTENT_PACKAGE = "com.google.android.gms.persistent";
    private static final String RKPD_PACKAGE = "com.google.android.rkpdapp";
    private static final String GSF_PACKAGE = "com.google.android.gsf";
    private static final String CONTACT_KEYS_PACKAGE = "com.google.android.contactkeys";
    private static final String SAFETY_CORE_PACKAGE = "com.google.android.safetycore";
    private static final String VELVET_PACKAGE = "com.google.android.googlequicksearchbox";

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.play_integrity_fix);

        Preference fetch = findPreference("pif_fetch_beta");
        if (fetch != null) {
            fetch.setOnPreferenceClickListener(preference -> {
                fetchPixelBetaConfig();
                return true;
            });
        }

        Preference importConfig = findPreference("pif_import_config");
        if (importConfig != null) {
            importConfig.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_IMPORT_CONFIG);
                return true;
            });
        }

        Preference deleteConfig = findPreference("pif_delete_config");
        if (deleteConfig != null) {
            deleteConfig.setOnPreferenceClickListener(preference -> {
                deleteConfig();
                return true;
            });
        }

        refreshStatus();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_CONFIG || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
            if (input == null) {
                toast(getString(R.string.pif_failed, ""));
                return;
            }
            String content = readInputStream(input);
            saveConfig(normalizePifPayload(content), true);
            toast(getString(R.string.pif_imported_as, PIF_CONFIG_NAME));
        } catch (Exception e) {
            toast(getString(R.string.pif_failed, e.getMessage() != null ? e.getMessage() : ""));
        }
    }

    private void fetchPixelBetaConfig() {
        Preference fetch = findPreference("pif_fetch_beta");
        if (fetch != null) {
            fetch.setEnabled(false);
            fetch.setSummary(R.string.pif_fetching);
        }

        Context appContext = requireContext().getApplicationContext();
        mExecutor.execute(() -> {
            try {
                String content = buildPifFromRepository(appContext);
                if (content == null) {
                    try (InputStream input = new URL(FALLBACK_PIF_URL).openStream()) {
                        content = readInputStream(input);
                    }
                }
                final String normalized = normalizePifPayload(content);
                mHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    saveConfig(normalized, false);
                    String model = readConfigData(normalized).get("MODEL");
                    toast(getString(R.string.pif_fetched_model,
                            model != null && !model.isEmpty() ? model : PIF_CONFIG_NAME));
                });
            } catch (Exception e) {
                mHandler.post(() -> {
                    if (isAdded()) {
                        toast(getString(R.string.pif_failed,
                                e.getMessage() != null ? e.getMessage() : ""));
                    }
                });
            } finally {
                mHandler.post(() -> {
                    if (isAdded() && fetch != null) {
                        fetch.setEnabled(true);
                        fetch.setSummary(R.string.pif_fetch_pixel_beta_summary);
                    }
                });
            }
        });
    }

    private String buildPifFromRepository(Context context) {
        try {
            List<PixelDeviceRepository.PixelProfile> profiles =
                    PixelDeviceRepository.getProfiles(context, true);
            if (profiles == null || profiles.isEmpty()) {
                profiles = PixelDeviceRepository.FALLBACK_PROFILES;
            }
            boolean isTablet = context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
            PixelDeviceRepository.PixelProfile profile = findPreferredProfile(profiles, isTablet);
            if (profile == null || profile.fingerprint == null || profile.fingerprint.isEmpty()) {
                return null;
            }

            JSONObject json = new JSONObject();
            json.put("MODEL", profile.model);
            json.put("MANUFACTURER", "Google");
            json.put("BRAND", profile.brand);
            json.put("PRODUCT", profile.product);
            json.put("DEVICE", profile.device);
            json.put("FINGERPRINT", profile.fingerprint);
            json.put("SECURITY_PATCH", profile.securityPatch);
            json.put("ID", profile.buildId);
            json.put("RELEASE", parseRelease(profile.fingerprint));
            json.put("DEVICE_INITIAL_SDK_INT", "34");
            return json.toString(2);
        } catch (Exception e) {
            Log.w(TAG, "PixelDeviceRepository PIF fetch failed", e);
            return null;
        }
    }

    private PixelDeviceRepository.PixelProfile findPreferredProfile(
            List<PixelDeviceRepository.PixelProfile> profiles, boolean isTablet) {
        String preferred = isTablet ? "tangorpro" : "mustang";
        PixelDeviceRepository.PixelProfile fallback = null;
        for (PixelDeviceRepository.PixelProfile profile : profiles) {
            if (profile == null) {
                continue;
            }
            if (preferred.equals(profile.codename)) {
                return profile;
            }
            if (fallback == null) {
                fallback = profile;
            }
        }
        return fallback;
    }

    private String parseRelease(String fingerprint) {
        try {
            int colon = fingerprint.indexOf(':');
            int slash = fingerprint.indexOf('/', colon + 1);
            if (colon >= 0 && slash > colon) {
                return fingerprint.substring(colon + 1, slash);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void saveConfig(String content, boolean manuallyImported) {
        try {
            JSONObject json = new JSONObject(content);
            String fingerprint = json.optString("FINGERPRINT", "");
            if (!fingerprint.isEmpty() && !isValidFingerprint(fingerprint)) {
                toast(getString(R.string.pif_failed, getString(R.string.pif_invalid_fingerprint)));
                return;
            }
            json.put("manually_imported", manuallyImported);
            Settings.Secure.putString(requireContext().getContentResolver(),
                    PIF_CONFIG_KEY, json.toString(2));
            String patch = json.optString("SECURITY_PATCH", "");
            if (!patch.isEmpty()) {
                Settings.Secure.putString(requireContext().getContentResolver(), PATCH_KEY, patch);
            }
            killGms();
            refreshStatus();
        } catch (Exception e) {
            toast(getString(R.string.pif_failed, e.getMessage() != null ? e.getMessage() : ""));
        }
    }

    private void deleteConfig() {
        Settings.Secure.putString(requireContext().getContentResolver(), PIF_CONFIG_KEY, null);
        toast(getString(R.string.pif_deleted, PIF_CONFIG_NAME));
        refreshStatus();
    }

    private void refreshStatus() {
        String content = Settings.Secure.getString(requireContext().getContentResolver(), PIF_CONFIG_KEY);
        Map<String, String> data = content != null && !content.isEmpty()
                ? readConfigData(content) : new LinkedHashMap<>();
        boolean exists = !data.isEmpty();

        Preference active = findPreference("pif_active_config");
        if (active != null) {
            if (exists) {
                String model = data.getOrDefault("MODEL", "");
                String fingerprint = data.getOrDefault("FINGERPRINT", "");
                active.setTitle(PIF_CONFIG_NAME);
                active.setSummary(!model.isEmpty()
                        ? "MODEL: " + model + (!fingerprint.isEmpty()
                                ? "\nFINGERPRINT: " + fingerprint : "")
                        : getString(R.string.pif_config_loaded));
            } else {
                active.setTitle(R.string.pif_active_config);
                active.setSummary(R.string.pif_no_config);
            }
        }

        Preference delete = findPreference("pif_delete_config");
        if (delete != null) {
            delete.setEnabled(exists);
        }
        populateConfigDetails(data);
    }

    private void populateConfigDetails(Map<String, String> data) {
        PreferenceCategory category = findPreference("pif_config_details_category");
        if (category == null) {
            return;
        }
        category.removeAll();
        if (data.isEmpty()) {
            return;
        }

        for (String key : Arrays.asList("MODEL", "MANUFACTURER", "BRAND", "PRODUCT", "DEVICE",
                "FINGERPRINT", "SECURITY_PATCH", "ID", "RELEASE", "DEVICE_INITIAL_SDK_INT")) {
            String value = data.get(key);
            if (value != null) {
                addEditableConfigPreference(category, key, value);
            }
        }

        for (Map.Entry<String, String> entry : data.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("spoof") || "DEBUG".equals(key) || "verboseLogs".equals(key)
                    || "manually_imported".equals(key)) {
                continue;
            }
            if (category.findPreference(key) == null) {
                addEditableConfigPreference(category, key, entry.getValue());
            }
        }
    }

    private void addEditableConfigPreference(PreferenceCategory category, String key, String value) {
        EditTextPreference pref = new EditTextPreference(requireContext());
        pref.setKey(key);
        pref.setTitle(key);
        pref.setSummary(value);
        pref.setText(value);
        pref.setDialogTitle(key);
        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            String updated = String.valueOf(newValue).trim();
            if (updated.isEmpty()) {
                toast(getString(R.string.pif_failed, "Value cannot be empty"));
                return false;
            }
            updateConfigValue(key, updated);
            return true;
        });
        category.addPreference(pref);
    }

    private void updateConfigValue(String key, String value) {
        try {
            String existing = Settings.Secure.getString(requireContext().getContentResolver(), PIF_CONFIG_KEY);
            JSONObject json = existing != null && !existing.isEmpty()
                    ? new JSONObject(existing) : new JSONObject();
            json.put(key, value);
            Settings.Secure.putString(requireContext().getContentResolver(), PIF_CONFIG_KEY,
                    json.toString(2));
            killGms();
            refreshStatus();
        } catch (Exception e) {
            toast(getString(R.string.pif_failed, e.getMessage() != null ? e.getMessage() : ""));
        }
    }

    private Map<String, String> readConfigData(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            String trimmed = content.trim();
            if (trimmed.startsWith("{")) {
                JSONObject json = new JSONObject(trimmed);
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    result.put(key, json.optString(key, ""));
                }
            } else {
                for (String line : trimmed.split("\\R")) {
                    String value = line.trim();
                    if (value.isEmpty() || value.startsWith("#") || value.startsWith("//")) {
                        continue;
                    }
                    int eq = value.indexOf('=');
                    if (eq > 0) {
                        result.put(value.substring(0, eq).trim(), value.substring(eq + 1).trim());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read config", e);
        }
        return result;
    }

    private String normalizePifPayload(String raw) throws Exception {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "{}";
        }
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        JSONObject json = new JSONObject();
        for (String line : trimmed.split("\\R")) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#") || value.startsWith("//")) {
                continue;
            }
            int eq = value.indexOf('=');
            if (eq > 0) {
                String key = value.substring(0, eq).trim();
                String propValue = value.substring(eq + 1).trim().split("#", 2)[0].trim();
                if (!key.isEmpty()) {
                    json.put(key, propValue);
                }
            }
        }
        return json.toString(2);
    }

    private String readInputStream(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private boolean isValidFingerprint(String fingerprint) {
        return fingerprint.matches("^[^/]+/[^/]+/[^:]+:[^/]+/[^/]+/[^:]+:[^/]+/[^:]+$");
    }

    private void killGms() {
        try {
            ActivityManager am = (ActivityManager) requireContext()
                    .getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return;
            }
            for (String pkg : Arrays.asList(VENDING_PACKAGE, DROIDGUARD_PACKAGE, GMS_PACKAGE,
                    GMS_PERSISTENT_PACKAGE, RKPD_PACKAGE, GSF_PACKAGE, CONTACT_KEYS_PACKAGE,
                    SAFETY_CORE_PACKAGE, VELVET_PACKAGE)) {
                am.forceStopPackage(pkg);
            }
            requireContext().getPackageManager().clearApplicationUserData(VENDING_PACKAGE, null);
        } catch (Exception ignored) {
        }
    }

    private void toast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.TESTING;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.play_integrity_fix);
}
