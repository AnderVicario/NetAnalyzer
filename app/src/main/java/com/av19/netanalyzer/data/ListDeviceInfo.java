package com.av19.netanalyzer.data;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ListDeviceInfo {
    private final Map<String, DeviceInfo> deviceMap = new ConcurrentHashMap<>();

    /**
     * Añade o fusiona un dispositivo. Devuelve el dispositivo resultante (el que queda en el mapa).
     * Es seguro llamarlo desde múltiples hilos.
     */
    @NonNull
    public DeviceInfo addOrUpdate(@NonNull DeviceInfo newDevice) {
        String ip = newDevice.getIp();
        if (ip == null) {
            throw new IllegalArgumentException("DeviceInfo must have an IP address");
        }
        return deviceMap.merge(ip, newDevice, this::merge);
    }

    /**
     * Lógica de fusión. Toma el dispositivo existente y el nuevo, y modifica el existente
     * con los campos no nulos del nuevo.
     */
    private DeviceInfo merge(DeviceInfo existing, DeviceInfo newDevice) {
        // Hostname
        if (newDevice.getHostname() != null && (existing.getHostname() == null || newDevice.getHostname().getPriority() > existing.getHostname().getPriority())) {
            existing.setHostname(newDevice.getHostname());
        }
        // MAC
        if (isEmpty(existing.getMac()) && !isEmpty(newDevice.getMac())) {
            existing.setMac(newDevice.getMac());
            existing.setVendor(newDevice.getVendor()); // vendor asociado a la MAC
        } else if (isEmpty(existing.getVendor()) && !isEmpty(newDevice.getVendor())) {
            existing.setVendor(newDevice.getVendor());
        }
        // OS
        if (newDevice.getOs() != null && (existing.getOs() == null || newDevice.getOs().getPriority() > existing.getOs().getPriority())) {
            existing.setOs(newDevice.getOs());
        }
        // TTL (el menor, más fiable)
        if (existing.getTtl() == null && newDevice.getTtl() != null) {
            existing.setTtl(newDevice.getTtl());
        } else if (existing.getTtl() != null && newDevice.getTtl() != null && newDevice.getTtl() < existing.getTtl()) {
            existing.setTtl(newDevice.getTtl());
        }
        // Modelo
        if (newDevice.getModel() != null && (existing.getModel() == null || newDevice.getModel().getPriority() > existing.getModel().getPriority())) {
            existing.setModel(newDevice.getModel());
        }
        // Puertos abiertos (fusión sin duplicados)
        if (newDevice.getOpenPorts() != null && !newDevice.getOpenPorts().isEmpty()) {
            List<Integer> merged = new ArrayList<>();
            if (existing.getOpenPorts() != null) merged.addAll(existing.getOpenPorts());
            for (Integer port : newDevice.getOpenPorts()) {
                if (!merged.contains(port)) merged.add(port);
            }
            Collections.sort(merged);
            existing.setOpenPorts(merged);
        }
        // Flags booleanos (si alguno es true, se queda)
        if (Boolean.TRUE.equals(newDevice.getIsCurrent())) existing.setIsCurrent(true);
        if (Boolean.TRUE.equals(newDevice.getIsGateway())) existing.setIsGateway(true);
        if (Boolean.TRUE.equals(newDevice.getIsDNS())) existing.setIsDNS(true);
        // ExtraDetails: fusionar mapas
        if (newDevice.getExtraDetails() != null && !newDevice.getExtraDetails().isEmpty()) {
            for (Map.Entry<String, String> entry : newDevice.getExtraDetails().entrySet()) {
                existing.addDetail(entry.getKey(), entry.getValue());
            }
        }
        return existing;
    }

    private boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /**
     * Devuelve una copia de la lista actual de dispositivos (inmutable a efectos de modificaciones externas).
     */
    @NonNull
    public List<DeviceInfo> getDevices() {
        return new ArrayList<>(deviceMap.values());
    }

    /**
     * Limpia la lista.
     */
    public void clear() {
        deviceMap.clear();
    }

    /**
     * Número de dispositivos.
     */
    public int size() {
        return deviceMap.size();
    }
}