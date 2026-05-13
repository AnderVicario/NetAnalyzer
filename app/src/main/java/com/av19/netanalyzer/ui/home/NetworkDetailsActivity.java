package com.av19.netanalyzer.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.ui.base.BaseActivity;
import com.av19.netanalyzer.utils.LocaleManager;

import java.util.ArrayList;
import java.util.List;

public class NetworkDetailsActivity extends BaseActivity {

    private boolean isMosaicMode = false;
    private List<DetailItem> detailItems;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_details);

        // Configurar Toolbar
        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        // Obtener datos
        NetworkInfo info = getIntent().getParcelableExtra("EXTRA_NETWORK_INFO");
        if (info != null) {
            detailItems = getDetailItems(info);
            // Inicialmente mostrar modo lista (isMosaicMode = false)
            buildGridLayout(false, detailItems);
        }

        // Configurar botón
        ImageButton btn = findViewById(R.id.btnEditLayout);
        btn.setOnClickListener(v -> {
            isMosaicMode = !isMosaicMode;   // cambiar modo
            buildGridLayout(isMosaicMode, detailItems);

            // Actualizar icono y contentDescription
            if (isMosaicMode) {
                btn.setImageResource(R.drawable.ic_mosaic);   // icono de mosaico
                btn.setContentDescription("Mosaic layout");
            } else {
                btn.setImageResource(R.drawable.ic_list);     // icono de lista
                btn.setContentDescription("List layout");
            }
        });
    }

    private List<DetailItem> getDetailItems(NetworkInfo info) {
        List<DetailItem> items = new ArrayList<>();

        items.add(new DetailItem(getString(R.string.ip_address), info.getIp()));
        items.add(new DetailItem(getString(R.string.netmask), info.getNetmask()));
        items.add(new DetailItem(getString(R.string.prefix_length), String.valueOf(info.getPrefix())));
        items.add(new DetailItem(getString(R.string.network_address), info.getNetworkAddress()));
        items.add(new DetailItem(getString(R.string.gateway), info.getGateway()));

        ArrayList<String> dnsList = info.getDns();
        if (dnsList != null && !dnsList.isEmpty()) {
            if (dnsList.size() == 1) {
                items.add(new DetailItem(getString(R.string.dns_server), dnsList.get(0)));
            } else {
                for (int i = 0; i < dnsList.size(); i++) {
                    items.add(new DetailItem(
                            getString(R.string.dns_server_format, (i + 1)),
                            dnsList.get(i)
                    ));
                }
            }
        } else {
            items.add(new DetailItem(getString(R.string.dns_servers),
                    getString(R.string.none)));
        }

        items.add(new DetailItem(getString(R.string.connection_type), info.getConnectionType()));
        items.add(new DetailItem(getString(R.string.internet_access), info.isHasInternet() ? getString(R.string.yes) : getString(R.string.no)));
        items.add(new DetailItem(getString(R.string.network_validated), info.isValidated() ? getString(R.string.yes) : getString(R.string.no)));
        items.add(new DetailItem(getString(R.string.metered_connection), info.isMetered() ? getString(R.string.yes) : getString(R.string.no)));
        items.add(new DetailItem(getString(R.string.downstream), getString(R.string.bandwidth_format, info.getDownstreamBandwidth())));
        items.add(new DetailItem(getString(R.string.upstream), getString(R.string.bandwidth_format, info.getUpstreamBandwidth())));
        items.add(new DetailItem(getString(R.string.ssid), info.getSsid()));
        items.add(new DetailItem(getString(R.string.rssi), String.valueOf(info.getRssi())));
        items.add(new DetailItem(getString(R.string.bssid), info.getBssid()));
        items.add(new DetailItem(getString(R.string.link_speed), getString(R.string.link_speed_format, info.getLinkSpeed())));
        items.add(new DetailItem(getString(R.string.signal), getString(R.string.signal_format, info.getRssi())));

        return items;
    }

    private void buildGridLayout(boolean isMosaicMode, List<DetailItem> items) {
        GridLayout gridLayout = findViewById(R.id.detail_layout);
        gridLayout.removeAllViews();               // Limpia el contenido actual
        gridLayout.setColumnCount(isMosaicMode ? 2 : 1);  // 2 columnas para mosaico, 1 para lista

        int layoutRes = isMosaicMode ? R.layout.view_detail_item_mosaic : R.layout.view_detail_item_list;
        int marginPx = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 0.5f, getResources().getDisplayMetrics()));
        for (DetailItem item : items) {
            // Inflar el layout del ítem
            View itemView = getLayoutInflater().inflate(layoutRes, gridLayout, false);

            // Asignar título y valor
            TextView tvTitle = itemView.findViewById(R.id.tv_title);
            TextView tvValue = itemView.findViewById(R.id.tv_value);
            tvTitle.setText(item.title);
            applyGlitchEffect(tvValue, item.value != null ? item.value : "—");

            // Configurar LayoutParams para que cada elemento ocupe el ancho adecuado
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);  // columnWeight = 1
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);
            if (!isMosaicMode) {
                // En modo lista, el elemento ocupa las 2 columnas (si columnCount=2) o la única columna
                // Con columnCount=1, el comportamiento por defecto es ocupar toda la fila.
                // Podemos forzar que ocupe toda la fila con rowSpec, pero no es necesario.
                // Simplemente aseguramos que el ancho sea match_parent.
                lp.width = GridLayout.LayoutParams.MATCH_PARENT;
            }
            itemView.setLayoutParams(lp);
            gridLayout.addView(itemView);
        }
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

    private static class DetailItem {
        String title;
        String value;

        DetailItem(String title, String value) {
            this.title = title;
            this.value = value;
        }
    }
}