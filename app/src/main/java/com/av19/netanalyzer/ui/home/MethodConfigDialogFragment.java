package com.av19.netanalyzer.ui.home;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.utils.PreferencesManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

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
        View root = inflater.inflate(R.layout.dialog_config_container, container, false);

        String methodName = getArguments() != null ? getArguments().getString(ARG_METHOD_NAME) : "";
        Context context = requireContext();

        TextView title = root.findViewById(R.id.dialog_title);
        title.setText(getString(R.string.adv_settings_method_config_title, methodName));

        ViewGroup contentContainer = root.findViewById(R.id.content_container);
        View methodView = createConfigView(methodName, context);
        if (methodView != null) {
            contentContainer.addView(methodView);
            // Solo cargar valores para métodos que NO sean TCP (TCP ya se configura en createConfigView)
            if (!"TCP".equals(methodName)) {
                loadConfigValues(methodName, methodView);
            }
        }

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
        switch (methodName) {
            case "TCP":
                View tcpView = LayoutInflater.from(context).inflate(R.layout.dialog_tcp_config, null);
                setupTcpConfig(tcpView);
                return tcpView;
            case "ICMP":
                return LayoutInflater.from(context).inflate(R.layout.dialog_icmp_config, null);
            case "SSDP":
                return LayoutInflater.from(context).inflate(R.layout.dialog_ssdp_config, null);
            default:
                return null;
        }
    }

    private void loadConfigValues(String methodName, View view) {
        switch (methodName) {
            case "ICMP":
                Resources res = requireContext().getResources();
                String defaultCount = String.valueOf(res.getInteger(R.integer.icmp_count_default));
                String defaultTimeout = String.valueOf(res.getInteger(R.integer.icmp_timeout_default));
                String defaultPacketSize = String.valueOf(res.getInteger(R.integer.icmp_packet_size_default));
                String defaultThreads = String.valueOf(res.getInteger(R.integer.icmp_threads_default));

                ((EditText) view.findViewById(R.id.icmp_count)).setText(pm.getMethodParam("icmp", "count", defaultCount));
                ((EditText) view.findViewById(R.id.icmp_timeout)).setText(pm.getMethodParam("icmp", "timeout", defaultTimeout));
                ((EditText) view.findViewById(R.id.icmp_packet_size)).setText(pm.getMethodParam("icmp", "packet_size", defaultPacketSize));
                ((EditText) view.findViewById(R.id.icmp_threads)).setText(pm.getMethodParam("icmp", "threads", defaultThreads));
                break;
            case "SSDP":
                Resources resSsdp = requireContext().getResources();
                String defaultTotal = String.valueOf(resSsdp.getInteger(R.integer.ssdp_timeout_total_default));
                String defaultSocket = String.valueOf(resSsdp.getInteger(R.integer.ssdp_socket_timeout_default));
                String defaultHttp = String.valueOf(resSsdp.getInteger(R.integer.ssdp_http_timeout_default));
                String defaultDelay = String.valueOf(resSsdp.getInteger(R.integer.ssdp_search_delay_default));

                ((EditText) view.findViewById(R.id.ssdp_timeout_total)).setText(pm.getMethodParam("ssdp", "timeout_total", defaultTotal));
                ((EditText) view.findViewById(R.id.ssdp_socket_timeout)).setText(pm.getMethodParam("ssdp", "socket_timeout", defaultSocket));
                ((EditText) view.findViewById(R.id.ssdp_http_timeout)).setText(pm.getMethodParam("ssdp", "http_timeout", defaultHttp));
                ((EditText) view.findViewById(R.id.ssdp_search_delay)).setText(pm.getMethodParam("ssdp", "search_delay", defaultDelay));
                break;
        }
    }

    private void saveConfigValues(String methodName, View view) {
        switch (methodName) {
            case "TCP":
                // Guardar número de hilos
                String threadsStr = ((EditText) view.findViewById(R.id.tcp_threads)).getText().toString();
                pm.setMethodParam("tcp", "threads", threadsStr);

                // Guardar lista de pares (puerto, timeout)
                LinearLayout container = view.findViewById(R.id.ports_container);
                List<PortTimeoutPair> pairs = new ArrayList<>();
                for (int i = 0; i < container.getChildCount(); i++) {
                    View row = container.getChildAt(i);
                    EditText portEdit = row.findViewById(R.id.port_edit);
                    EditText timeoutEdit = row.findViewById(R.id.timeout_edit);
                    String portStr = portEdit.getText().toString().trim();
                    String timeoutStr = timeoutEdit.getText().toString().trim();
                    if (!portStr.isEmpty() && !timeoutStr.isEmpty()) {
                        try {
                            int port = Integer.parseInt(portStr);
                            int timeout = Integer.parseInt(timeoutStr);
                            pairs.add(new PortTimeoutPair(port, timeout));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                String json = new Gson().toJson(pairs);
                pm.setMethodParam("tcp", "ports", json);
                break;

            case "ICMP":
                pm.setMethodParam("icmp", "count", ((EditText) view.findViewById(R.id.icmp_count)).getText().toString());
                pm.setMethodParam("icmp", "timeout", ((EditText) view.findViewById(R.id.icmp_timeout)).getText().toString());
                pm.setMethodParam("icmp", "packet_size", ((EditText) view.findViewById(R.id.icmp_packet_size)).getText().toString());
                pm.setMethodParam("icmp", "threads", ((EditText) view.findViewById(R.id.icmp_threads)).getText().toString());
                break;

            case "SSDP":
                pm.setMethodParam("ssdp", "timeout_total", ((EditText) view.findViewById(R.id.ssdp_timeout_total)).getText().toString());
                pm.setMethodParam("ssdp", "socket_timeout", ((EditText) view.findViewById(R.id.ssdp_socket_timeout)).getText().toString());
                pm.setMethodParam("ssdp", "http_timeout", ((EditText) view.findViewById(R.id.ssdp_http_timeout)).getText().toString());
                pm.setMethodParam("ssdp", "search_delay", ((EditText) view.findViewById(R.id.ssdp_search_delay)).getText().toString());
                break;
        }
    }

    private void setupTcpConfig(View view) {
        LinearLayout container = view.findViewById(R.id.ports_container);
        Button btnAdd = view.findViewById(R.id.btn_add_port);
        EditText threadsEdit = view.findViewById(R.id.tcp_threads);

        // Cargar configuración guardada (hilos)
        String threadsStr = pm.getMethodParam("tcp", "threads",
                String.valueOf(getResources().getInteger(R.integer.tcp_threads_default)));
        threadsEdit.setText(threadsStr);

        // Cargar lista de puertos desde JSON
        String portsJson = pm.getMethodParam("tcp", "ports", null);
        List<PortTimeoutPair> pairs = new ArrayList<>();
        if (portsJson != null && !portsJson.isEmpty()) {
            try {
                Type type = new TypeToken<List<PortTimeoutPair>>() {
                }.getType();
                pairs = new Gson().fromJson(portsJson, type);
            } catch (Exception e) { /* ignorar */ }
        }
        // Si no hay pares guardados, usar un par por defecto
        if (pairs.isEmpty()) {
            int defaultPort = getResources().getInteger(R.integer.tcp_default_port);
            int defaultTimeout = getResources().getInteger(R.integer.tcp_default_timeout);
            pairs.add(new PortTimeoutPair(defaultPort, defaultTimeout));
        }
        // Mostrar las filas
        for (PortTimeoutPair pair : pairs) {
            addPortRow(container, pair.port, pair.timeout);
        }

        btnAdd.setOnClickListener(v -> addPortRow(container, null, null));
    }

    private void addPortRow(LinearLayout container, Integer port, Integer timeout) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_tcp_item_port_timeout, container, false);
        EditText portEdit = row.findViewById(R.id.port_edit);
        EditText timeoutEdit = row.findViewById(R.id.timeout_edit);
        ImageButton removeBtn = row.findViewById(R.id.btn_remove);

        if (port != null) portEdit.setText(String.valueOf(port));
        if (timeout != null) timeoutEdit.setText(String.valueOf(timeout));

        removeBtn.setOnClickListener(v -> container.removeView(row));
        container.addView(row);
    }

    public interface ConfigSaveListener {
        void onConfigSaved(String methodName);
    }

    private static class PortTimeoutPair {
        int port;
        int timeout;

        PortTimeoutPair(int port, int timeout) {
            this.port = port;
            this.timeout = timeout;
        }
    }
}