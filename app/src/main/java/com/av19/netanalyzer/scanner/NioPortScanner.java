package com.av19.netanalyzer.scanner;

import android.util.Log;

import com.av19.netanalyzer.utils.CancellationToken;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class NioPortScanner {

    private final int[] ports;
    private final int timeoutMillis;
    private final int maxInflight;

    public NioPortScanner(int[] ports) {
        this(ports, 1000, 50);
    }

    public NioPortScanner(int[] ports, int timeoutMillis, int maxInflight) {
        this.ports = ports;
        this.timeoutMillis = timeoutMillis;
        this.maxInflight = maxInflight;
    }

    public List<Integer> scan(String host, CancellationToken token) {
        List<Integer> openPorts = new ArrayList<>();
        Map<SelectionKey, Long> timestamps = new HashMap<>();

        try (Selector selector = Selector.open()) {
            InetAddress addr = InetAddress.getByName(host);
            int inflight = 0;
            int index = 0;

            while (index < ports.length || inflight > 0) {
                if (token.isCancelled()) break;

                // Lanzar nuevas conexiones si hay hueco
                while (index < ports.length && inflight < maxInflight && !token.isCancelled()) {
                    int port = ports[index++];
                    try {
                        SocketChannel ch = SocketChannel.open();
                        ch.configureBlocking(false);
                        ch.connect(new InetSocketAddress(addr, port));
                        SelectionKey key = ch.register(selector, SelectionKey.OP_CONNECT, port);
                        timestamps.put(key, System.currentTimeMillis());
                        inflight++;
                    } catch (Exception e) { /* ignorar errores individuales */ }
                }

                // Esperar eventos
                if (selector.select(200) > 0) {
                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();
                        SocketChannel ch = (SocketChannel) key.channel();
                        try {
                            if (ch.finishConnect()) {
                                openPorts.add((Integer) key.attachment());
                            }
                        } catch (Exception ignored) {
                        } finally {
                            timestamps.remove(key);
                            ch.close();
                            inflight--;
                        }
                    }
                }

                // Limpieza de zombies
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<SelectionKey, Long>> it = timestamps.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<SelectionKey, Long> entry = it.next();
                    if (now - entry.getValue() > timeoutMillis) {
                        SelectionKey key = entry.getKey();
                        try {
                            key.channel().close();
                        } catch (Exception ignored) {
                        }
                        key.cancel();
                        it.remove();
                        inflight--;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("NioPortScanner", "Error escaneando puertos de " + host, e);
        }
        return openPorts;
    }
}