package com.av19.netanalyzer.ui.home;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.utils.PreferencesManager;
import com.av19.netanalyzer.utils.SnackbarUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdvancedSettingsBottomSheet extends BottomSheetDialogFragment
        implements MethodConfigDialogFragment.ConfigSaveListener, ImportConfigDialogFragment.ImportListener {

    private PreferencesManager pm;
    private List<MaterialButton> methodButtons;
    private MaterialButton btnAuto;
    private MaterialButtonToggleGroup groupPorts;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = new PreferencesManager(requireContext());
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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.layout_advanced_settings, container, false);

        ViewCompat.setOnApplyWindowInsetsListener(v, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            int basePaddingBottom = (int) (24 * getResources().getDisplayMetrics().density);
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), basePaddingBottom + systemBars.bottom);
            return windowInsets;
        });

        btnAuto = v.findViewById(R.id.btn_method_auto);
        MaterialButton btnArp = v.findViewById(R.id.btn_method_arp);
        MaterialButton btnTcp = v.findViewById(R.id.btn_method_tcp);
        MaterialButton btnIcmp = v.findViewById(R.id.btn_method_icmp);
        MaterialButton btnMdns = v.findViewById(R.id.btn_method_mdns);
        MaterialButton btnSsdp = v.findViewById(R.id.btn_method_ssdp);
        MaterialButton btnNetbios = v.findViewById(R.id.btn_method_netbios);
        ImageButton btnImport = v.findViewById(R.id.btn_import);
        ImageButton btnExport = v.findViewById(R.id.btn_export);
        groupPorts = v.findViewById(R.id.toggle_group_ports);

        methodButtons = Arrays.asList(btnArp, btnTcp, btnIcmp, btnMdns, btnSsdp, btnNetbios);

        // Tooltips
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            btnTcp.setTooltipText(getString(R.string.adv_settings_long_press));
            btnIcmp.setTooltipText(getString(R.string.adv_settings_long_press));
            btnSsdp.setTooltipText(getString(R.string.adv_settings_long_press));
        }

        // AUTO listener
        btnAuto.setOnClickListener(vw -> {
            if (btnAuto.isChecked()) {
                for (MaterialButton btn : methodButtons) {
                    btn.setChecked(false);
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

        // Manual method listener
        View.OnClickListener clickListener = vw -> {
            MaterialButton btn = (MaterialButton) vw;
            if (btn.isChecked()) {
                if (btn.getId() == R.id.btn_method_arp) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isRootAvailable()) {
                        SnackbarUtils.showWarning(requireView(), requireContext(),
                                getString(R.string.adv_settings_arp_warning));
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
        };

        // Long‑click: ahora mostramos el DialogFragment
        View.OnLongClickListener longClickListener = vw -> {
            MaterialButton btn = (MaterialButton) vw;
            String methodName = getMethodNameFromButton(btn);
            MethodConfigDialogFragment dialogFrag = MethodConfigDialogFragment.newInstance(methodName);
            dialogFrag.show(getChildFragmentManager(), MethodConfigDialogFragment.class.getSimpleName());
            return true;
        };

        for (MaterialButton btn : methodButtons) {
            btn.setOnClickListener(clickListener);
        }
        btnTcp.setOnLongClickListener(longClickListener);
        btnIcmp.setOnLongClickListener(longClickListener);
        btnSsdp.setOnLongClickListener(longClickListener);

        // Export / Import (solo ajustes avanzados)
        btnExport.setOnClickListener(view -> {
            String json = pm.exportAdvancedSettings();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, json);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.adv_settings_export_title)));
        });

        btnImport.setOnClickListener(view -> {
            ImportConfigDialogFragment dialog = ImportConfigDialogFragment.newInstance(ImportConfigDialogFragment.TYPE_ADVANCED_SETTINGS);
            dialog.show(getChildFragmentManager(), "import_config");
        });

        // Load saved selection
        loadSavedSelection();

        v.findViewById(R.id.btn_save_config).setOnClickListener(view -> {
            saveConfiguration();
            pm.println();
            dismiss();
        });

        return v;
    }

    @Override
    public void onConfigSaved(String methodName) {
        // Actualmente no hace falta hacer nada porque PreferencesManager ya se actualiza solo.
    }

    private void loadSavedSelection() {
        List<String> activeMethods = pm.getActiveMethods();
        boolean autoSelected = activeMethods.contains("AUTO");

        btnAuto.setChecked(autoSelected);
        for (MaterialButton btn : methodButtons) {
            String methodName = getMethodNameFromButton(btn);
            boolean checked = activeMethods.contains(methodName);
            btn.setChecked(checked);
        }

        String level = pm.getScanLevel();
        int portId = R.id.btn_ports_100;
        if ("500".equals(level)) portId = R.id.btn_ports_500;
        else if ("1000".equals(level)) portId = R.id.btn_ports_1000;
        groupPorts.check(portId);
    }

    private void saveConfiguration() {
        List<String> selectedMethods = new ArrayList<>();
        if (btnAuto.isChecked()) {
            selectedMethods.add("AUTO");
        } else {
            for (MaterialButton btn : methodButtons) {
                if (btn.isChecked()) {
                    selectedMethods.add(getMethodNameFromButton(btn));
                }
            }
        }
        pm.setActiveMethods(selectedMethods);
        pm.setScanLevel(getPortsFromId(groupPorts.getCheckedButtonId()));
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

    private String getPortsFromId(int id) {
        if (id == R.id.btn_ports_500) return "500";
        if (id == R.id.btn_ports_1000) return "1000";
        return "100";
    }

    private boolean isRootAvailable() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su/xbin", "/su/bin/su"};
        for (String path : paths) {
            if (new java.io.File(path).exists()) return true;
        }
        return false;
    }

    @Override
    public void onImportSuccess() {
        loadSavedSelection();
    }
}