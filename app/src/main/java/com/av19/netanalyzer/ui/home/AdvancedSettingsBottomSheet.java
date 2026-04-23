package com.av19.netanalyzer.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.utils.SnackbarUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.widget.Toast;
import java.io.File;

public class AdvancedSettingsBottomSheet extends BottomSheetDialogFragment {

    private SharedPreferences prefs;
    private List<MaterialButton> methodButtons;
    private MaterialButton btnAuto;

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
        btnAuto = v.findViewById(R.id.btn_method_auto);
        MaterialButton btnArp = v.findViewById(R.id.btn_method_arp);
        MaterialButton btnTcp = v.findViewById(R.id.btn_method_tcp);
        MaterialButton btnIcmp = v.findViewById(R.id.btn_method_icmp);
        MaterialButton btnMdns = v.findViewById(R.id.btn_method_mdns);
        MaterialButton btnSsdp = v.findViewById(R.id.btn_method_ssdp);
        MaterialButton btnNetbios = v.findViewById(R.id.btn_method_netbios);

        methodButtons = Arrays.asList(btnArp, btnTcp, btnIcmp, btnMdns, btnSsdp, btnNetbios);

        // Tooltips para indicar long click (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            btnTcp.setTooltipText("Long press to configure");
            btnIcmp.setTooltipText("Long press to configure");
            btnSsdp.setTooltipText("Long press to configure");
        }

        // Listener para AUTO
        btnAuto.setOnClickListener(vw -> {
            if (btnAuto.isChecked()) {
                for (MaterialButton btn : methodButtons) {
                    btn.setChecked(false);
                    updateButtonStyle(btn, false);
                }
            } else {
                boolean anyChecked = false;
                for (MaterialButton btn : methodButtons) {
                    if (btn.isChecked()) {
                        anyChecked = true;
                        break;
                    }
                }
                if (!anyChecked) btnAuto.setChecked(true);
            }
        });

        // Listener para métodos manuales
        View.OnClickListener clickListener = vw -> {
            MaterialButton btn = (MaterialButton) vw;
            if (btn.isChecked()) {
                if (btn.getId() == R.id.btn_method_arp) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isRootAvailable()) {
                        SnackbarUtils.showWarning(requireView(), requireContext(),
                                "ARP discovery may not work on Android 10+ without root access");
                    }
                }
                if (btnAuto.isChecked()) btnAuto.setChecked(false);
            } else {
                boolean anyChecked = false;
                for (MaterialButton b : methodButtons) {
                    if (b.isChecked()) {
                        anyChecked = true;
                        break;
                    }
                }
                if (!anyChecked && !btnAuto.isChecked()) {
                    btnAuto.setChecked(true);
                }
            }
            updateButtonStyle(btn, btn.isChecked());
        };

        // Long click para abrir diálogo de configuración
        View.OnLongClickListener longClickListener = vw -> {
            MaterialButton btn = (MaterialButton) vw;
            String methodName = getMethodNameFromButton(btn);
            showConfigDialog(methodName);
            return true;
        };

        for (MaterialButton btn : methodButtons) {
            btn.setOnClickListener(clickListener);
        }
        btnTcp.setOnLongClickListener(longClickListener);
        btnIcmp.setOnLongClickListener(longClickListener);
        btnSsdp.setOnLongClickListener(longClickListener);
        // Añadir más si otros métodos tienen opciones

        // Cargar selección guardada
        loadSavedSelection();

        // Grupo de puertos
        MaterialButtonToggleGroup groupPorts = v.findViewById(R.id.toggle_group_ports);
        String currentPorts = prefs.getString("scan_level", "100");
        if (currentPorts.equals("500")) groupPorts.check(R.id.btn_ports_500);
        else if (currentPorts.equals("1000")) groupPorts.check(R.id.btn_ports_1000);
        else groupPorts.check(R.id.btn_ports_100);

        v.findViewById(R.id.btn_save_config).setOnClickListener(view -> {
            saveConfiguration(groupPorts);
            dismiss();
        });

        return v;
    }

    private void showConfigDialog(String methodName) {
        View dialogView = null;
        switch (methodName) {
            case "TCP":
                dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_tcp_config, null);
                break;
            case "ICMP":
                dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_icmp_config, null);
                break;
            case "SSDP":
                dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_ssdp_config, null);
                break;
            default:
                return;
        }

        final String finalMethodName = methodName;
        final View finalDialogView = dialogView;

        loadConfigValues(finalMethodName, finalDialogView);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(finalMethodName + " Configuration")
                .setView(finalDialogView)
                .setPositiveButton("Save", (d, which) -> saveConfigValues(finalMethodName, finalDialogView))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadConfigValues(String methodName, View view) {
        switch (methodName) {
            case "TCP":
                ((EditText) view.findViewById(R.id.tcp_port)).setText(prefs.getString("tcp_port", ""));
                ((EditText) view.findViewById(R.id.tcp_timeout)).setText(prefs.getString("tcp_timeout", "200"));
                break;
            case "ICMP":
                ((EditText) view.findViewById(R.id.icmp_count)).setText(prefs.getString("icmp_count", "1"));
                ((EditText) view.findViewById(R.id.icmp_timeout)).setText(prefs.getString("icmp_timeout", "700"));
                break;
            case "SSDP":
                ((EditText) view.findViewById(R.id.ssdp_timeout)).setText(prefs.getString("ssdp_timeout", "5000"));
                break;
        }
    }

    private void saveConfigValues(String methodName, View view) {
        SharedPreferences.Editor editor = prefs.edit();
        switch (methodName) {
            case "TCP":
                editor.putString("tcp_port", ((EditText) view.findViewById(R.id.tcp_port)).getText().toString());
                editor.putString("tcp_timeout", ((EditText) view.findViewById(R.id.tcp_timeout)).getText().toString());
                break;
            case "ICMP":
                editor.putString("icmp_count", ((EditText) view.findViewById(R.id.icmp_count)).getText().toString());
                editor.putString("icmp_timeout", ((EditText) view.findViewById(R.id.icmp_timeout)).getText().toString());
                break;
            case "SSDP":
                editor.putString("ssdp_timeout", ((EditText) view.findViewById(R.id.ssdp_timeout)).getText().toString());
                break;
        }
        editor.apply();
    }

    private void loadSavedSelection() {
        Set<String> savedMethods = getStoredMethodsSet();
        boolean autoSelected = savedMethods.contains("AUTO") || savedMethods.isEmpty();

        btnAuto.setChecked(autoSelected);
        for (MaterialButton btn : methodButtons) {
            String methodName = getMethodNameFromButton(btn);
            boolean checked = savedMethods.contains(methodName);
            btn.setChecked(checked);
            updateButtonStyle(btn, checked);
        }

        if (!autoSelected && !savedMethods.isEmpty()) {
            btnAuto.setChecked(false);
        } else if (autoSelected && savedMethods.size() > 1) {
            btnAuto.setChecked(false);
        }
    }

    private void saveConfiguration(MaterialButtonToggleGroup groupPorts) {
        Set<String> selectedMethods = new HashSet<>();
        if (btnAuto.isChecked()) {
            selectedMethods.add("AUTO");
        } else {
            for (MaterialButton btn : methodButtons) {
                if (btn.isChecked()) {
                    selectedMethods.add(getMethodNameFromButton(btn));
                }
            }
        }
        String methodsStr = String.join(",", selectedMethods);
        prefs.edit()
                .putString("scan_method", methodsStr)
                .putString("scan_level", getPortsFromId(groupPorts.getCheckedButtonId()))
                .apply();
    }

    private Set<String> getStoredMethodsSet() {
        String methodsStr = prefs.getString("scan_method", "AUTO");
        Set<String> methods = new HashSet<>();
        for (String part : methodsStr.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) methods.add(trimmed);
        }
        if (methods.isEmpty()) methods.add("AUTO");
        return methods;
    }

    private String getMethodNameFromButton(MaterialButton btn) {
        int id = btn.getId();
        if (id == R.id.btn_method_arp) return "ARP";
        if (id == R.id.btn_method_tcp) return "TCP";
        if (id == R.id.btn_method_icmp) return "ICMP";
        if (id == R.id.btn_method_mdns) return "MDNS";
        if (id == R.id.btn_method_ssdp) return "SSDP";
        if (id == R.id.btn_method_netbios) return "NETBIOS";
        return "AUTO";
    }

    private void updateButtonStyle(MaterialButton btn, boolean checked) {
        int color = checked ? getResources().getColor(R.color.button) : getResources().getColor(R.color.transparent);
        btn.setBackgroundColor(color);
    }

    private String getPortsFromId(int id) {
        if (id == R.id.btn_ports_500) return "500";
        if (id == R.id.btn_ports_1000) return "1000";
        return "100";
    }

    private boolean isRootAvailable() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su/xbin", "/su/bin/su"};
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }
}