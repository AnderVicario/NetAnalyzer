package com.av19.netanalyzer.ui.home;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.utils.PreferencesManager;

public class MethodConfigDialogFragment extends DialogFragment {

    private static final String ARG_METHOD_NAME = "method_name";
    private PreferencesManager pm;

    public static MethodConfigDialogFragment newInstance(String methodName) {
        MethodConfigDialogFragment frag = new MethodConfigDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_METHOD_NAME, methodName);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(
                    (int) (screenWidth * 0.9),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_DeviceDefault_Dialog);
        pm = new PreferencesManager(requireContext());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflar el contenedor del diálogo
        View root = inflater.inflate(R.layout.dialog_config_container, container, false);

        String methodName = getArguments() != null ? getArguments().getString(ARG_METHOD_NAME) : "";
        Context context = requireContext();

        // Título
        TextView title = root.findViewById(R.id.dialog_title);
        title.setText(getString(R.string.adv_settings_method_config_title, methodName));

        // Contenedor de contenido
        ViewGroup contentContainer = root.findViewById(R.id.content_container);

        // Inflar el layout específico del método y añadirlo al contenedor
        View methodView = createConfigView(methodName, context);
        if (methodView != null) {
            contentContainer.addView(methodView);
            loadConfigValues(methodName, methodView);
        }

        // Botones
        Button btnSave = root.findViewById(R.id.btn_save);
        Button btnCancel = root.findViewById(R.id.btn_cancel);

        btnCancel.setOnClickListener(v -> dismiss());

        btnSave.setOnClickListener(v -> {
            if (methodView != null) {
                saveConfigValues(methodName, methodView);
            }
            Fragment parent = getParentFragment();
            if (parent instanceof ConfigSaveListener) {
                ((ConfigSaveListener) parent).onConfigSaved(methodName);
            }
            dismiss();
        });

        return root;
    }

    @Nullable
    private View createConfigView(String methodName, Context context) {
        int layoutRes;
        switch (methodName) {
            case "TCP":
                layoutRes = R.layout.dialog_tcp_config;
                break;
            case "ICMP":
                layoutRes = R.layout.dialog_icmp_config;
                break;
            case "SSDP":
                layoutRes = R.layout.dialog_ssdp_config;
                break;
            default:
                return null;
        }
        return LayoutInflater.from(context).inflate(layoutRes, null);
    }

    private void loadConfigValues(String methodName, View view) {
        switch (methodName) {
            case "TCP":
                ((EditText) view.findViewById(R.id.tcp_port)).setText(pm.getMethodParam("tcp", "port", ""));
                ((EditText) view.findViewById(R.id.tcp_timeout)).setText(pm.getMethodParam("tcp", "timeout", "200"));
                break;
            case "ICMP":
                ((EditText) view.findViewById(R.id.icmp_count)).setText(pm.getMethodParam("icmp", "count", "1"));
                ((EditText) view.findViewById(R.id.icmp_timeout)).setText(pm.getMethodParam("icmp", "timeout", "700"));
                ((EditText) view.findViewById(R.id.icmp_packet_size)).setText(pm.getMethodParam("icmp", "packet_size", "56"));
                break;
            case "SSDP":
                ((EditText) view.findViewById(R.id.ssdp_timeout)).setText(pm.getMethodParam("ssdp", "timeout", "5000"));
                break;
        }
    }

    private void saveConfigValues(String methodName, View view) {
        switch (methodName) {
            case "TCP":
                pm.setMethodParam("tcp", "port", ((EditText) view.findViewById(R.id.tcp_port)).getText().toString());
                pm.setMethodParam("tcp", "timeout", ((EditText) view.findViewById(R.id.tcp_timeout)).getText().toString());
                break;
            case "ICMP":
                pm.setMethodParam("icmp", "count", ((EditText) view.findViewById(R.id.icmp_count)).getText().toString());
                pm.setMethodParam("icmp", "timeout", ((EditText) view.findViewById(R.id.icmp_timeout)).getText().toString());
                pm.setMethodParam("icmp", "packet_size", ((EditText) view.findViewById(R.id.icmp_packet_size)).getText().toString());
                break;
            case "SSDP":
                pm.setMethodParam("ssdp", "timeout", ((EditText) view.findViewById(R.id.ssdp_timeout)).getText().toString());
                break;
        }
    }

    public interface ConfigSaveListener {
        void onConfigSaved(String methodName);
    }
}