package com.av19.netanalyzer.ui.devices;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.ScanState;
import com.av19.netanalyzer.viewmodel.ScanViewModel;

import java.util.ArrayList;
import java.util.List;

public class DevicesFragment extends Fragment {

    private ScanViewModel viewModel;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private DeviceAdapter adapter;

    public DevicesFragment() {
        super(R.layout.fragment_devices);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerViewDevices);
        emptyView = view.findViewById(R.id.emptyView);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DeviceAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(requireActivity()).get(ScanViewModel.class);
        viewModel.getScanState().observe(getViewLifecycleOwner(), this::updateDevicesList);
    }

    private void updateDevicesList(ScanState state) {
        List<DeviceInfo> devices = (state != null && state.getDevices() != null)
                ? state.getDevices()
                : new ArrayList<>();

        if (devices.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            adapter.updateDevices(devices);
        }
    }
}