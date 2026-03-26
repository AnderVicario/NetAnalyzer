package com.av19.netanalyzer.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.av19.netanalyzer.R;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class AdvancedSettingsBottomSheet extends com.google.android.material.bottomsheet.BottomSheetDialogFragment {

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.layout_advanced_settings, container, false);
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