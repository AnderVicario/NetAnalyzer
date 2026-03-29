package com.av19.netanalyzer.ui.settings;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.av19.netanalyzer.R;

import java.util.List;

public class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.ViewHolder> {

    private List<SettingsItem> settings;
    private OnSettingClickListener listener;
    private int expandedPosition = -1;
    private Context context;
    private SharedPreferences prefs;

    public interface OnSettingClickListener {
        void onSettingClicked(SettingsItem item, int position);
        void onOptionSelected(SettingsItem item, int optionIndex);
        void onSwitchChanged(SettingsItem item, boolean isChecked, int position);
    }

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
    public void onBindViewHolder(ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        SettingsItem item = settings.get(position);

        holder.iconImageView.setImageResource(item.getIconRes());
        holder.titleTextView.setText(item.getTitle());

        // Configurar subtitle (se mantiene estático)
        if (item.getSubtitle() != null && !item.getSubtitle().isEmpty()) {
            holder.subtitleTextView.setText(item.getSubtitle());
            holder.subtitleTextView.setVisibility(View.VISIBLE);
        } else {
            holder.subtitleTextView.setVisibility(View.GONE);
        }

        // Inicialmente ocultar el panel de opciones
        holder.optionsPanel.setVisibility(View.INVISIBLE);
        ViewGroup.LayoutParams params = holder.optionsPanel.getLayoutParams();
        params.height = 0;
        holder.optionsPanel.setLayoutParams(params);

        // Configurar según tipo
        switch (item.getType()) {
            case SWITCH:
                holder.settingSwitch.setVisibility(View.VISIBLE);
                holder.accessoryImageView.setVisibility(View.GONE);
                holder.optionsPanel.setVisibility(View.GONE);
                holder.buttonPanel.setBackgroundResource(R.drawable.round_button_36);

                // Obtener valor actual desde SharedPreferences si existe una clave
                boolean switchValue = getSwitchValueFromPreferences(item);
                holder.settingSwitch.setChecked(switchValue);

                // Actualizar el modelo
                item.setSwitchValue(switchValue);

                holder.settingSwitch.setOnCheckedChangeListener(null); // Limpiar listener previo
                holder.settingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    item.setSwitchValue(isChecked);

                    // Guardar en SharedPreferences si existe una clave
                    saveSwitchValueToPreferences(item, isChecked);

                    // Notificar al listener
                    if (listener != null) {
                        listener.onSwitchChanged(item, isChecked, position);
                    }
                });

                holder.buttonPanel.setOnClickListener(v -> {
                    // Alternar el switch al hacer clic en el panel
                    boolean newValue = !holder.settingSwitch.isChecked();
                    holder.settingSwitch.setChecked(newValue);

                    if (listener != null) {
                        listener.onSettingClicked(item, position);
                    }
                });
                break;

            case NAVIGATION:
                holder.settingSwitch.setVisibility(View.GONE);
                holder.accessoryImageView.setVisibility(View.VISIBLE);
                holder.optionsPanel.setVisibility(View.GONE);
                holder.buttonPanel.setBackgroundResource(R.drawable.round_button_selector_36);

