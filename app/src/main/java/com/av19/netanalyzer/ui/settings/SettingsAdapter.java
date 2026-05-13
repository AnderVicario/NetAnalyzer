package com.av19.netanalyzer.ui.settings;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.av19.netanalyzer.R;

import java.util.ArrayList;
import java.util.List;

public class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.ViewHolder> {

    private final List<SettingsItem> settings;
    private final OnSettingClickListener listener;
    private final Context context;
    private final SharedPreferences prefs;
    private int expandedPosition = -1;

    public SettingsAdapter(List<SettingsItem> settings, OnSettingClickListener listener, Context context) {
        this.settings = settings;
        this.listener = listener;
        this.context = context;
        this.prefs = context.getSharedPreferences("MiAppPrefs", Context.MODE_PRIVATE);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.settings_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        SettingsItem item = settings.get(position);

        holder.iconImageView.setImageResource(item.getIconRes());
        holder.titleTextView.setText(item.getTitle());

        // Subtitle
        if (item.getSubtitle() != null && !item.getSubtitle().isEmpty()) {
            holder.subtitleTextView.setText(item.getSubtitle());
            holder.subtitleTextView.setVisibility(View.VISIBLE);
        } else {
            holder.subtitleTextView.setVisibility(View.GONE);
        }

        // Resetear panel de opciones (limpiar vistas previas)
        holder.optionsPanel.removeAllViews();
        holder.optionsPanel.setVisibility(View.INVISIBLE);
        ViewGroup.LayoutParams params = holder.optionsPanel.getLayoutParams();
        params.height = 0;
        holder.optionsPanel.setLayoutParams(params);

        // Configurar según tipo
        switch (item.getType()) {
            case SWITCH:
                configSwitch(holder, item, position);
                break;

            case NAVIGATION:
                configNavigation(holder, item, position);
                break;

            case KEY:
                configKey(holder, item, position);
                break;

            case INFO:
                configInfo(holder, item, position);
                break;
        }
    }

    // ==================== CONFIGURACIÓN POR TIPO ====================

