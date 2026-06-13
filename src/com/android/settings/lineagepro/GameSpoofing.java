/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.android.settings.lineagepro;

import android.app.AlertDialog;
import android.app.settings.SettingsEnums;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class GameSpoofing extends SettingsPreferenceFragment {

    private static final String TAG = "GameSpoofing";
    private static final String CONFIG_PATH = "/data/system/gameprops";
    private static final String CONFIG_FILE = "gameprops.json";

    private static final Profile[] PRESET_PROFILES = new Profile[] {
            new Profile("ROG Phone 8 Pro", props("MODEL", "ASUS_AI2401_A",
                    "MANUFACTURER", "asus")),
            new Profile("Galaxy S24 Ultra", props("MODEL", "SM-S928B",
                    "MANUFACTURER", "samsung")),
            new Profile("Xiaomi 13 Pro", props("MODEL", "2210132C",
                    "MANUFACTURER", "Xiaomi")),
            new Profile("OnePlus 9 Pro", props("MODEL", "LE2101",
                    "MANUFACTURER", "OnePlus")),
            new Profile("Black Shark 4", props("MODEL", "2SM-X706B",
                    "MANUFACTURER", "blackshark")),
            new Profile("Lenovo Y700", props("MODEL", "Lenovo TB-9707F",
                    "MANUFACTURER", "Lenovo"))
    };

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final List<GameConfig> mGameConfigs = new ArrayList<>();
    private boolean mEnabled;

    private PreferenceCategory mGamesCategory;
    private SwitchPreferenceCompat mEnabledPref;

    private static class GameConfig {
        final String packageName;
        final String appName;
        final Map<String, String> props;

        GameConfig(String packageName, String appName, Map<String, String> props) {
            this.packageName = packageName;
            this.appName = appName;
            this.props = props;
        }
    }

    private static class Profile {
        final String name;
        final Map<String, String> props;

        Profile(String name, Map<String, String> props) {
            this.name = name;
            this.props = props;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.game_spoofing);
        requireActivity().setTitle(R.string.game_spoofing_title);

        mGamesCategory = findPreference("gs_games_category");
        mEnabledPref = findPreference("gs_enabled");

        if (mEnabledPref != null) {
            mEnabledPref.setOnPreferenceChangeListener((preference, newValue) -> {
                mEnabled = (Boolean) newValue;
                saveConfig();
                return true;
            });
        }

        Preference addGame = findPreference("gs_add_game");
        if (addGame != null) {
            addGame.setOnPreferenceClickListener(preference -> {
                showAddGameDialog();
                return true;
            });
        }

        Preference reload = findPreference("gs_reload");
        if (reload != null) {
            reload.setOnPreferenceClickListener(preference -> {
                loadConfig();
                return true;
            });
        }

        File dir = new File(CONFIG_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        loadConfig();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
    }

    private void loadConfig() {
        Config config = readGamePropsConfig();
        mEnabled = config.enabled;
        mGameConfigs.clear();
        PackageManager pm = requireContext().getPackageManager();
        for (GameConfig game : config.games) {
            String label = game.packageName;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(game.packageName, 0)).toString();
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            mGameConfigs.add(new GameConfig(game.packageName, label, game.props));
        }
        if (mEnabledPref != null) {
            mEnabledPref.setChecked(mEnabled);
        }
        populateGameList();
    }

    private void populateGameList() {
        if (mGamesCategory == null) {
            return;
        }
        mGamesCategory.removeAll();
        if (mGameConfigs.isEmpty()) {
            Preference empty = new Preference(requireContext());
            empty.setTitle(R.string.gs_no_apps);
            empty.setSummary(R.string.gs_no_apps_summary);
            empty.setSelectable(false);
            mGamesCategory.addPreference(empty);
            return;
        }
        for (GameConfig game : mGameConfigs) {
            Preference pref = new Preference(requireContext());
            pref.setTitle(game.appName);
            pref.setSummary(game.packageName + "\n" + propsToSummary(game.props));
            pref.setOnPreferenceClickListener(preference -> {
                showGameOptionsDialog(game);
                return true;
            });
            mGamesCategory.addPreference(pref);
        }
    }

    private void showAddGameDialog() {
        List<ApplicationInfo> apps = getInstalledUserApps();
        List<ApplicationInfo> available = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if (findGame(app.packageName) == null) {
                available.add(app);
            }
        }
        String[] labels = new String[available.size()];
        PackageManager pm = requireContext().getPackageManager();
        for (int i = 0; i < available.size(); i++) {
            labels[i] = pm.getApplicationLabel(available.get(i)).toString();
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.gs_select_app)
                .setItems(labels, (dialog, which) -> {
                    ApplicationInfo app = available.get(which);
                    showProfileSelector(new GameConfig(app.packageName, labels[which],
                            new LinkedHashMap<>()));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showGameOptionsDialog(GameConfig game) {
        String[] options = {
                getString(R.string.gs_edit_props),
                getString(R.string.gs_change_profile),
                getString(R.string.gs_remove)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(game.appName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showEditPropsDialog(game);
                    } else if (which == 1) {
                        showProfileSelector(game);
                    } else {
                        showDeleteGameDialog(game);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showProfileSelector(GameConfig game) {
        String[] names = new String[PRESET_PROFILES.length];
        for (int i = 0; i < PRESET_PROFILES.length; i++) {
            names[i] = PRESET_PROFILES[i].name;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.gs_select_profile)
                .setItems(names, (dialog, which) -> {
                    upsertGame(new GameConfig(game.packageName, game.appName,
                            new LinkedHashMap<>(PRESET_PROFILES[which].props)));
                    saveConfig();
                    populateGameList();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEditPropsDialog(GameConfig game) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);

        List<EditText[]> fields = new ArrayList<>();
        Map<String, String> props = game.props.isEmpty()
                ? props("MODEL", "") : game.props;
        for (Map.Entry<String, String> entry : props.entrySet()) {
            EditText key = new EditText(requireContext());
            key.setHint(R.string.gs_key);
            key.setText(entry.getKey());
            EditText value = new EditText(requireContext());
            value.setHint(R.string.gs_value);
            value.setText(entry.getValue());
            container.addView(key);
            container.addView(value);
            fields.add(new EditText[] { key, value });
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.gs_edit_app, game.appName))
                .setView(container)
                .setPositiveButton(R.string.gs_save, (dialog, which) -> {
                    Map<String, String> updated = new LinkedHashMap<>();
                    for (EditText[] pair : fields) {
                        String key = pair[0].getText().toString().trim();
                        String value = pair[1].getText().toString().trim();
                        if (!key.isEmpty()) {
                            updated.put(key, value);
                        }
                    }
                    upsertGame(new GameConfig(game.packageName, game.appName, updated));
                    saveConfig();
                    populateGameList();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDeleteGameDialog(GameConfig game) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.gs_remove_title)
                .setMessage(getString(R.string.gs_remove_message, game.appName))
                .setPositiveButton(R.string.gs_remove, (dialog, which) -> {
                    mGameConfigs.removeIf(item -> item.packageName.equals(game.packageName));
                    saveConfig();
                    populateGameList();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveConfig() {
        List<GameConfig> snapshot = new ArrayList<>(mGameConfigs);
        boolean enabled = mEnabled;
        mExecutor.execute(() -> writeGamePropsConfig(enabled, snapshot));
        Toast.makeText(requireContext(), R.string.gs_config_saved, Toast.LENGTH_SHORT).show();
    }

    private List<ApplicationInfo> getInstalledUserApps() {
        PackageManager pm = requireContext().getPackageManager();
        List<ApplicationInfo> apps = new ArrayList<>();
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                apps.add(app);
            }
        }
        apps.sort((left, right) -> pm.getApplicationLabel(left).toString().toLowerCase(Locale.ROOT)
                .compareTo(pm.getApplicationLabel(right).toString().toLowerCase(Locale.ROOT)));
        return apps;
    }

    private GameConfig findGame(String packageName) {
        for (GameConfig game : mGameConfigs) {
            if (game.packageName.equals(packageName)) {
                return game;
            }
        }
        return null;
    }

    private void upsertGame(GameConfig game) {
        for (int i = 0; i < mGameConfigs.size(); i++) {
            if (mGameConfigs.get(i).packageName.equals(game.packageName)) {
                mGameConfigs.set(i, game);
                return;
            }
        }
        mGameConfigs.add(game);
    }

    private String propsToSummary(Map<String, String> props) {
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            items.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join(", ", items);
    }

    private static class Config {
        final boolean enabled;
        final List<GameConfig> games;

        Config(boolean enabled, List<GameConfig> games) {
            this.enabled = enabled;
            this.games = games;
        }
    }

    private Config readGamePropsConfig() {
        File file = new File(CONFIG_PATH, CONFIG_FILE);
        if (!file.exists()) {
            return new Config(false, new ArrayList<>());
        }
        try {
            String content;
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] bytes = new byte[(int) file.length()];
                int read = input.read(bytes);
                content = new String(bytes, 0, Math.max(read, 0), StandardCharsets.UTF_8);
            }
            JSONObject json = new JSONObject(content);
            boolean enabled = json.optBoolean("enabled", false);
            List<GameConfig> games = new ArrayList<>();
            JSONObject gamesObj = json.optJSONObject("games");
            if (gamesObj != null) {
                Iterator<String> packages = gamesObj.keys();
                while (packages.hasNext()) {
                    String pkg = packages.next();
                    JSONObject propsObj = gamesObj.getJSONObject(pkg);
                    Map<String, String> props = new LinkedHashMap<>();
                    Iterator<String> keys = propsObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        props.put(key, propsObj.optString(key, ""));
                    }
                    games.add(new GameConfig(pkg, pkg, props));
                }
            }
            return new Config(enabled, games);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load config", e);
            return new Config(false, new ArrayList<>());
        }
    }

    private void writeGamePropsConfig(boolean enabled, List<GameConfig> games) {
        try {
            File dir = new File(CONFIG_PATH);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            JSONObject json = new JSONObject();
            json.put("enabled", enabled);
            JSONObject gamesObj = new JSONObject();
            for (GameConfig game : games) {
                JSONObject propsObj = new JSONObject();
                for (Map.Entry<String, String> entry : game.props.entrySet()) {
                    propsObj.put(entry.getKey(), entry.getValue());
                }
                gamesObj.put(game.packageName, propsObj);
            }
            json.put("games", gamesObj);
            File file = new File(dir, CONFIG_FILE);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            file.setReadable(true, false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save config", e);
        }
    }

    private static Map<String, String> props(String... values) {
        Map<String, String> props = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            props.put(values[i], values[i + 1]);
        }
        return props;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.TESTING;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.game_spoofing);
}
