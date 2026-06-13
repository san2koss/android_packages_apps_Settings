/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.android.settings.lineagepro;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.widget.EditText;
import android.widget.Toast;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class TrickyStore extends SettingsPreferenceFragment {

    private static final int REQUEST_IMPORT_KEYBOX = 2001;
    private static final int REQUEST_IMPORT_TARGETS = 2002;

    static final String TARGET_KEY = "spoof_trickystore_target";

    private static final String KEYBOX_KEY = "spoof_trickystore_keybox";
    private static final String PATCH_KEY = "spoof_trickystore_patch";
    private static final String LAST_FETCHED_KEY = "spoof_trickystore_last_fetched";
    private static final String LAST_REVOCATION_CHECK_KEY = "spoof_trickystore_last_revocation_check";
    private static final String LAST_REVOCATION_STATUS_KEY = "spoof_trickystore_last_revocation_status";

    private static final String REVOCATION_URL =
            "https://android.googleapis.com/attestation/status?encrypted=0";
    private static final String OFFICIAL_KEYBOX_URL =
            "https://git.evolution-x.org/EvoX/keybox/raw/branch/main/keybox.xml";

    private static final List<String> GMS_PACKAGES = Arrays.asList(
            "com.android.vending",
            "com.google.android.gms.unstable",
            "com.google.android.gms",
            "com.google.android.gms.persistent",
            "com.google.android.rkpdapp",
            "com.google.android.gsf",
            "com.google.android.contactkeys",
            "com.google.android.safetycore",
            "com.google.android.googlequicksearchbox"
    );

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.tricky_store);
        requireActivity().setTitle(R.string.tricky_store_title);

        Preference importKeybox = findPreference("ts_import_keybox");
        if (importKeybox != null) {
            importKeybox.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_IMPORT_KEYBOX);
                return true;
            });
        }

        Preference fetchKeybox = findPreference("ts_fetch_keybox");
        if (fetchKeybox != null) {
            fetchKeybox.setOnPreferenceClickListener(preference -> {
                fetchOfficialKeybox();
                return true;
            });
        }

        Preference deleteKeybox = findPreference("ts_delete_keybox");
        if (deleteKeybox != null) {
            deleteKeybox.setOnPreferenceClickListener(preference -> {
                showDeleteKeyboxDialog();
                return true;
            });
        }

        Preference importTargets = findPreference("ts_import_targets");
        if (importTargets != null) {
            importTargets.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/*");
                startActivityForResult(intent, REQUEST_IMPORT_TARGETS);
                return true;
            });
        }

        Preference revocation = findPreference("ts_revocation_status");
        if (revocation != null) {
            revocation.setOnPreferenceClickListener(preference -> {
                checkKeyboxRevocation();
                return true;
            });
        }

        Preference patch = findPreference("ts_security_patch");
        if (patch != null) {
            patch.setOnPreferenceClickListener(preference -> {
                showPatchDateDialog();
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
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return;
            }
            byte[] bytes = readBytes(input);
            if (requestCode == REQUEST_IMPORT_KEYBOX) {
                Settings.Secure.putString(requireContext().getContentResolver(), KEYBOX_KEY,
                        Base64.encodeToString(bytes, Base64.NO_WRAP));
                Settings.Secure.putLong(requireContext().getContentResolver(), LAST_FETCHED_KEY,
                        System.currentTimeMillis());
                Settings.Secure.putString(requireContext().getContentResolver(),
                        LAST_REVOCATION_STATUS_KEY, "");
                killGms();
                toast(getString(R.string.ts_keybox_imported));
                checkKeyboxRevocation();
            } else if (requestCode == REQUEST_IMPORT_TARGETS) {
                Settings.Secure.putString(requireContext().getContentResolver(), TARGET_KEY,
                        new String(bytes, StandardCharsets.UTF_8));
                toast(getString(R.string.ts_target_list_imported));
            }
            refreshStatus();
        } catch (Exception e) {
            toast(getString(R.string.ts_failed, e.getMessage() != null ? e.getMessage() : ""));
        }
    }

    private void refreshStatus() {
        boolean keyboxExists = !isEmpty(Settings.Secure.getString(
                requireContext().getContentResolver(), KEYBOX_KEY));

        Preference importKeybox = findPreference("ts_import_keybox");
        if (importKeybox != null) {
            importKeybox.setSummary(keyboxExists ? R.string.ts_keybox_installed
                    : R.string.ts_no_keybox);
        }

        Preference deleteKeybox = findPreference("ts_delete_keybox");
        if (deleteKeybox != null) {
            deleteKeybox.setEnabled(keyboxExists);
        }

        Preference manageTargets = findPreference("ts_manage_targets");
        if (manageTargets != null) {
            int count = countTargets();
            manageTargets.setSummary(count > 0 ? getString(R.string.ts_target_apps_count, count)
                    : getString(R.string.ts_no_targets));
        }

        Preference patch = findPreference("ts_security_patch");
        if (patch != null) {
            String patchDate = Settings.Secure.getString(requireContext().getContentResolver(),
                    PATCH_KEY);
            patch.setSummary(isEmpty(patchDate) ? getString(R.string.ts_no_patch) : patchDate);
        }

        Preference fetchKeybox = findPreference("ts_fetch_keybox");
        if (fetchKeybox != null) {
            String fetched = getLastFetchedFormatted();
            fetchKeybox.setSummary(fetched != null
                    ? getString(R.string.ts_fetch_keybox_last_fetched, fetched)
                    : getString(R.string.ts_fetch_keybox_summary));
        }

        applyRevocationStatus();
    }

    private void fetchOfficialKeybox() {
        Preference fetchKeybox = findPreference("ts_fetch_keybox");
        if (fetchKeybox != null) {
            fetchKeybox.setEnabled(false);
            fetchKeybox.setSummary(R.string.ts_fetch_keybox_fetching);
        }
        Context appContext = requireContext().getApplicationContext();
        mExecutor.execute(() -> {
            try {
                String xml = readUrl(OFFICIAL_KEYBOX_URL);
                String existing = decodeKeybox(Settings.Secure.getString(
                        appContext.getContentResolver(), KEYBOX_KEY));
                if (existing != null && existing.trim().equals(xml.trim())) {
                    mHandler.post(() -> {
                        if (isAdded()) {
                            toast(getString(R.string.ts_fetch_keybox_same_file));
                        }
                    });
                    return;
                }
                Settings.Secure.putString(appContext.getContentResolver(), KEYBOX_KEY,
                        Base64.encodeToString(xml.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                Settings.Secure.putLong(appContext.getContentResolver(), LAST_FETCHED_KEY,
                        System.currentTimeMillis());
                Settings.Secure.putString(appContext.getContentResolver(),
                        LAST_REVOCATION_STATUS_KEY, "");
                killGms(appContext);
                mHandler.post(() -> {
                    if (!isAdded()) return;
                    toast(getString(R.string.ts_fetch_keybox_success));
                    refreshStatus();
                    checkKeyboxRevocation();
                });
            } catch (Exception e) {
                mHandler.post(() -> {
                    if (isAdded()) {
                        toast(getString(R.string.ts_fetch_keybox_failed,
                                e.getMessage() != null ? e.getMessage() : ""));
                    }
                });
            } finally {
                mHandler.post(() -> {
                    if (isAdded() && fetchKeybox != null) {
                        fetchKeybox.setEnabled(true);
                        refreshStatus();
                    }
                });
            }
        });
    }

    private void checkKeyboxRevocation() {
        String raw = Settings.Secure.getString(requireContext().getContentResolver(), KEYBOX_KEY);
        String xml = decodeKeybox(raw);
        if (isEmpty(xml)) {
            applyRevocationStatus();
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        Preference revocation = findPreference("ts_revocation_status");
        if (revocation != null) {
            revocation.setSummary(R.string.ts_revocation_checking);
        }
        mExecutor.execute(() -> {
            try {
                List<String> serials = extractCertSerials(xml);
                JSONObject entries = new JSONObject(readUrl(REVOCATION_URL))
                        .optJSONObject("entries");
                String status = "VALID";
                if (entries != null) {
                    for (String serial : serials) {
                        JSONObject entry = entries.optJSONObject(serial);
                        if (entry == null) continue;
                        String itemStatus = entry.optString("status", "").toUpperCase(Locale.ROOT);
                        if ("REVOKED".equals(itemStatus) || "SUSPENDED".equals(itemStatus)) {
                            status = itemStatus;
                            break;
                        }
                    }
                }
                final String finalStatus = status;
                Settings.Secure.putString(appContext.getContentResolver(),
                        LAST_REVOCATION_STATUS_KEY, finalStatus);
                Settings.Secure.putLong(appContext.getContentResolver(),
                        LAST_REVOCATION_CHECK_KEY, System.currentTimeMillis());
                mHandler.post(() -> {
                    if (isAdded()) {
                        applyRevocationStatus();
                    }
                });
            } catch (Exception e) {
                mHandler.post(() -> {
                    if (isAdded() && revocation != null) {
                        revocation.setSummary(getString(R.string.ts_revocation_error,
                                e.getMessage() != null ? e.getMessage() : ""));
                    }
                });
            }
        });
    }

    private void applyRevocationStatus() {
        Preference pref = findPreference("ts_revocation_status");
        if (pref == null) return;
        String raw = Settings.Secure.getString(requireContext().getContentResolver(), KEYBOX_KEY);
        if (isEmpty(raw)) {
            pref.setSummary(R.string.ts_revocation_no_keybox);
            return;
        }
        String status = Settings.Secure.getString(requireContext().getContentResolver(),
                LAST_REVOCATION_STATUS_KEY);
        String summary;
        if ("VALID".equals(status)) {
            summary = getString(R.string.ts_revocation_valid);
        } else if ("REVOKED".equals(status)) {
            summary = getString(R.string.ts_revocation_revoked, "");
        } else if ("SUSPENDED".equals(status)) {
            summary = getString(R.string.ts_revocation_suspended, "");
        } else {
            summary = getString(R.string.ts_revocation_not_yet_checked);
        }
        String checked = getLastRevocationCheckedFormatted();
        pref.setSummary(checked == null ? summary
                : summary + "\n" + getString(R.string.ts_revocation_last_checked, checked));
    }

    private void showDeleteKeyboxDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ts_delete_keybox_title)
                .setMessage(R.string.ts_delete_keybox_message)
                .setPositiveButton(R.string.ts_delete, (dialog, which) -> {
                    Settings.Secure.putString(requireContext().getContentResolver(), KEYBOX_KEY, "");
                    Settings.Secure.putLong(requireContext().getContentResolver(), LAST_FETCHED_KEY, 0L);
                    Settings.Secure.putString(requireContext().getContentResolver(),
                            LAST_REVOCATION_STATUS_KEY, "");
                    Settings.Secure.putLong(requireContext().getContentResolver(),
                            LAST_REVOCATION_CHECK_KEY, 0L);
                    toast(getString(R.string.ts_keybox_deleted));
                    refreshStatus();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showPatchDateDialog() {
        String current = Settings.Secure.getString(requireContext().getContentResolver(), PATCH_KEY);
        EditText input = new EditText(requireContext());
        input.setText(current != null ? current : "");
        input.setHint(R.string.ts_patch_date_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ts_security_patch)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty() && !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        toast(getString(R.string.ts_invalid_patch_date));
                        return;
                    }
                    Settings.Secure.putString(requireContext().getContentResolver(), PATCH_KEY, value);
                    refreshStatus();
                })
                .setNeutralButton(R.string.ts_delete, (dialog, which) -> {
                    Settings.Secure.putString(requireContext().getContentResolver(), PATCH_KEY, "");
                    refreshStatus();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int countTargets() {
        String content = Settings.Secure.getString(requireContext().getContentResolver(), TARGET_KEY);
        if (isEmpty(content)) return 0;
        int count = 0;
        for (String line : content.split("\\R")) {
            if (!line.trim().isEmpty()) count++;
        }
        return count;
    }

    private String decodeKeybox(String payload) {
        if (isEmpty(payload)) return null;
        String trimmed = payload.trim();
        if (trimmed.startsWith("<")) return trimmed;
        try {
            String decoded = new String(Base64.decode(trimmed, Base64.DEFAULT), StandardCharsets.UTF_8);
            return decoded.trim().startsWith("<") ? decoded : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> extractCertSerials(String xml) throws Exception {
        List<String> serials = new ArrayList<>();
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        Matcher matcher = Pattern.compile(
                "-----BEGIN CERTIFICATE-----([\\s\\S]+?)-----END CERTIFICATE-----")
                .matcher(xml);
        while (matcher.find()) {
            byte[] der = Base64.decode(matcher.group(1).replaceAll("\\s", ""), Base64.DEFAULT);
            X509Certificate cert = (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(der));
            serials.add(cert.getSerialNumber().toString(16).toUpperCase(Locale.ROOT));
        }
        return serials;
    }

    private String readUrl(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException("HTTP " + conn.getResponseCode());
        }
        try (InputStream input = conn.getInputStream()) {
            return new String(readBytes(input), StandardCharsets.UTF_8);
        }
    }

    private byte[] readBytes(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String getLastFetchedFormatted() {
        long millis = Settings.Secure.getLong(requireContext().getContentResolver(),
                LAST_FETCHED_KEY, 0L);
        return millis == 0L ? null : formatMillis(millis);
    }

    private String getLastRevocationCheckedFormatted() {
        long millis = Settings.Secure.getLong(requireContext().getContentResolver(),
                LAST_REVOCATION_CHECK_KEY, 0L);
        return millis == 0L ? null : formatMillis(millis);
    }

    private String formatMillis(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    private void killGms() {
        killGms(requireContext());
    }

    private void killGms(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            for (String pkg : GMS_PACKAGES) {
                am.forceStopPackage(pkg);
            }
            context.getPackageManager().clearApplicationUserData("com.android.vending", null);
        } catch (Exception ignored) {
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void toast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.TESTING;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.tricky_store);
}
