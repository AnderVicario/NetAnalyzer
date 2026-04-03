package com.av19.netanalyzer.ui.devices;

import static com.av19.netanalyzer.utils.NetUtils.compareIps;

import android.animation.ValueAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.utils.NetUtils;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    private List<DeviceInfo> devices;
    private boolean[] expandedStates;
    private int expandedPosition = -1; // solo un panel expandido a la vez

    public DeviceAdapter(List<DeviceInfo> devices) {
        this.devices = devices;
        this.expandedStates = new boolean[devices.size()];
    }

    public void updateDevices(List<DeviceInfo> newDevices) {
        if (newDevices != null) {
            // Ordenar la lista antes de asignarla
            newDevices.sort((d1, d2) -> compareIps(d1.getIp(), d2.getIp()));
        }

        this.devices = newDevices;
        this.expandedStates = new boolean[newDevices != null ? newDevices.size() : 0];
        expandedPosition = -1;
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
        boolean expanded = expandedStates[position];
        holder.bind(device, expanded);

        holder.buttonPanel.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION) return; // item ya no existe

            // cerrar previamente expandido si es otro
            if (expandedPosition != -1 && expandedPosition != currentPosition) {
                expandedStates[expandedPosition] = false;
                notifyItemChanged(expandedPosition);
            }

            // toggle expand/collapse
            if (expandedStates[currentPosition]) {
                holder.collapse(holder.detailsPanel);
                expandedPosition = -1;
            } else {
                holder.expand(holder.detailsPanel);
                expandedPosition = currentPosition;
            }
            expandedStates[currentPosition] = !expandedStates[currentPosition];
        });
    }

    @Override
    public int getItemCount() {
        return devices != null ? devices.size() : 0;
    }

    public static class DeviceViewHolder extends RecyclerView.ViewHolder {

        private final LinearLayout buttonPanel;
        private final TextView titleTextView;
        private final TextView tagTextView;
        private final TextView subtitleTextView;
        private final ImageView iconImageView;
        private final LinearLayout detailsPanel;
        private final TextView detailsTextView;

        private ValueAnimator currentAnimator;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            buttonPanel = itemView.findViewById(R.id.buttonPanel);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            tagTextView = itemView.findViewById(R.id.device_tag);
            subtitleTextView = itemView.findViewById(R.id.subtitleTextView);
            iconImageView = itemView.findViewById(R.id.iconImageView);
            detailsPanel = itemView.findViewById(R.id.details_panel);
            detailsTextView = itemView.findViewById(R.id.detailsTextView);
        }

        private void bind(DeviceInfo device, boolean expanded) {
            // TÍTULO: hostname > OS > "Dispositivo"
            String title;
            if (device.getHostname() != null && !device.getHostname().isEmpty()) {
                title = device.getHostname();
            } else if (device.getOs() != null && !device.getOs().isEmpty()) {
                title = device.getOs();
            } else {
                title = "Dispositivo";
            }
            titleTextView.setText(title);

            // SUBTÍTULO: siempre la IP
            String subtitle = device.getIp() != null ? device.getIp() : "Unknown IP";
            subtitleTextView.setText(subtitle);

            // TAG e ICONO según tipo de dispositivo
            if (device.getIsCurrent()) {
                tagTextView.setText("● ACTUAL");
                iconImageView.setImageResource(R.drawable.ic_phone);
            } else if (device.getIsGateway()) {
                tagTextView.setText("● GATEWAY");
                iconImageView.setImageResource(R.drawable.ic_router);
            } else if (device.getIsDNS()) {
                tagTextView.setText("● DNS");
                iconImageView.setImageResource(R.drawable.ic_dns);
            } else {
                tagTextView.setText("● ONLINE");
                iconImageView.setImageResource(R.drawable.ic_device);
            }

            // DETALLES: toda la información adicional
            StringBuilder details = new StringBuilder();

            // ============================================================
            // 1. INFORMACIÓN DE RED
            // ============================================================
            boolean hasInfo = false;

            // Hostname (siempre en detalles, aunque sea el título)
            if (device.getHostname() != null && !device.getHostname().isEmpty()) {
                details.append("📡 HOSTNAME\n");
                details.append("  ").append(device.getHostname()).append("\n\n");
                hasInfo = true;
            }

            // ============================================================
            // 2. INFORMACIÓN DE HARDWARE
            // ============================================================
            if ((device.getMac() != null && !device.getMac().isEmpty()) ||
                    (device.getVendor() != null && !device.getVendor().isEmpty())) {
                details.append("🔧 HARDWARE\n");
                if (device.getMac() != null && !device.getMac().isEmpty()) {
                    details.append("  MAC: ").append(device.getMac()).append("\n");
                }
                if (device.getVendor() != null && !device.getVendor().isEmpty()) {
                    details.append("  Vendor: ").append(device.getVendor()).append("\n");
                }
                details.append("\n");
                hasInfo = true;
            }

            // ============================================================
            // 3. INFORMACIÓN DE DISPOSITIVO (OS, Modelo, TTL, etc.)
            // ============================================================
            boolean hasDeviceInfo = false;
            StringBuilder deviceInfo = new StringBuilder();

            // OS (siempre en detalles)
            if (device.getOs() != null && !device.getOs().isEmpty()) {
                deviceInfo.append("  OS: ").append(device.getOs()).append("\n");
                hasDeviceInfo = true;
            }

            // Modelo
            if (device.getModel() != null && !device.getModel().isEmpty()) {
                deviceInfo.append("  Model: ").append(device.getModel()).append("\n");
                hasDeviceInfo = true;
            }

            // TTL - siempre mostrar si está disponible
            if (device.getTtl() != null && device.getTtl() > 0) {
                deviceInfo.append("  TTL: ").append(device.getTtl());

                // Inferir OS basado en TTL si no hay OS específico
                if (device.getOs() == null || device.getOs().isEmpty()) {
                    if (device.getTtl() <= 64) {
                        deviceInfo.append(" (Linux/Android/Unix)");
                    } else if (device.getTtl() <= 128) {
                        deviceInfo.append(" (Windows)");
                    } else if (device.getTtl() <= 255) {
                        deviceInfo.append(" (Cisco/otros)");
                    }
                }
                deviceInfo.append("\n");
                hasDeviceInfo = true;
            }

            if (hasDeviceInfo) {
                details.append("💻 DEVICE INFO\n");
                details.append(deviceInfo);
                details.append("\n");
                hasInfo = true;
            }

            // ============================================================
            // 4. PUERTOS ABIERTOS
            // ============================================================
            if (device.getOpenPorts() != null && !device.getOpenPorts().isEmpty()) {
                details.append("🔌 OPEN PORTS (").append(device.getOpenPorts().size()).append(")\n");
                details.append("  ");
                for (int i = 0; i < device.getOpenPorts().size(); i++) {
                    int port = device.getOpenPorts().get(i);
                    details.append(port);

                    String serviceName = NetUtils.getPortServiceName(itemView.getContext(), "top1000.txt", port);
                    if (serviceName != null) {
                        details.append(" (").append(serviceName).append(")");
                    }

                    if (i < device.getOpenPorts().size() - 1) {
                        details.append(", ");
                        if ((i + 1) % 5 == 0) {
                            details.append("\n  ");
                        }
                    }
                }
                details.append("\n\n");
                hasInfo = true;
            } else {
                details.append("🔌 OPEN PORTS\n");
                details.append("  No open ports found.\n\n");
                hasInfo = true;
            }

            // ============================================================
            // 5. INFORMACIÓN ESPECIAL (Gateway, DNS, etc.)
            // ============================================================
            if (device.getIsCurrent() || device.getIsGateway() || device.getIsDNS()) {
                details.append("⭐ SPECIAL\n");
                if (device.getIsCurrent()) {
                    details.append("  This is the current device\n");
                }
                if (device.getIsGateway()) {
                    details.append("  This is the network gateway\n");
                }
                if (device.getIsDNS()) {
                    details.append("  This is a DNS server\n");
                }
                details.append("\n");
                hasInfo = true;
            }

            // Si no hay información adicional, mostrar mensaje
            if (!hasInfo) {
                details.append("ℹ️ No additional information available for this device.");
            }

            detailsTextView.setText(details.toString());

            // Estado inicial de detailsPanel
            detailsPanel.setVisibility(expanded ? View.VISIBLE : View.GONE);
            ViewGroup.LayoutParams lp = detailsPanel.getLayoutParams();
            lp.height = expanded ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
            detailsPanel.setLayoutParams(lp);
        }

        // Expand con animación
        private void expand(final View view) {
            // 1. Hacemos el panel visible pero con altura 0 para que mantenga el layout
            view.setVisibility(View.VISIBLE);

            view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    view.getViewTreeObserver().removeOnPreDrawListener(this);

                    // 2. SOLUCIÓN: Si el ancho es 0, usamos el ancho del padre (el item entero)
                    int widthSpec = View.MeasureSpec.makeMeasureSpec(
                            view.getWidth() > 0 ? view.getWidth() : ((View) view.getParent()).getWidth(),
                            View.MeasureSpec.EXACTLY
                    );
                    int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

                    view.measure(widthSpec, heightSpec);
                    final int targetHeight = view.getMeasuredHeight();

                    // 3. Comenzar la animación desde 0
                    ViewGroup.LayoutParams lp = view.getLayoutParams();
                    lp.height = 0;
                    view.setLayoutParams(lp);

                    ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
                    animator.addUpdateListener(animation -> {
                        lp.height = (int) animation.getAnimatedValue();
                        view.setLayoutParams(lp);
                    });

                    animator.setDuration(300);
                    animator.start();

                    return true;
                }
            });
        }

        // Collapse con animación
        private void collapse(final View view) {
            final int initialHeight = view.getMeasuredHeight();

            currentAnimator = ValueAnimator.ofInt(initialHeight, 0);
            currentAnimator.addUpdateListener(animation -> {
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                lp.height = (int) animation.getAnimatedValue();
                view.setLayoutParams(lp);
            });
            currentAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    view.setVisibility(View.GONE);
                }
            });
            currentAnimator.setDuration(300);
            currentAnimator.start();
        }
    }
}