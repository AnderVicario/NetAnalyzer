package com.av19.netanalyzer.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.av19.netanalyzer.R;
import com.google.android.material.snackbar.Snackbar;

public class SnackbarUtils {

    private SnackbarUtils() {
    }

    public static void showSuccess(@NonNull View view, @NonNull Context context, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
        configBaseSnackbar(snackbar, context, R.attr.colorPrimary, null);
        snackbar.show();
    }

    public static void showWarning(@NonNull View view, @NonNull Context context, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        configBaseSnackbar(snackbar, context, R.attr.colorWarning, R.drawable.ic_warning);
        snackbar.show();
    }

    public static void showError(@NonNull View view, @NonNull Context context, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        configBaseSnackbar(snackbar, context, R.attr.colorError, R.drawable.ic_error);
        snackbar.show();
    }

    private static void configBaseSnackbar(Snackbar snackbar, Context context, int backgroundColorAttr, Integer iconRes) {
        View snackbarView = snackbar.getView();
        Context themedContext = snackbarView.getContext(); // ya tiene el tema aplicado

        // Obtener colores del tema actual
        int backgroundColor = getColorFromAttr(themedContext, backgroundColorAttr);
        int textColor = getColorFromAttr(themedContext, R.attr.colorOnSurface);
        int iconTintColor = getColorFromAttr(themedContext, R.attr.colorOnPrimary); // para iconos de warning/error

        // Aplicar fondo con un pequeño alpha opcional (para error se puede mantener el alpha o no)
        if (backgroundColorAttr == R.attr.colorError) {
            // Opcional: darle un toque más oscuro con alpha, pero es mejor usar el color puro
            snackbarView.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        } else {
            snackbarView.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        }

        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (iconRes != null) {
            Drawable icon = ContextCompat.getDrawable(themedContext, iconRes);
            if (icon != null) {
                icon = DrawableCompat.wrap(icon).mutate();
                DrawableCompat.setTint(icon, iconTintColor);
                icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                textView.setCompoundDrawables(icon, null, null, null);
                textView.setCompoundDrawablePadding(themedContext.getResources().getDimensionPixelOffset(R.dimen.padding_icon));
            }
            textView.setTextColor(textColor);
        } else {
            textView.setTextColor(textColor);
        }

        // Asegurar que el texto sea legible en fondos oscuros
        textView.setTextColor(textColor);
    }

    private static int getColorFromAttr(Context context, int attrRes) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }
}