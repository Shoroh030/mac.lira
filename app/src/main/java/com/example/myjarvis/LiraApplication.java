package com.example.myjarvis;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

public final class LiraApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        boolean darkTheme = getSharedPreferences("LiraPrefs", MODE_PRIVATE)
                .getBoolean("theme_dark", true);
        AppCompatDelegate.setDefaultNightMode(darkTheme
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
