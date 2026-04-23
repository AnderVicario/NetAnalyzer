package com.av19.netanalyzer.ui.home;

import static com.av19.netanalyzer.utils.OpenRouterApiClient.hasToken;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.data.ScanState;
import com.av19.netanalyzer.service.ScanService;
import com.av19.netanalyzer.utils.OpenRouterApiClient;
import com.av19.netanalyzer.viewmodel.ScanViewModel;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
    private NetworkInfo currentInfo;
    private TextView tvDevicesCount;
    private TextView tvTimeElapsed;
    private TextView tvHistoryTime;
    private Handler timerHandler;
    private Runnable timerRunnable;
    private boolean timerRunning = false;

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

        tvDevicesCount = view.findViewById(R.id.tv_devices_count);
        tvTimeElapsed = view.findViewById(R.id.tv_time_elapsed);
        tvHistoryTime = view.findViewById(R.id.tv_history_time);

        OpenRouterApiClient.getInstance(requireContext());

        if (!hasToken()){
            btnScan.setIcon(null);
        } else {
            btnScan.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_ai));
        }

        SharedPreferences prefs = requireContext().getSharedPreferences("scan_summary", Context.MODE_PRIVATE);
        long lastScanTime = prefs.getLong("last_scan_time", 0);
        int lastDeviceCount = prefs.getInt("last_device_count", 0);
        long lastDuration = prefs.getLong("last_duration", 0);
        if (lastScanTime != 0) {
            updateHistoryCard(lastScanTime, lastDeviceCount, lastDuration);
        } else {
            // Placeholder si no hay resumen
            tvHistoryTime.setText("Never");
            tvDevicesCount.setText("0");
            tvTimeElapsed.setText("0 s");
        }

        btnScan.setOnClickListener(v -> {
            if (isScanning) stopScan();
            else startScan();
        });

        view.findViewById(R.id.more).setOnClickListener(v -> {
            if (currentInfo != null) {
                Intent intent = new Intent(requireContext(), NetworkDetailsActivity.class);
                intent.putExtra("EXTRA_NETWORK_INFO", currentInfo);
                startActivity(intent);
            }
        });

        view.findViewById(R.id.advanced_menu).setOnClickListener(v -> {
            AdvancedSettingsBottomSheet bottomSheet = new AdvancedSettingsBottomSheet();
            bottomSheet.show(getChildFragmentManager(), "AdvancedSettings");
        });

        // Observe state changes
        viewModel.getScanState().observe(getViewLifecycleOwner(), this::updateUi);
    }

    private void updateUi(ScanState state) {
        if (state == null) return;

        updateNetworkInfo(state.getNetworkInfo());

        switch (state.getStatus()) {
            case SCANNING:
                isScanning = true;
                btnScan.setText("STOP");
                tvStatus.setText("Escaneando red… " + state.getCurrentMethod() + " " + state.getProgress() + "%");
                tvStatus.animate().alpha(1f).setDuration(300).start();
                startRippleAnimation();

                // Si es el inicio real (start time es 0), lo fijamos
                if (viewModel.getScanStartTime() <= 0) {
                    viewModel.setScanStartTime(System.currentTimeMillis());
                }

                startTimer(); // El timer actualiza el UI en tiempo real
                updateDeviceCount(state.getDevices().size());
                tvHistoryTime.setText("In progress");
                break;

            case COMPLETED:
                // 1. Actualizar banderas y estado
                isScanning = false;

                // 2. Limpiar el start time en el ViewModel
                viewModel.setScanStartTime(-1);

                // 3. UI de estado detenido
                btnScan.setText("SCAN");
                tvStatus.animate().alpha(0f).setDuration(200).start();
                stopRippleAnimation();
                stopTimer();

                // 4. Mostrar lo que el ScanService acaba de guardar en SharedPreferences
                loadLastScanSummary();
                break;

            case ERROR:
            case IDLE:
                isScanning = false;
                btnScan.setText("SCAN");
                tvStatus.animate().alpha(0f).setDuration(200).start();
                stopRippleAnimation();
                stopTimer();
                viewModel.setScanStartTime(0);
                loadLastScanSummary(); // Mostrar el último escaneo exitoso
                break;
        }
    }

    private void updateNetworkInfo(NetworkInfo info) {
        this.currentInfo = info;
        if (info == null) {
            tvIp.setText("X.X.X.X");
            tvGateway.setText("X.X.X.X");
            tvNet.setText("X.X.X.X/X");
            tvConnection.setText("—");

            tvIp.setTag(R.id.tag_glitch_target, null);
            tvGateway.setTag(R.id.tag_glitch_target, null);
            tvNet.setTag(R.id.tag_glitch_target, null);
            tvConnection.setTag(R.id.tag_glitch_target, null);
            return;
        }

        String newIp = info.getIp() != null ? info.getIp() : "—";
        checkAndApplyGlitch(tvIp, newIp);

        String newGateway = info.getGateway() != null ? info.getGateway() : "—";
        checkAndApplyGlitch(tvGateway, newGateway);

        String newNet = info.getNetworkAddress() != null ?
                info.getNetworkAddress() + "/" + info.getPrefix() : "—";
        checkAndApplyGlitch(tvNet, newNet);

        String newConn = info.getConnectionType() != null ? info.getConnectionType() : "—";
        checkAndApplyGlitch(tvConnection, newConn);
    }

    private void startScan() {
        SharedPreferences p = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);

        Intent intent = new Intent(requireContext(), ScanService.class);
        intent.putExtra("SCAN_LEVEL", p.getString("scan_level", "100"));
        intent.putExtra("SCAN_METHOD", p.getString("scan_method", "AUTO"));

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
        ObjectAnimator alpha = ObjectAnimator.ofFloat(ring, View.ALPHA, 0.8f, 0f);

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
        stopTimer();
        if (rippleSet != null) {
            rippleSet.cancel();
        }
    }

    private void checkAndApplyGlitch(TextView textView, String newText) {
        String lastTarget = (String) textView.getTag(R.id.tag_glitch_target);

        if (newText.equals(lastTarget)) {
            return;
        }

        applyGlitchEffect(textView, newText);
    }

    private void applyGlitchEffect(final TextView textView, final String targetText) {
        textView.setTag(R.id.tag_glitch_target, targetText);

        final int duration = 120; // Algo muy rápido
        final int totalFrames = 4;

        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(0, totalFrames);
        animator.setDuration(duration);
        animator.setInterpolator(new android.view.animation.LinearInterpolator());

        animator.addUpdateListener(animation -> {
            int frame = (int) animation.getAnimatedValue();
            if (frame == totalFrames) {
                textView.setText(targetText);
            } else {
                textView.setText(generateRandomBinary(targetText.length()));
            }
        });
        animator.start();
    }

    private String generateRandomBinary(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(Math.random() > 0.5 ? "1" : "0");
        }
        return sb.toString();
    }

    private void startTimer() {
        if (timerRunning) return;
        timerRunning = true;
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!timerRunning) return;
                long start = viewModel.getScanStartTime();
                if (start > 0) {
                    long elapsedSeconds = (System.currentTimeMillis() - start) / 1000;
                    tvTimeElapsed.setText(elapsedSeconds + " s");
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunning) {
            timerRunning = false;
            if (timerHandler != null) {
                timerHandler.removeCallbacks(timerRunnable);
            }
        }
    }

    private void updateDeviceCount(int count) {
        tvDevicesCount.setText(String.valueOf(count));
    }

    private void saveScanSummary(long timestamp, int deviceCount, long durationSeconds) {
        SharedPreferences prefs = requireContext().getSharedPreferences("scan_summary", Context.MODE_PRIVATE);
        prefs.edit()
                .putLong("last_scan_time", timestamp)
                .putInt("last_device_count", deviceCount)
                .putLong("last_duration", durationSeconds)
                .apply();
    }

    private void updateHistoryCard(long timestamp, int deviceCount, long durationSeconds) {
        String dateStr = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date(timestamp));
        tvHistoryTime.setText(dateStr);
        tvDevicesCount.setText(String.valueOf(deviceCount));
        tvTimeElapsed.setText(durationSeconds + " s");
    }

    private String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date(timestamp));
    }

    private void loadLastScanSummary() {
        SharedPreferences prefs = requireContext().getSharedPreferences("scan_summary", Context.MODE_PRIVATE);
        long lastScanTime = prefs.getLong("last_scan_time", 0);
        int lastDeviceCount = prefs.getInt("last_device_count", 0);
        long lastDuration = prefs.getLong("last_duration", 0);

        if (lastScanTime != 0) {
            updateHistoryCard(lastScanTime, lastDeviceCount, lastDuration);
        }
    }
}