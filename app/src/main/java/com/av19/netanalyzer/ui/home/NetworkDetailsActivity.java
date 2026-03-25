package com.av19.netanalyzer.ui.home;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.av19.netanalyzer.R;
import com.av19.netanalyzer.data.NetworkInfo;

public class NetworkDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_details);

        // Configurar Toolbar para volver atrás
        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        NetworkInfo info = getIntent().getParcelableExtra("EXTRA_NETWORK_INFO");
        if (info != null) {
            populateData(info);
        }
    }

    private void populateData(NetworkInfo info) {
        // Ejemplo de mapeo de datos
        setupDetailItem(R.id.detail_ip, "LOCAL IP", info.getIp());
        setupDetailItem(R.id.detail_gateway, "GATEWAY", info.getGateway());
        setupDetailItem(R.id.detail_mask, "NETMASK", info.getNetmask());
        setupDetailItem(R.id.detail_dns, "DNS", info.getDns());
        setupDetailItem(R.id.detail_ssid, "SSID", info.getSsid());
        setupDetailItem(R.id.detail_speed, "LINK SPEED", info.getLinkSpeed() + " Mbps");
        setupDetailItem(R.id.detail_rssi, "SIGNAL", info.getRssi() + " dBm");
        setupDetailItem(R.id.detail_metered, "METERED", info.isMetered() ? "YES" : "NO");
    }

    private void setupDetailItem(int containerId, String title, String value) {
        android.view.View container = findViewById(containerId);
        TextView tvTitle = container.findViewById(R.id.tv_title); // Ajusta según tu view_detail_item
        TextView tvValue = container.findViewById(R.id.tv_value);

        tvTitle.setText(title);
        applyGlitchEffect(tvValue, value != null ? value : "—");
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
}