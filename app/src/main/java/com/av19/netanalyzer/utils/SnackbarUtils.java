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
        // Usar android.R.attr.colorPrimary (estándar de Android)
        configBaseSnackbar(snackbar, context, android.R.attr.colorPrimary, null);
        snackbar.show();
    }

    public static void showWarning(@NonNull View view, @NonNull Context context, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        // colorWarning es tuyo (sí está en attrs.xml)
        configBaseSnackbar(snackbar, context, R.attr.colorWarning, R.drawable.ic_warning);
        snackbar.show();
    }

    public static void showError(@NonNull View view, @NonNull Context context, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        // Usar android.R.attr.colorError (estándar desde API 23)
        configBaseSnackbar(snackbar, context, android.R.attr.colorError, R.drawable.ic_error);
        snackbar.show();
    }

    private static void configBaseSnackbar(Snackbar snackbar, Context context, int backgroundColorAttr, Integer iconRes) {
        View snackbarView = snackbar.getView();
        Context themedContext = snackbarView.getContext();

        // Obtener colores del tema actual
        int backgroundColor = getColorFromAttr(themedContext, backgroundColorAttr);

        // Para textColor y iconTintColor usar atributos de Material Components
        int textColor = getColorFromAttr(themedContext, com.google.android.material.R.attr.colorOnPrimary);
        int iconTintColor = getColorFromAttr(themedContext, com.google.android.material.R.attr.colorOnPrimary);

        snackbarView.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));

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
        }
        textView.setTextColor(textColor);
    }

    private static int getColorFromAttr(Context context, int attrRes) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }
}