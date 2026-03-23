package com.av19.netanalyzer.ui.home;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.av19.netanalyzer.R;
import com.google.android.material.button.MaterialButton;

public class HomeFragment extends Fragment {

    private boolean isScanning = false;
    private AnimatorSet rippleSet;

    private View ring1, ring2, ring3;
    private MaterialButton btnScan;
    private TextView tvStatus;

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ring1    = view.findViewById(R.id.ring1);
        ring2    = view.findViewById(R.id.ring2);
        ring3    = view.findViewById(R.id.ring3);
        btnScan  = view.findViewById(R.id.btn_scan);
        tvStatus = view.findViewById(R.id.tv_status);

        btnScan.setOnClickListener(v -> {
            if (isScanning) stopScan();
            else            startScan();
        });
    }

    // ── Iniciar escaneo ───────────────────────────────────────────────────────

    private void startScan() {
        isScanning = true;
        btnScan.setText("STOP");
        tvStatus.setText("Escaneando red…");
        tvStatus.animate().alpha(1f).setDuration(300).start();

        rippleSet = buildRippleAnimator();
        rippleSet.start();
    }

    // ── Detener escaneo ───────────────────────────────────────────────────────

    private void stopScan() {
        isScanning = false;
        btnScan.setText("SCAN");
        tvStatus.animate().alpha(0f).setDuration(200).start();

        if (rippleSet != null) {
            rippleSet.cancel();
            rippleSet = null;
        }
        resetRings();
    }

    // ── Animación de ondas concéntricas ───────────────────────────────────────

    private AnimatorSet buildRippleAnimator() {
        long duration  = 1800L;
        long delay1    = 0L;
        long delay2    = 500L;
        long delay3    = 1000L;

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