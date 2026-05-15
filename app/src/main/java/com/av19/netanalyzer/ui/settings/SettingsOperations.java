package com.av19.netanalyzer.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.utils.LocaleManager;

public class SettingsOperations {

    public static void setAppTheme(AppCompatActivity activity, String mode) {
        SharedPreferences prefs = activity.getSharedPreferences("MiAppPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("tema", mode).apply();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            activity.finish();
            activity.startActivity(new Intent(activity, activity.getClass()));
            activity.overridePendingTransition(R.anim.fade_in_log, R.anim.fade_out_log);
        }, 300);
    }

    public static void setLanguageChange(AppCompatActivity activity, String languageCode) {
        LocaleManager.setNewLocale(activity, languageCode);
        new Handler(Looper.getMainLooper()).postDelayed(activity::recreate, 300);
    }
}