                holder.buttonPanel.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onSettingClicked(item, position);
                    }
                });
                break;

            case INFO:
                holder.settingSwitch.setVisibility(View.GONE);
                holder.accessoryImageView.setVisibility(View.GONE);
                holder.buttonPanel.setBackgroundResource(R.drawable.round_button_selector_36);

                // Configurar opciones si las tiene
                if (item.getOptions() != null && item.getOptions().length >= 3) {
                    setupOptions(holder, item, position);

                    // Pre-medir la altura del panel de opciones
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

                        if (listener != null) {
                            listener.onSettingClicked(item, position);
                        }
                    });
                } else {
                    // Para "Acerca de" que no tiene opciones
                    holder.optionsPanel.setVisibility(View.GONE);
                    holder.buttonPanel.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onSettingClicked(item, position);
                        }
                    });
                }
                break;
        }
    }

    /**
     * Obtiene el valor del switch desde SharedPreferences
     */
    private boolean getSwitchValueFromPreferences(SettingsItem item) {
        if (item.getSettingKey() != null && !item.getSettingKey().isEmpty()) {
            return prefs.getBoolean(item.getSettingKey(),
                    item.getSwitchValue() != null ? item.getSwitchValue() : false);
        }
        return item.getSwitchValue() != null ? item.getSwitchValue() : false;
    }

    /**
     * Guarda el valor del switch en SharedPreferences
     */
    private void saveSwitchValueToPreferences(SettingsItem item, boolean value) {
        if (item.getSettingKey() != null && !item.getSettingKey().isEmpty()) {
            prefs.edit().putBoolean(item.getSettingKey(), value).apply();
        }
    }

    private void setupOptions(ViewHolder holder, SettingsItem item, int position) {
        // Configurar textos de opciones
        holder.option1.setText(item.getOptions()[0]);
        holder.option2.setText(item.getOptions()[1]);
        holder.option3.setText(item.getOptions()[2]);

        // Mostrar check en la opción seleccionada
        updateOptionSelection(holder, item.getSelectedOptionIndex());

        // Configurar listeners para las opciones
        holder.option1Layout.setOnClickListener(v -> handleOptionSelection(holder, item, position, 0));
        holder.option2Layout.setOnClickListener(v -> handleOptionSelection(holder, item, position, 1));
        holder.option3Layout.setOnClickListener(v -> handleOptionSelection(holder, item, position, 2));
    }

    private void handleOptionSelection(ViewHolder holder, SettingsItem item, int position, int optionIndex) {
        // OBTENER el valor actual ANTES de guardar el nuevo
        String currentValue = prefs.getString(item.getSettingKey(), "es");
        String newValue = item.getOptionValues()[optionIndex];

        // Actualizar selección visual
        updateOptionSelection(holder, optionIndex);

        // Actualizar el modelo
        item.setSelectedOptionIndex(optionIndex);

        // Actualizar subtítulo con la opción seleccionada
        if (item.getOptions() != null && optionIndex < item.getOptions().length) {
            item.setSubtitle(item.getOptions()[optionIndex]);
            holder.subtitleTextView.setText(item.getOptions()[optionIndex]);
            holder.subtitleTextView.setVisibility(View.VISIBLE);
        }

        // Solo guardar si es diferente al valor actual
        if (!newValue.equals(currentValue)) {
            prefs.edit().putString(item.getSettingKey(), newValue).apply();

            // Aplicar cambios según el tipo de setting
            applySettingChange(item.getTitle(), newValue);
        }

        // Contraer el panel después de seleccionar
        collapse(holder.optionsPanel);
        expandedPosition = -1;

        // Notificar al listener SOLO si hubo cambio
        if (!newValue.equals(currentValue) && listener != null) {
            listener.onOptionSelected(item, optionIndex);
        }
    }

    private void updateOptionSelection(ViewHolder holder, int selectedIndex) {
        // Ocultar todos los checks primero
        holder.option1Check.setVisibility(View.INVISIBLE);
        holder.option2Check.setVisibility(View.INVISIBLE);
        holder.option3Check.setVisibility(View.INVISIBLE);

        // Mostrar check en la opción seleccionada
        switch (selectedIndex) {
            case 0:
                holder.option1Check.setVisibility(View.VISIBLE);
                break;
            case 1:
                holder.option2Check.setVisibility(View.VISIBLE);
                break;
            case 2:
                holder.option3Check.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void applySettingChange(String settingTitle, String value) {
        // Aquí puedes aplicar cambios inmediatos como reiniciar la actividad si es necesario
    }

    // Los métodos preMeasureOptionsPanel, expand, collapse y getItemCount se mantienen igual...
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
        return settings != null ? settings.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView iconImageView;
        TextView titleTextView;
        TextView subtitleTextView;
        ImageView accessoryImageView;
        SwitchCompat settingSwitch;
        LinearLayout buttonPanel;
        LinearLayout optionsPanel;

        // Nuevos elementos para las opciones
        LinearLayout option1Layout, option2Layout, option3Layout;
        TextView option1, option2, option3;
        ImageView option1Check, option2Check, option3Check;

        public ViewHolder(View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.iconImageView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            subtitleTextView = itemView.findViewById(R.id.subtitleTextView);
            accessoryImageView = itemView.findViewById(R.id.accessoryImageView);
            settingSwitch = itemView.findViewById(R.id.settingSwitch);
            buttonPanel = itemView.findViewById(R.id.buttonPanel);
            optionsPanel = itemView.findViewById(R.id.options_panel);

            // Opción 1
            option1Layout = itemView.findViewById(R.id.option1_layout);
            option1 = itemView.findViewById(R.id.option1);
            option1Check = itemView.findViewById(R.id.option1_check);

            // Opción 2
            option2Layout = itemView.findViewById(R.id.option2_layout);
            option2 = itemView.findViewById(R.id.option2);
            option2Check = itemView.findViewById(R.id.option2_check);

            // Opción 3
            option3Layout = itemView.findViewById(R.id.option3_layout);
            option3 = itemView.findViewById(R.id.option3);
            option3Check = itemView.findViewById(R.id.option3_check);
        }
    }
}