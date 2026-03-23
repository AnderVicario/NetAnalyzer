package com.av19.netanalyzer.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

public class LocaleManager {
    private static final String PREF_NAME = "MiAppPrefs";
    private static final String KEY_LANGUAGE = "idioma";

    public static Context setLocale(Context context) {
        return updateResources(context, getLanguage(context));
    }

    public static void setNewLocale(Context context, String languageCode) {
        persistLanguage(context, languageCode);
        updateResources(context, languageCode);
    }

    private static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, "es");
    }

    private static void persistLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    private static Context updateResources(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());

        config.setLocale(locale);
        context = context.createConfigurationContext(config);

        return context;
    }

    public static Locale getLocale(Resources res) {
        Configuration config = res.getConfiguration();
        return config.getLocales().get(0);
    }

    public static String getPrepRegionName(Context context, String regionName) {
        if (LocaleManager.getLanguage(context).equals("es")) {
            return "en " + regionName;
        }
        else if (LocaleManager.getLanguage(context).equals("eu")) {
            char lastChar = regionName.charAt(regionName.length() - 1);
            if (regionName.endsWith("r")) {
                return regionName + "ren";
            }
            else if ("bcdfghjklmnpqstvwxyzBCDFGHJKLMNPQSTVWXYZ".indexOf(lastChar) != -1) {
                return regionName + "en";
            }
            else {
                return regionName + "n";
            }
        }
        else if (LocaleManager.getLanguage(context).equals("en")) {
            return "in " + regionName;
        }
        return "";
    }
}