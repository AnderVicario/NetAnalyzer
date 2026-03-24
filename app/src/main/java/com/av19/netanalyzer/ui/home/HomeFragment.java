package com.av19.netanalyzer.ui.home;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.data.ScanState;
import com.av19.netanalyzer.service.ScanService;
import com.av19.netanalyzer.viewmodel.ScanViewModel;
import com.google.android.material.button.MaterialButton;

public class HomeFragment extends Fragment {

    private ScanViewModel viewModel;
    private boolean isScanning = false;
    private AnimatorSet rippleSet;

    private View ring1, ring2, ring3;
    private MaterialButton btnScan;
    private TextView tvStatus;

    // Network info UI elements
    private TextView tvIp;
    private TextView tvGateway;
    private TextView tvNet;
    private TextView tvConnection;

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ring1 = view.findViewById(R.id.ring1);
        ring2 = view.findViewById(R.id.ring2);
        ring3 = view.findViewById(R.id.ring3);
        btnScan = view.findViewById(R.id.btn_scan);
        tvStatus = view.findViewById(R.id.tv_status);

        // Find network info views
        tvIp = view.findViewById(R.id.tv_ip);
        tvGateway = view.findViewById(R.id.tv_gateway);
        tvNet = view.findViewById(R.id.tv_net);
        tvConnection = view.findViewById(R.id.tv_connection);

        viewModel = new ViewModelProvider(requireActivity()).get(ScanViewModel.class);

        btnScan.setOnClickListener(v -> {
            if (isScanning) stopScan();
            else startScan();
        });

        // Observe state changes
        viewModel.getScanState().observe(getViewLifecycleOwner(), this::updateUi);
    }

    private void updateUi(ScanState state) {
        if (state == null) return;

        // Update network info (if available)
        updateNetworkInfo(state.getNetworkInfo());

        switch (state.getStatus()) {
            case SCANNING:
                isScanning = true;
                btnScan.setText("STOP");
                tvStatus.setText("Escaneando red… " + state.getProgress() + "%");
                tvStatus.animate().alpha(1f).setDuration(300).start();
                startRippleAnimation();
                break;
            case COMPLETED:
                isScanning = false;
                btnScan.setText("SCAN");
                tvStatus.animate().alpha(0f).setDuration(200).start();
                stopRippleAnimation();
                break;
            case ERROR:
                isScanning = false;
                btnScan.setText("SCAN");
                tvStatus.setText("Error: " + state.getErrorMessage());
                tvStatus.animate().alpha(1f).setDuration(300).start();
                stopRippleAnimation();
                break;
            default:
                isScanning = false;
                btnScan.setText("SCAN");
                tvStatus.animate().alpha(0f).setDuration(200).start();
                stopRippleAnimation();
                break;
        }
    }

    private void updateNetworkInfo(NetworkInfo info) {
        if (info == null) {
            // Set placeholders or clear
            tvIp.setText("X.X.X.X");
            tvGateway.setText("X.X.X.X");
            tvNet.setText("X.X.X.X/X");
            tvConnection.setText("—");
            return;
        }

        // Update the four fields
        tvIp.setText(info.getIp() != null ? info.getIp() : "—");

        String gatewayText = info.getGateway() != null ? info.getGateway() : "—";
        tvGateway.setText(gatewayText);

        // Network address in format "192.168.1.0/24"
        String netText = info.getNetworkAddress() != null ?
                info.getNetworkAddress() + "/" + info.getPrefix() : "—";
        tvNet.setText(netText);

        // Connection type (WiFi, Cellular, etc.)
        String connText = info.getConnectionType() != null ? info.getConnectionType() : "—";
        tvConnection.setText(connText);
    }

    private void startScan() {
        Intent intent = new Intent(requireContext(), ScanService.class);

        // Handle foreground service based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ (API 26+)
            requireContext().startForegroundService(intent);
        } else {
            // Android 7.0 and below (API 24-25)
            requireContext().startService(intent);
        }
    }

    private void stopScan() {
        Intent intent = new Intent(requireContext(), ScanService.class);
        intent.setAction("STOP_SCAN");
        requireContext().startService(intent);
        // Optionally also reset state in ViewModel
        viewModel.resetScan();
    }

    private void startRippleAnimation() {
        if (rippleSet != null && rippleSet.isRunning()) return;
        rippleSet = buildRippleAnimator();
        rippleSet.start();
    }

    private void stopRippleAnimation() {
        if (rippleSet != null) {
            rippleSet.cancel();
            rippleSet = null;
        }
        resetRings();
    }

    // ── Animación de ondas concéntricas ───────────────────────────────────────
    private AnimatorSet buildRippleAnimator() {
        long duration = 1800L;
        long delay1 = 0L;
        long delay2 = 500L;
        long delay3 = 1000L;

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                buildSingleRipple(ring1, duration, delay1),
                buildSingleRipple(ring2, duration, delay2),
                buildSingleRipple(ring3, duration, delay3)
        );

        // Repetición infinita manual: al terminar el ciclo, lo relanzamos
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isScanning) {
                    resetRings();
                    rippleSet = buildRippleAnimator();
                    rippleSet.start();
                }
            }
        });
        return set;
    }

    private AnimatorSet buildSingleRipple(View ring, long duration, long startDelay) {
        // Scale 1 → 1.6
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ring, View.SCALE_X, 1f, 1.6f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ring, View.SCALE_Y, 1f, 1.6f);
        // Alpha 0.8 → 0
        ObjectAnimator alpha  = ObjectAnimator.ofFloat(ring, View.ALPHA, 0.8f, 0f);

        AnimatorSet ringSet = new AnimatorSet();
        ringSet.playTogether(scaleX, scaleY, alpha);
        ringSet.setDuration(duration);
        ringSet.setStartDelay(startDelay);
        ringSet.setInterpolator(new AccelerateDecelerateInterpolator());
        return ringSet;
    }

    private void resetRings() {
        for (View r : new View[]{ring1, ring2, ring3}) {
            r.setScaleX(1f);
            r.setScaleY(1f);
            r.setAlpha(0f);
        }
    }

    // ── Limpieza al destruir la vista ─────────────────────────────────────────
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (rippleSet != null) {
            rippleSet.cancel();
        }
    }
}