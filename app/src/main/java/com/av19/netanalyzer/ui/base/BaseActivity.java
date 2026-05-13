package com.av19.netanalyzer.ui.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.utils.LocaleManager;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
    }

    private void applyTheme() {
        SharedPreferences prefs = getSharedPreferences("MiAppPrefs", MODE_PRIVATE);
        String themeMode = prefs.getString("tema", "auto");

        int themeResId;
        if ("light".equals(themeMode)) {
            themeResId = R.style.Theme_NetAnalyzer_Light;
        } else if ("dark".equals(themeMode)) {
            themeResId = R.style.Theme_NetAnalyzer_Dark;
        } else if ("ocean".equals(themeMode)) {
            themeResId = R.style.Theme_NetAnalyzer_Ocean;
        } else { // auto
            int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                themeResId = R.style.Theme_NetAnalyzer_Dark;
            } else {
                themeResId = R.style.Theme_NetAnalyzer_Light;
            }
        }
        setTheme(themeResId);
    }
}