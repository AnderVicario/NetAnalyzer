package com.av19.netanalyzer.ui.settings;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.utils.LocaleManager;

public class SettingsOperations {

    public static void setAppTheme(AppCompatActivity activity, String mode) {
        SharedPreferences prefs = activity.getSharedPreferences("MiAppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("tema", mode).apply();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            applyTheme(activity, mode);
        }, 300);
    }

    private static void applyTheme(AppCompatActivity activity, String mode) {
        // Obtener el modo actual del sistema
        int currentNightMode = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isCurrentlyDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES;
        boolean isCurrentlyLight = currentNightMode == Configuration.UI_MODE_NIGHT_NO;

        // Determinar el modo objetivo
        int targetNightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        boolean shouldApplyTheme = true;

        if ("light".equals(mode)) {
            targetNightMode = AppCompatDelegate.MODE_NIGHT_NO;
            shouldApplyTheme = !isCurrentlyLight;
        } else if ("dark".equals(mode)) {
            targetNightMode = AppCompatDelegate.MODE_NIGHT_YES;
            shouldApplyTheme = !isCurrentlyDark;
        } else {
            int currentAppMode = AppCompatDelegate.getDefaultNightMode();
            shouldApplyTheme = currentAppMode != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }

        if (!shouldApplyTheme) {
            return;
        }

        activity.finish();
        activity.startActivity(new Intent(activity, activity.getClass()));
        activity.overridePendingTransition(R.anim.fade_in_log, R.anim.fade_out_log);
        AppCompatDelegate.setDefaultNightMode(targetNightMode);
    }

    public static void setLanguageChange(AppCompatActivity activity, String languageCode) {
        LocaleManager.setNewLocale(activity, languageCode);

        new Handler(Looper.getMainLooper()).postDelayed(activity::recreate, 300);
    }

    /*public static void showInfoDialog(AppCompatActivity activity) {
        new MaterialAlertDialogBuilder(activity, R.style.RoundedDialog)
                .setTitle("Acerca de")
                .setMessage("Información de tu aplicación")
                .setPositiveButton("Aceptar", null)
                .show();
    }*/

}
