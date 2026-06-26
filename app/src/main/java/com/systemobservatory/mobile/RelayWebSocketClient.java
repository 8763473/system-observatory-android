package com.systemobservatory.mobile;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public final class RelayWebSocketClient {
    private static final String TAG = "RelayWebSocket";
    private static final int NORMAL_CLOSURE = 1000;
    private static final long RECONNECT_DELAY_MS = 5000;

    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebSocket webSocket;
    private String relayUrl;
    private String deviceKey;
    private Listener listener;
    private boolean manualClose;
    private int reconnectAttempts;

    public interface Listener {
        void onOpen();
        void onMessage(String json);
        void onClose(int code, String reason);
        void onError(String message);
    }

    public RelayWebSocketClient() {
        client = new OkHttpClient.Builder()
                .pingInterval(java.time.Duration.ofSeconds(15))
                .build();
    }

    public void connect(String baseUrl, String key, Listener l) {
        relayUrl = normalizeWsUrl(baseUrl);
        deviceKey = key == null ? "" : key.trim();
        listener = l;
        manualClose = false;
        reconnectAttempts = 0;
        openConnection();
    }

    public void disconnect() {
        manualClose = true;
        if (webSocket != null) {
            try { webSocket.close(NORMAL_CLOSURE, "client disconnect"); } catch (Exception ignored) {}
            webSocket = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    public boolean isConnected() {
        return webSocket != null;
    }

    private void openConnection() {
        if (relayUrl == null || relayUrl.isEmpty()) return;
        Request request = new Request.Builder()
                .url(relayUrl)
                .header("X-Device-Key", deviceKey)
                .build();

        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                reconnectAttempts = 0;
                mainHandler.post(() -> {
                    if (listener != null) listener.onOpen();
                });
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                mainHandler.post(() -> {
                    if (listener != null) listener.onMessage(text);
                });
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                String text = bytes.utf8();
                mainHandler.post(() -> {
                    if (listener != null) listener.onMessage(text);
                });
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                try { ws.close(code, reason); } catch (Exception ignored) {}
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                mainHandler.post(() -> {
                    if (listener != null) listener.onClose(code, reason);
                    scheduleReconnect();
                });
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                String msg = t != null ? t.getMessage() : "unknown error";
                Log.w(TAG, "WebSocket failure: " + msg);
                mainHandler.post(() -> {
                    if (listener != null) listener.onError(msg);
                    scheduleReconnect();
                });
            }
        });
    }

    private void scheduleReconnect() {
        if (manualClose) return;
        reconnectAttempts++;
        long delay = Math.min(RECONNECT_DELAY_MS * reconnectAttempts, 30000);
        mainHandler.postDelayed(this::openConnection, delay);
    }

    private static String normalizeWsUrl(String baseUrl) {
        if (baseUrl == null) return "";
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.startsWith("https://")) {
            url = "wss://" + url.substring(8);
        } else if (url.startsWith("http://")) {
            url = "ws://" + url.substring(7);
        }
        return url + "/api/snapshot/stream";
    }
}
