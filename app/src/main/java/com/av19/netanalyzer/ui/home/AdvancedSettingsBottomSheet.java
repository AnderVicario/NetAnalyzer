package com.av19.netanalyzer.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.HashSet;
import java.util.Set;

public class AdvancedSettingsBottomSheet extends com.google.android.material.bottomsheet.BottomSheetDialogFragment {

    private SharedPreferences prefs;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.AppBottomSheetDialogTheme);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            // El layout se dibuje fuera de los límites (debajo de las barras)
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

            // Sumar la altura de la navigation bar al padding inferior que ya tenías (24dp)
            // Convertir 24dp a pixels para que sea exacto
            int basePaddingBottom = (int) (24 * getResources().getDisplayMetrics().density);
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), basePaddingBottom + systemBars.bottom);
            return windowInsets;
        });

        prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);

        MaterialButtonToggleGroup groupMethod = v.findViewById(R.id.toggle_group_method);
        MaterialButtonToggleGroup groupPorts = v.findViewById(R.id.toggle_group_ports);

        // Lógica de exclusividad para AUTO
        MaterialButton btnAuto = v.findViewById(R.id.btn_method_auto);
        MaterialButton btnArp = v.findViewById(R.id.btn_method_arp);
        MaterialButton btnIcmp = v.findViewById(R.id.btn_method_icmp);
        MaterialButton btnTcp = v.findViewById(R.id.btn_method_tcp);

        btnAuto.setOnClickListener(view -> {

            Log.d("AdvancedSettingsBottomSheet", "AUTO pulsado");

            groupMethod.clearChecked();
            groupMethod.check(R.id.btn_method_auto);

            btnArp.setBackgroundColor(getResources().getColor(R.color.transparent));
            btnIcmp.setBackgroundColor(getResources().getColor(R.color.transparent));
            btnTcp.setBackgroundColor(getResources().getColor(R.color.transparent));
        });


        View.OnClickListener manualListener = view -> {

            int id = view.getId();
            Log.d("AdvancedSettingsBottomSheet", "Metodo manual pulsado");

            // quitar AUTO si estaba activo
            groupMethod.uncheck(R.id.btn_method_auto);

            // alternar el botón pulsado
            MaterialButton btn = (MaterialButton) view;

            if (btn.isChecked()) {
                btn.setBackgroundColor(getResources().getColor(R.color.button));
            } else {
                btn.setBackgroundColor(getResources().getColor(R.color.transparent));
            }
        };

        btnArp.setOnClickListener(manualListener);
        btnIcmp.setOnClickListener(manualListener);
        btnTcp.setOnClickListener(manualListener);

        // Cargar estado actual
        setupInitialSelection(groupMethod, groupPorts);

        // Botón Guardar
        v.findViewById(R.id.btn_save_config).setOnClickListener(view -> {
            // Guardar métodos seleccionados
            Set<String> selectedMethods = new HashSet<>();
            for (int id : groupMethod.getCheckedButtonIds()) {
                selectedMethods.add(getMethodNameFromId(id));
            }
            // Convertir el conjunto en una cadena separada por comas
            String methodsStr = String.join(",", selectedMethods);
            prefs.edit()
                    .putString("scan_method", methodsStr)
                    .putString("scan_level", getPortsFromId(groupPorts.getCheckedButtonId()))
                    .apply();

            dismiss();

            Log.d("AdvancedSettingsBottomSheet", "Configuración guardada: " + methodsStr);
        });

        return v;
    }

    private void setupInitialSelection(MaterialButtonToggleGroup groupMethod, MaterialButtonToggleGroup groupPorts) {
        // Cargar métodos guardados (soporta formato antiguo y nuevo)
        Set<String> savedMethods = getStoredMethods();

        // Marcar los botones correspondientes
        for (String method : savedMethods) {
            int id = getMethodIdFromName(method);
            if (id != -1) {
                groupMethod.check(id);
            }
        }

        // Si no hay ningúno marcado (por ejemplo, si se guardó vacío), marcar AUTO por defecto
        if (groupMethod.getCheckedButtonIds().isEmpty()) {
            groupMethod.check(R.id.btn_method_auto);
        }

        // Cargar nivel de puertos (sigue siendo selección única)
        String currentPorts = prefs.getString("scan_level", "100");
        if (currentPorts.equals("500")) groupPorts.check(R.id.btn_ports_500);
        else if (currentPorts.equals("1000")) groupPorts.check(R.id.btn_ports_1000);
        else groupPorts.check(R.id.btn_ports_100);
    }

    private Set<String> getStoredMethods() {
        String methodsStr = prefs.getString("scan_method", "AUTO");
        Set<String> methods = new HashSet<>();
        for (String part : methodsStr.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                methods.add(trimmed);
            }
        }
        return methods;
    }

    private int getMethodIdFromName(String method) {
        switch (method) {
            case "ARP":
                return R.id.btn_method_arp;
            case "TCP":
                return R.id.btn_method_tcp;
            case "ICMP":
                return R.id.btn_method_icmp;
            case "AUTO":
                return R.id.btn_method_auto;
            default:
                return -1;
        }
    }

    private String getMethodNameFromId(int id) {
        if (id == R.id.btn_method_arp) return "ARP";
        if (id == R.id.btn_method_tcp) return "TCP";
        if (id == R.id.btn_method_icmp) return "ICMP";
        if (id == R.id.btn_method_auto) return "AUTO";
        return "AUTO"; // fallback
    }

    private String getPortsFromId(int id) {
        if (id == R.id.btn_ports_500) return "500";
        if (id == R.id.btn_ports_1000) return "1000";
        return "100";
    }
}