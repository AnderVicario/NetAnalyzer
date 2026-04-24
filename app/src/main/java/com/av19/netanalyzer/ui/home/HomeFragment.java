package com.av19.netanalyzer.ui.home;

import static com.av19.netanalyzer.utils.OpenRouterApiClient.hasToken;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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
import com.av19.netanalyzer.data.ScanRecord;
import com.av19.netanalyzer.data.ScanState;
import com.av19.netanalyzer.service.ScanService;
import com.av19.netanalyzer.utils.OpenRouterApiClient;
import com.av19.netanalyzer.utils.PreferencesManager;
import com.av19.netanalyzer.utils.SnackbarUtils;
import com.av19.netanalyzer.viewmodel.ScanViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.GsonBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
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

        if (!hasToken()) {
            btnScan.setIcon(null);
        } else {
            btnScan.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_ai));
        }

        PreferencesManager pm = new PreferencesManager(requireContext());
        List<ScanRecord> history = pm.getScanHistory();
        if (!history.isEmpty()) {
            ScanRecord last = history.get(0);
            updateHistoryCard(last.getTimestamp(), last.getDeviceCount(), last.getDurationSec());
        } else {
            tvHistoryTime.setText(getString(R.string.history_time_default));
            tvDevicesCount.setText("0");
            tvTimeElapsed.setText("0 s");
        }

        // Copiar último escaneo al portapapeles al pulsar la tarjeta de historial
        MaterialCardView cardHistory = view.findViewById(R.id.card_history);
        cardHistory.setOnClickListener(v -> copyLastScanToClipboard());

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

    private void copyLastScanToClipboard() {
        PreferencesManager pm = new PreferencesManager(requireContext());
        List<ScanRecord> history = pm.getScanHistory();
        if (history.isEmpty()) {
            SnackbarUtils.showSuccess(requireView(), requireContext(), getString(R.string.history_none_copy));
            return;
        }

        ScanRecord last = history.get(0);
        // Serializar el ScanRecord a JSON con formato legible
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(last);

        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("scan_record", json);
        clipboard.setPrimaryClip(clip);

        SnackbarUtils.showSuccess(requireView(), requireContext(), getString(R.string.history_copied_to_clipboard));
    }

    private void updateUi(ScanState state) {
        if (state == null) return;

        updateNetworkInfo(state.getNetworkInfo());

        switch (state.getStatus()) {
            case SCANNING:
                isScanning = true;
                btnScan.setText("STOP");
                tvStatus.setText(getString(R.string.scanning_network,
                        state.getCurrentMethod(),
                        state.getProgress()));
                tvStatus.animate().alpha(1f).setDuration(300).start();
                startRippleAnimation();

                if (viewModel.getScanStartTime() <= 0) {
                    viewModel.setScanStartTime(System.currentTimeMillis());
                }

                startTimer();
                updateDeviceCount(state.getDevices().size());
                tvHistoryTime.setText(getString(R.string.scanning_in_progress));
                break;

            case COMPLETED:
                isScanning = false;
                viewModel.setScanStartTime(-1);
                btnScan.setText("SCAN");
                tvStatus.animate().alpha(0f).setDuration(200).start();
                stopRippleAnimation();
                stopTimer();
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
                loadLastScanSummary();
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
        PreferencesManager pm = new PreferencesManager(requireContext());
        Intent intent = new Intent(requireContext(), ScanService.class);
        intent.putExtra("SCAN_LEVEL", pm.getScanLevel());
        List<String> activeMethods = pm.getActiveMethods();
        String methodStr = activeMethods.contains("AUTO") ? "AUTO" : TextUtils.join(",", activeMethods);
        intent.putExtra("SCAN_METHOD", methodStr);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent);
        } else {
            requireContext().startService(intent);
        }
    }

    private void stopScan() {
        Intent intent = new Intent(requireContext(), ScanService.class);
        intent.setAction("STOP_SCAN");
        requireContext().startService(intent);
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
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ring, View.SCALE_X, 1f, 1.6f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ring, View.SCALE_Y, 1f, 1.6f);
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
        if (newText.equals(lastTarget)) return;
        applyGlitchEffect(textView, newText);
    }

    private void applyGlitchEffect(final TextView textView, final String targetText) {
        textView.setTag(R.id.tag_glitch_target, targetText);
        final int duration = 120;
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

    private void updateHistoryCard(long timestamp, int deviceCount, long durationSeconds) {
        String dateStr = new SimpleDateFormat(getString(R.string.date_format), Locale.getDefault())
                .format(new Date(timestamp));
        tvHistoryTime.setText(dateStr);
        tvDevicesCount.setText(String.valueOf(deviceCount));
        tvTimeElapsed.setText(durationSeconds + " s");
    }

    private void loadLastScanSummary() {
        PreferencesManager pm = new PreferencesManager(requireContext());
        List<ScanRecord> history = pm.getScanHistory();
        if (!history.isEmpty()) {
            ScanRecord last = history.get(0);
            updateHistoryCard(last.getTimestamp(), last.getDeviceCount(), last.getDurationSec());
        } else {
            tvHistoryTime.setText(getString(R.string.history_time_default));
            tvDevicesCount.setText("0");
            tvTimeElapsed.setText("0 s");
        }
    }
}