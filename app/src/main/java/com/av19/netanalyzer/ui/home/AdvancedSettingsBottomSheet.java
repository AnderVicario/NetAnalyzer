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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
            int basePaddingBottom = (int) (24 * getResources().getDisplayMetrics().density);
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), basePaddingBottom + systemBars.bottom);
            return windowInsets;
        });

        prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);

        // Grupos de métodos (dos filas)
        MaterialButtonToggleGroup groupRow1 = v.findViewById(R.id.toggle_group_row1);
        MaterialButtonToggleGroup groupRow2 = v.findViewById(R.id.toggle_group_row2);
        MaterialButtonToggleGroup groupPorts = v.findViewById(R.id.toggle_group_ports);

        // Botones
        MaterialButton btnAuto = v.findViewById(R.id.btn_method_auto);
        MaterialButton btnArp = v.findViewById(R.id.btn_method_arp);
        MaterialButton btnTcp = v.findViewById(R.id.btn_method_tcp);
        MaterialButton btnIcmp = v.findViewById(R.id.btn_method_icmp);
        MaterialButton btnMdns = v.findViewById(R.id.btn_method_mdns);
        MaterialButton btnSsdp = v.findViewById(R.id.btn_method_ssdp);

        // Lista de todos los botones manuales (para resetear colores)
        List<MaterialButton> manualButtons = List.of(btnArp, btnTcp, btnIcmp, btnMdns, btnSsdp);

        // Lógica de AUTO: limpia todo, marca AUTO y resetea colores
        btnAuto.setOnClickListener(view -> {
            Log.d("AdvancedSettingsBottomSheet", "AUTO pulsado");

            // Limpiar ambos grupos
            groupRow1.clearChecked();
            groupRow2.clearChecked();
            // Marcar AUTO en el primer grupo
            groupRow1.check(R.id.btn_method_auto);

            // Resetear fondo de todos los botones manuales
            for (MaterialButton btn : manualButtons) {
                btn.setBackgroundColor(getResources().getColor(R.color.transparent));
            }
        });

        // Listener para botones manuales
        View.OnClickListener manualListener = view -> {
            Log.d("AdvancedSettingsBottomSheet", "Método manual pulsado");

            // Desmarcar AUTO si estaba marcado en cualquiera de los dos grupos
            groupRow1.uncheck(R.id.btn_method_auto);
            groupRow2.uncheck(R.id.btn_method_auto); // por si acaso

            MaterialButton btn = (MaterialButton) view;
            // Alternar color de fondo según el nuevo estado checked
            if (btn.isChecked()) {
                btn.setBackgroundColor(getResources().getColor(R.color.button));
            } else {
                btn.setBackgroundColor(getResources().getColor(R.color.transparent));
            }
        };

        // Asignar listener a todos los botones manuales
        btnArp.setOnClickListener(manualListener);
        btnTcp.setOnClickListener(manualListener);
        btnIcmp.setOnClickListener(manualListener);
        btnMdns.setOnClickListener(manualListener);
        btnSsdp.setOnClickListener(manualListener);

        // Cargar estado guardado
        setupInitialSelection(groupRow1, groupRow2, groupPorts, manualButtons);

        // Botón Guardar
        v.findViewById(R.id.btn_save_config).setOnClickListener(view -> {
            Set<String> selectedMethods = new HashSet<>();

            // Recoger selecciones de ambos grupos
            for (int id : groupRow1.getCheckedButtonIds()) {
                selectedMethods.add(getMethodNameFromId(id));
            }
            for (int id : groupRow2.getCheckedButtonIds()) {
                selectedMethods.add(getMethodNameFromId(id));
            }

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

    private void setupInitialSelection(MaterialButtonToggleGroup groupRow1,
                                       MaterialButtonToggleGroup groupRow2,
                                       MaterialButtonToggleGroup groupPorts,
                                       List<MaterialButton> manualButtons) {
        Set<String> savedMethods = getStoredMethods();

        // Limpiar ambos grupos antes de marcar
        groupRow1.clearChecked();
        groupRow2.clearChecked();

        // Marcar los botones según los métodos guardados
        for (String method : savedMethods) {
            int id = getMethodIdFromName(method);
            if (id == -1) continue;

            // Buscar en qué grupo está el botón y marcarlo
            if (id == R.id.btn_method_auto) {
                groupRow1.check(id);
            } else {
                // Intentar marcar en row1, si no está allí, en row2
                if (groupRow1.findViewById(id) != null) {
                    groupRow1.check(id);
                } else if (groupRow2.findViewById(id) != null) {
                    groupRow2.check(id);
                }
            }
        }

        // Si no hay nada marcado (incluyendo AUTO), marcar AUTO por defecto
        if (groupRow1.getCheckedButtonIds().isEmpty() && groupRow2.getCheckedButtonIds().isEmpty()) {
            groupRow1.check(R.id.btn_method_auto);
        }

        // Aplicar colores a los botones manuales según su estado checked
        for (MaterialButton btn : manualButtons) {
            if (btn.isChecked()) {
                btn.setBackgroundColor(getResources().getColor(R.color.button));
            } else {
                btn.setBackgroundColor(getResources().getColor(R.color.transparent));
            }
        }

        // Cargar nivel de puertos
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
            case "ARP": return R.id.btn_method_arp;
            case "TCP": return R.id.btn_method_tcp;
            case "ICMP": return R.id.btn_method_icmp;
            case "mDNS": return R.id.btn_method_mdns;
            case "SSDP": return R.id.btn_method_ssdp;
            case "AUTO": return R.id.btn_method_auto;
            default: return -1;
        }
    }

    private String getMethodNameFromId(int id) {
        if (id == R.id.btn_method_arp) return "ARP";
        if (id == R.id.btn_method_tcp) return "TCP";
        if (id == R.id.btn_method_icmp) return "ICMP";
        if (id == R.id.btn_method_mdns) return "mDNS";
        if (id == R.id.btn_method_ssdp) return "SSDP";
        if (id == R.id.btn_method_auto) return "AUTO";
        return "AUTO";
    }

    private String getPortsFromId(int id) {
        if (id == R.id.btn_ports_500) return "500";
        if (id == R.id.btn_ports_1000) return "1000";
        return "100";
    }
}