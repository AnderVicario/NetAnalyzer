package com.av19.netanalyzer.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.av19.netanalyzer.R;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class AdvancedSettingsBottomSheet extends com.google.android.material.bottomsheet.BottomSheetDialogFragment {

    private SharedPreferences prefs;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Aplicamos el estilo que creamos
        setStyle(STYLE_NORMAL, R.style.AppBottomSheetDialogTheme);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            // 1. Forzamos a que el layout se dibuje fuera de los límites (debajo de las barras)
            window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.layout_advanced_settings, container, false);

        ViewCompat.setOnApplyWindowInsetsListener(v, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());

            // Sumamos la altura de la navigation bar al padding inferior que ya tenías (24dp)
            // Convertimos 24dp a pixels para que sea exacto
            int basePaddingBottom = (int) (24 * getResources().getDisplayMetrics().density);
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), basePaddingBottom + systemBars.bottom);

            return windowInsets;
        });

        prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);

        MaterialButtonToggleGroup groupMethod = v.findViewById(R.id.toggle_group_method);
        MaterialButtonToggleGroup groupPorts = v.findViewById(R.id.toggle_group_ports);

        // 1. Cargar estado actual
        setupInitialSelection(groupMethod, groupPorts);

        // 2. Botón Guardar
        v.findViewById(R.id.btn_save_config).setOnClickListener(view -> {
            String method = getMethodFromId(groupMethod.getCheckedButtonId());
            String ports = getPortsFromId(groupPorts.getCheckedButtonId());

            prefs.edit()
                    .putString("scan_method", method)
                    .putString("scan_level", ports)
                    .apply();

            dismiss();
        });

        return v;
    }

    private void setupInitialSelection(MaterialButtonToggleGroup gM, MaterialButtonToggleGroup gP) {
        String currentMethod = prefs.getString("scan_method", "AUTO");
        String currentPorts = prefs.getString("scan_level", "100");

        if (currentMethod.equals("ARP")) gM.check(R.id.btn_method_arp);
        else if (currentMethod.equals("TCP")) gM.check(R.id.btn_method_tcp);
        else if (currentMethod.equals("ICMP")) gM.check(R.id.btn_method_icmp);
        else gM.check(R.id.btn_method_auto);

        if (currentPorts.equals("500")) gP.check(R.id.btn_ports_500);
        else if (currentPorts.equals("1000")) gP.check(R.id.btn_ports_1000);
        else gP.check(R.id.btn_ports_100);
    }

    private String getMethodFromId(int id) {
        if (id == R.id.btn_method_arp) return "ARP";
        if (id == R.id.btn_method_tcp) return "TCP";
        if (id == R.id.btn_method_icmp) return "ICMP";
        return "AUTO";
    }

    private String getPortsFromId(int id) {
        if (id == R.id.btn_ports_500) return "500";
        if (id == R.id.btn_ports_1000) return "1000";
        return "100";
    }
}