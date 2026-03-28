package com.av19.netanalyzer.ui.devices;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.data.DeviceInfo;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    private List<DeviceInfo> devices;
    private boolean[] expandedStates;

    public DeviceAdapter(List<DeviceInfo> devices) {
        this.devices = devices;
        this.expandedStates = new boolean[devices.size()];
    }

    public void updateDevices(List<DeviceInfo> newDevices) {
        this.devices = newDevices;
        this.expandedStates = new boolean[newDevices.size()];
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.devices_item, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        DeviceInfo device = devices.get(position);
        holder.bind(device, expandedStates[position]);
        holder.buttonPanel.setOnClickListener(v -> {
            expandedStates[position] = !expandedStates[position];
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {

        LinearLayout buttonPanel;
        TextView titleTextView;
        TextView subtitleTextView;
        ImageView accessoryImageView;
        LinearLayout detailsPanel;
        TextView detailsTextView;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            buttonPanel = itemView.findViewById(R.id.buttonPanel);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            subtitleTextView = itemView.findViewById(R.id.subtitleTextView);
            accessoryImageView = itemView.findViewById(R.id.accessoryImageView);
            detailsPanel = itemView.findViewById(R.id.details_panel);
            detailsTextView = itemView.findViewById(R.id.detailsTextView);
        }

        void bind(DeviceInfo device, boolean expanded) {
            // Title: IP address (or fallback)
            titleTextView.setText(device.getIp() != null ? device.getIp() : "Unknown IP");

            // Subtitle: MAC + Vendor if available
            StringBuilder sub = new StringBuilder();
            if (device.getMac() != null && !device.getMac().isEmpty()) {
                sub.append(device.getMac());
            }
            if (device.getVendor() != null && !device.getVendor().isEmpty()) {
                if (sub.length() > 0) sub.append(" · ");
                sub.append(device.getVendor());
            }
            if (sub.length() > 0) {
                subtitleTextView.setText(sub.toString());
                subtitleTextView.setVisibility(View.VISIBLE);
            } else {
                subtitleTextView.setVisibility(View.GONE);
            }

            // Expand/collapse details panel
            if (expanded) {
                detailsPanel.setVisibility(View.VISIBLE);
                accessoryImageView.setRotation(90f); // rotate arrow
                // Build details text
                StringBuilder details = new StringBuilder();
                if (device.getOpenPorts() != null && !device.getOpenPorts().isEmpty()) {
                    details.append("Open ports: ");
                    for (int port : device.getOpenPorts()) {
                        details.append(port).append(" ");
                    }
                } else {
                    details.append("No open ports found.");
                }
                detailsTextView.setText(details.toString());
            } else {
                detailsPanel.setVisibility(View.GONE);
                accessoryImageView.setRotation(0f);
            }
        }
    }
}