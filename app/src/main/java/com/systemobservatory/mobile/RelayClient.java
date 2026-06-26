package com.systemobservatory.mobile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class RelayClient {
    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static final int READ_TIMEOUT_MS = 8000;

    public SnapshotDto fetchLatest(String relayBaseUrl, String deviceKey) throws Exception {
        URL url = new URL(normalizeBaseUrl(relayBaseUrl) + "/api/snapshot/latest");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Device-Key", deviceKey == null ? "" : deviceKey.trim());

        int code = connection.getResponseCode();
        String body = readFully(code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream());
        connection.disconnect();

        if (code < 200 || code >= 300) {
            throw new IOException("Relay returned HTTP " + code + ": " + body);
        }
        return SnapshotDto.fromJson(body);
    }

    public static void postSnapshot(String relayBaseUrl, String deviceKey, String snapshotJson) throws IOException {
        URL url = new URL(normalizeBaseUrl(relayBaseUrl) + "/api/snapshot");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("X-Device-Key", deviceKey == null ? "" : deviceKey.trim());

        byte[] payload = snapshotJson.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(payload.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload);
        }

        int code = connection.getResponseCode();
        String body = readFully(code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream());
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("Relay returned HTTP " + code + ": " + body);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String readFully(InputStream input) throws IOException {
        if (input == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString().trim();
    }
}