    private void configSwitch(ViewHolder holder, SettingsItem item, int position) {
        holder.settingSwitch.setVisibility(View.VISIBLE);
        holder.accessoryImageView.setVisibility(View.GONE);
        holder.buttonPanel.setBackgroundResource(R.drawable.round_button_36);

        boolean switchValue = getSwitchValueFromPreferences(item);
        holder.settingSwitch.setChecked(switchValue);
        item.setSwitchValue(switchValue);

        holder.settingSwitch.setOnCheckedChangeListener(null);
        holder.settingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setSwitchValue(isChecked);
            saveSwitchValueToPreferences(item, isChecked);
            if (listener != null) {
                listener.onSwitchChanged(item, isChecked, position);
            }
        });

        holder.buttonPanel.setOnClickListener(v -> {
            boolean newValue = !holder.settingSwitch.isChecked();
            holder.settingSwitch.setChecked(newValue);
            if (listener != null) listener.onSettingClicked(item, position);
        });
    }

    private void configNavigation(ViewHolder holder, SettingsItem item, int position) {
        holder.settingSwitch.setVisibility(View.GONE);
        holder.accessoryImageView.setVisibility(View.VISIBLE);
        holder.buttonPanel.setBackgroundResource(R.drawable.round_button_selector_36);
        holder.buttonPanel.setOnClickListener(v -> {
            if (listener != null) listener.onSettingClicked(item, position);
        });
    }

    private void configKey(ViewHolder holder, SettingsItem item, int position) {
        holder.settingSwitch.setVisibility(View.GONE);
        holder.accessoryImageView.setVisibility(View.GONE);
        holder.editText.setVisibility(View.VISIBLE);

        // Limpiar opciones previas (por si acaso)
        holder.optionsPanel.removeAllViews();
        holder.editText.setText(prefs.getString(item.getSettingKey(), ""));
        holder.editText.setSelection(holder.editText.getText().length());

        preMeasureOptionsPanel(holder.optionsPanel);

        holder.buttonPanel.setBackgroundResource(R.drawable.round_button_selector_36);
        holder.buttonPanel.setOnClickListener(v -> {
            boolean isExpanded = holder.optionsPanel.getVisibility() == View.VISIBLE;

            if (isExpanded) {
                // Colapsar y guardar el nuevo valor
                String newKey = holder.editText.getText().toString().trim();
                String oldKey = prefs.getString(item.getSettingKey(), "");
                if (!newKey.equals(oldKey)) {
                    boolean isEmpty = newKey.isEmpty();
                    String newSubtitle = isEmpty
                            ? context.getString(R.string.settings_key_empty)
                            : context.getString(R.string.settings_key_not_empty);
                    item.setSubtitle(newSubtitle);
                    holder.subtitleTextView.setText(newSubtitle);
                    holder.subtitleTextView.setVisibility(View.VISIBLE);
                    if (listener != null) listener.onKeyChanged(item, newKey);
                }
                collapse(holder.optionsPanel);
                expandedPosition = -1;
            } else {
                // Expandir
                if (expandedPosition != -1 && expandedPosition != position) {
                    notifyItemChanged(expandedPosition);
                }
                expand(holder.optionsPanel);
                expandedPosition = position;
            }
            if (listener != null) listener.onSettingClicked(item, position);
        });
    }

    private void configInfo(ViewHolder holder, SettingsItem item, int position) {
        holder.settingSwitch.setVisibility(View.GONE);
        holder.accessoryImageView.setVisibility(View.GONE);
        holder.editText.setVisibility(View.GONE);
        holder.buttonPanel.setBackgroundResource(R.drawable.round_button_selector_36);

        // Generar opciones dinámicamente
        if (item.getOptions() != null && item.getOptions().length > 0) {
            populateOptionsPanel(holder, item, position);
            preMeasureOptionsPanel(holder.optionsPanel);

            holder.buttonPanel.setOnClickListener(v -> {
                boolean isExpanded = holder.optionsPanel.getVisibility() == View.VISIBLE;
                if (isExpanded) {
                    collapse(holder.optionsPanel);
                    expandedPosition = -1;
                } else {
                    if (expandedPosition != -1 && expandedPosition != position) {
                        notifyItemChanged(expandedPosition);
                    }
                    expand(holder.optionsPanel);
                    expandedPosition = position;
                }
                if (listener != null) listener.onSettingClicked(item, position);
            });
        } else {
            // Sin opciones (ej. About)
            holder.optionsPanel.setVisibility(View.GONE);
            holder.buttonPanel.setOnClickListener(v -> {
                if (listener != null) listener.onSettingClicked(item, position);
            });
        }
    }

    // ==================== MÉTODOS DINÁMICOS PARA OPCIONES ====================

    private void populateOptionsPanel(ViewHolder holder, SettingsItem item, int position) {
        LinearLayout panel = holder.optionsPanel;
        panel.removeAllViews();

        String[] options = item.getOptions();
        String[] optionValues = item.getOptionValues();
        int selectedIndex = item.getSelectedOptionIndex();

        // Lista para guardar las vistas de check (para poder actualizarlas después)
        holder.optionCheckViews.clear();

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            View optionView = LayoutInflater.from(context).inflate(R.layout.settings_option_item, panel, false);
            TextView textView = optionView.findViewById(R.id.option_text);
            ImageView checkView = optionView.findViewById(R.id.option_check);
            textView.setText(options[i]);
            checkView.setVisibility(index == selectedIndex ? View.VISIBLE : View.INVISIBLE);
            holder.optionCheckViews.add(checkView);

            optionView.setOnClickListener(v -> handleOptionSelection(holder, item, position, index));
            panel.addView(optionView);

            // Añadir divisor (excepto después del último)
            if (i < options.length - 1) {
                View divider = new View(context);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                divider.setLayoutParams(dividerParams);
                divider.setBackgroundColor(getColorFromAttr(R.attr.colorBackground10));
                panel.addView(divider);
            }
        }
    }

    private int getColorFromAttr(int attrRes) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        context.getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }

    private void handleOptionSelection(ViewHolder holder, SettingsItem item, int position, int optionIndex) {
        String currentValue = prefs.getString(item.getSettingKey(), "");
        String newValue = item.getOptionValues()[optionIndex];

        // Actualizar checks visualmente
        for (int i = 0; i < holder.optionCheckViews.size(); i++) {
            holder.optionCheckViews.get(i).setVisibility(i == optionIndex ? View.VISIBLE : View.INVISIBLE);
        }

        // Actualizar modelo
        item.setSelectedOptionIndex(optionIndex);
        if (item.getOptions() != null && optionIndex < item.getOptions().length) {
            item.setSubtitle(item.getOptions()[optionIndex]);
            holder.subtitleTextView.setText(item.getOptions()[optionIndex]);
            holder.subtitleTextView.setVisibility(View.VISIBLE);
        }

        // Guardar solo si cambió
        if (!newValue.equals(currentValue)) {
            prefs.edit().putString(item.getSettingKey(), newValue).apply();
            applySettingChange(item.getTitle(), newValue);
        }

        // Colapsar panel
        collapse(holder.optionsPanel);
        expandedPosition = -1;

        // Notificar cambio
        if (!newValue.equals(currentValue) && listener != null) {
            listener.onOptionSelected(item, optionIndex);
        }
    }

    // ==================== MÉTODOS AUXILIARES ====================

    private boolean getSwitchValueFromPreferences(SettingsItem item) {
        if (item.getSettingKey() != null && !item.getSettingKey().isEmpty()) {
            return prefs.getBoolean(item.getSettingKey(),
                    item.getSwitchValue() != null ? item.getSwitchValue() : false);
        }
        return item.getSwitchValue() != null ? item.getSwitchValue() : false;
    }

    private void saveSwitchValueToPreferences(SettingsItem item, boolean value) {
        if (item.getSettingKey() != null && !item.getSettingKey().isEmpty()) {
            prefs.edit().putBoolean(item.getSettingKey(), value).apply();
        }
    }

    private void applySettingChange(String settingTitle, String value) {
        // Aquí puedes aplicar cambios inmediatos si es necesario
    }

    // ==================== MÉTODOS DE ANIMACIÓN (expandir/colapsar) ====================

    private void preMeasureOptionsPanel(final View optionsPanel) {
        if (optionsPanel.getTag() != null && optionsPanel.getTag().equals("measured")) {
            return;
        }
        optionsPanel.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                optionsPanel.getViewTreeObserver().removeOnPreDrawListener(this);
                optionsPanel.measure(
                        View.MeasureSpec.makeMeasureSpec(optionsPanel.getWidth(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
                optionsPanel.setTag("measured");
                return true;
            }
        });
    }

    private void expand(final View view) {
        if (view.getVisibility() == View.VISIBLE) return;
        view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                view.measure(
                        View.MeasureSpec.makeMeasureSpec(view.getWidth(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
                final int targetHeight = view.getMeasuredHeight();
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                layoutParams.height = 0;
                view.setLayoutParams(layoutParams);
                view.setVisibility(View.VISIBLE);

                ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
                animator.addUpdateListener(animation -> {
                    int value = (int) animation.getAnimatedValue();
                    layoutParams.height = value;
                    view.setLayoutParams(layoutParams);
                });
                animator.setDuration(300);
                animator.start();
                return true;
            }
        });
    }

    private void collapse(final View view) {
        final int initialHeight = view.getMeasuredHeight();
        if (initialHeight == 0) {
            view.setVisibility(View.INVISIBLE);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofInt(initialHeight, 0);
        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            layoutParams.height = value;
            view.setLayoutParams(layoutParams);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                view.setVisibility(View.INVISIBLE);
            }
        });
        animator.setDuration(300);
        animator.start();
    }

    @Override
    public int getItemCount() {
        return settings.size();
    }

    // ==================== INTERFACE ====================

    public interface OnSettingClickListener {
        void onSettingClicked(SettingsItem item, int position);

        void onOptionSelected(SettingsItem item, int optionIndex);

        void onSwitchChanged(SettingsItem item, boolean isChecked, int position);

        void onKeyChanged(SettingsItem item, String newValue);
    }

    // ==================== VIEWHOLDER ====================

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView iconImageView;
        TextView titleTextView;
        TextView subtitleTextView;
        ImageView accessoryImageView;
        SwitchCompat settingSwitch;
        LinearLayout buttonPanel;
        LinearLayout optionsPanel;
        EditText editText;

        // Para opciones dinámicas: lista de ImageView de check (una por opción)
        List<ImageView> optionCheckViews = new ArrayList<>();

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.iconImageView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            subtitleTextView = itemView.findViewById(R.id.subtitleTextView);
            accessoryImageView = itemView.findViewById(R.id.accessoryImageView);
            settingSwitch = itemView.findViewById(R.id.settingSwitch);
            buttonPanel = itemView.findViewById(R.id.buttonPanel);
            optionsPanel = itemView.findViewById(R.id.options_panel);
            editText = itemView.findViewById(R.id.ic_edit);
        }
    }
}