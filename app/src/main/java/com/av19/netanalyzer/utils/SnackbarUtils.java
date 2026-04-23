package com.av19.netanalyzer.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.av19.netanalyzer.R;
import com.google.android.material.snackbar.Snackbar;

public class SnackbarUtils {

    private SnackbarUtils() {}

    public static void showSuccess(@NonNull View view, @NonNull Context context, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
        configBaseSnackbar(snackbar, context, R.color.primary, null);
        snackbar.show();
    }

    public static void showWarning(@NonNull View view, @NonNull Context context, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        configBaseSnackbar(snackbar, context, R.color.warning, R.drawable.ic_warning);
        snackbar.show();
    }

    public static void showError(@NonNull View view, @NonNull Context context, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        configBaseSnackbar(snackbar, context, R.color.error, R.drawable.ic_error);
        snackbar.getView().setBackgroundTintList(
                ColorStateList.valueOf(Color.argb(200, 255, 0, 0))
        );
        snackbar.show();
    }

    private static void configBaseSnackbar(Snackbar snackbar, Context context, int backgroundColorRes, Integer iconRes) {
        View snackbarView = snackbar.getView();

        snackbarView.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(context, backgroundColorRes))
        );

        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (iconRes != null) {
            Drawable icon = ContextCompat.getDrawable(context, iconRes);
            if (icon != null) {
                icon = DrawableCompat.wrap(icon).mutate();
                int iconColor = ContextCompat.getColor(context, R.color.on_error);
                DrawableCompat.setTint(icon, iconColor);
                icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                textView.setCompoundDrawables(icon, null, null, null);
                textView.setCompoundDrawablePadding(context.getResources().getDimensionPixelOffset(R.dimen.padding_icon));
            }
            textView.setTextColor(ContextCompat.getColor(context, R.color.on_error));
        } else {
            textView.setTextColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.on_surface)));
        }
    }

}