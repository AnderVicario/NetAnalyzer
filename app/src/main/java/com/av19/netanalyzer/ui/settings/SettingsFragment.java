package com.av19.netanalyzer.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.utils.SnackbarUtils;

import java.util.ArrayList;
import java.util.List;

public class SettingsFragment extends Fragment implements SettingsAdapter.OnSettingClickListener {

    private RecyclerView recyclerView;
    private SettingsAdapter adapter;
    private List<SettingsItem> settingsList;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = requireContext().getSharedPreferences("MiAppPrefs", Context.MODE_PRIVATE);
        initViews(view);
        setupSettings();
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerViewSettings);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
    }

    private void setupSettings() {
        settingsList = new ArrayList<>();

        // Obtener preferencias actuales (solo para mostrar estado inicial)
        String currentTheme = prefs.getString("tema", "auto");
        String currentLanguage = prefs.getString("idioma", "es");
        boolean locationEnabled = prefs.getBoolean("ubicacion_habilitada", false);
        String open_router_api_key = prefs.getString("open_router_api_key", "");
        boolean isEmptyAPIKey = open_router_api_key.isEmpty();
        String versionName = getVersionName();

        int themeIndex = getIndexForTheme(currentTheme);
        int languageIndex = getIndexForLanguage(currentLanguage);

        // Item Ubicación (Switch)
        /*settingsList.add(new SettingsItem(
                R.drawable.ic_location,
                getString(R.string.settings_location),
                getString(R.string.settings_location_description),
                SettingsItem.Type.SWITCH,
                locationEnabled,
                "ubicacion"
        ));*/

        // Item Tema (INFO con opciones)
        settingsList.add(new SettingsItem(
                R.drawable.ic_theme,
                getString(R.string.settings_theme),
                getThemeOptionText(themeIndex),
                SettingsItem.Type.INFO,
                new String[]{
                        getString(R.string.settings_theme_default),  // "Sistema"
                        getString(R.string.settings_theme_light),    // "Claro"
                        getString(R.string.settings_theme_dark),     // "Oscuro"
                        getString(R.string.settings_theme_amoled)     // "Ocean"  ← NUEVO
                },
                new String[]{"auto", "light", "dark", "amoled"},
                "tema",
                themeIndex
        ));

        settingsList.add(new SettingsItem(
                R.drawable.ic_key,
                getString(R.string.settings_key),
                getThemeOptionText(isEmptyAPIKey),
                SettingsItem.Type.KEY,
                open_router_api_key,
                "open_router_api_key"
        ));

        // Item Idioma (INFO con opciones)
        settingsList.add(new SettingsItem(
                R.drawable.ic_language,
                getString(R.string.settings_language),
                getLanguageOptionText(languageIndex),
                SettingsItem.Type.INFO,
                new String[]{
                        getString(R.string.settings_language_spanish),
                        getString(R.string.settings_language_basque),
                        getString(R.string.settings_language_english)
                },
                new String[]{"es", "eu", "en"},
                "idioma",
                languageIndex
        ));

        // Item Acerca de (NAVIGATION)
        /*settingsList.add(new SettingsItem(
                R.drawable.ic_about,
                getString(R.string.settings_about),
                getString(R.string.version, versionName),
                SettingsItem.Type.NAVIGATION,
                null,
                "acerca de"
        ));*/

        adapter = new SettingsAdapter(settingsList, this, requireContext());
        recyclerView.setAdapter(adapter);
    }

    private String getVersionName() {
        try {
            PackageInfo pInfo = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0.0";
        }
    }

    private int getIndexForTheme(String theme) {
        switch (theme) {
            case "auto":
                return 0;
            case "light":
                return 1;
            case "dark":
                return 2;
            case "amoled":
                return 3;
            default:
                return 0;
        }
    }

    private int getIndexForLanguage(String language) {
        switch (language) {
            case "es":
                return 0;
            case "eu":
                return 1;
            case "en":
                return 2;
            default:
                return 0;
        }
    }

    private String getThemeOptionText(int index) {
        switch (index) {
            case 1:
                return getString(R.string.settings_theme_light);
            case 2:
                return getString(R.string.settings_theme_dark);
            case 3:
                return getString(R.string.settings_theme_amoled);
            default:
                return getString(R.string.settings_theme_default);
        }
    }

    private String getThemeOptionText(boolean empty) {
        if (empty) {
            return getString(R.string.settings_key_empty);
        }
        return getString(R.string.settings_key_not_empty);
    }

    private String getLanguageOptionText(int index) {
        switch (index) {
            case 1:
                return getString(R.string.settings_language_basque);
            case 2:
                return getString(R.string.settings_language_english);
            default:
                return getString(R.string.settings_language_spanish);
        }
    }

    @Override
    public void onSettingClicked(SettingsItem item, int position) {
        // Manejar clicks en items de tipo NAVIGATION (Acerca de)
        String key = item.getSettingKey();
        if (key != null && key.equals("acerca de")) {
            Intent intent = new Intent(requireContext(), AboutActivity.class);
            startActivity(intent);
        }
        // Otros tipos (INFO) ya manejan expansión interna
    }

    @Override
    public void onOptionSelected(SettingsItem item, int optionIndex) {
        String settingKey = item.getSettingKey();
        String value = item.getOptionValues()[optionIndex];

        if ("tema".equals(settingKey)) {
            if (getActivity() instanceof AppCompatActivity) {
                SettingsOperations.setAppTheme((AppCompatActivity) getActivity(), value);
            }
        } else if ("idioma".equals(settingKey)) {
            if (getActivity() instanceof AppCompatActivity) {
                SettingsOperations.setLanguageChange((AppCompatActivity) getActivity(), value);
            }
        }
    }

    @Override
    public void onSwitchChanged(SettingsItem item, boolean isChecked, int position) {
        // Cambio de switch (ubicación) – sin funcionalidad real
        String key = item.getSettingKey();
        if (key != null) {
            prefs.edit().putBoolean(key, isChecked).apply();
            Toast.makeText(requireContext(),
                    "Ubicación " + (isChecked ? "activada" : "desactivada"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onKeyChanged(SettingsItem item, String newValue) {
        String key = item.getSettingKey();
        if (key != null) {
            prefs.edit().putString(key, newValue).apply();
            SnackbarUtils.showSuccess(requireView(), requireContext(), "Nueva API key establecida. ");
        }
    }
}