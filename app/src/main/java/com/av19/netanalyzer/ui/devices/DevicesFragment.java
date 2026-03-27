package com.av19.netanalyzer.ui.devices;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.ScanState;
import com.av19.netanalyzer.viewmodel.ScanViewModel;

import java.util.List;

public class DevicesFragment extends Fragment {

    private ScanViewModel viewModel;
    private LinearLayout devicesContainer;

    public DevicesFragment() {
        super(R.layout.fragment_devices);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        devicesContainer = view.findViewById(R.id.devices_container);
        viewModel = new ViewModelProvider(requireActivity()).get(ScanViewModel.class);

        viewModel.getScanState().observe(getViewLifecycleOwner(), this::updateDevicesList);
    }

    private void updateDevicesList(ScanState state) {
        devicesContainer.removeAllViews();
        if (state == null || state.getDevices() == null || state.getDevices().isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No devices discovered yet.\nStart a scan from Home.");
            empty.setTextSize(14);
            empty.setPadding(20, 20, 20, 20);
            devicesContainer.addView(empty);
            return;
        }

        for (DeviceInfo device : state.getDevices()) {
            addDeviceView(device);
        }
    }

    private void addDeviceView(DeviceInfo device) {
        if (device==null){return;}
        TextView tv = new TextView(requireContext());
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(13);
        tv.setPadding(20, 10, 20, 10);

        StringBuilder sb = new StringBuilder();
        if (device.getIp()!= null && !device.getIp().isEmpty()) {
            sb.append("IP: ").append(device.getIp()).append("\n");
        }
        if (device.getMac()!= null && !device.getMac().isEmpty()) {
            sb.append("MAC: ").append(device.getMac()).append("\n");
        }
        if (device.getVendor()!= null && !device.getVendor().isEmpty()) {
            sb.append("Vendor: ").append(device.getVendor()).append("\n");
        }
        if (device.getOpenPorts() != null && !device.getOpenPorts().isEmpty()) {
            sb.append("Open ports: ");
            for (int port : device.getOpenPorts()) {
                sb.append(port).append(" ");
            }
            sb.append("\n");
        } else {
            sb.append("No open ports found.\n");
        }
        tv.setText(sb.toString());

        devicesContainer.addView(tv);

        // Add a divider
        View divider = new View(requireContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFFDDDDDD);
        devicesContainer.addView(divider);
    }
}