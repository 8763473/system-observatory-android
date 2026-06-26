package com.systemobservatory.mobile;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String PREF_SKIP_VERSION = "skip_version";
    private static final String UPDATE_PATH = "Updates";
    private static final String GITHUB_API = "https://api.github.com/repos/8763473/system-observatory-android/releases/latest";

    private final Context context;
    private final String currentVersion;
    private final SharedPreferences prefs;
    private long downloadId = -1;
    private Callback callback;

    public interface Callback {
        void onUpdateAvailable(String newVersion, String body);
        void onNoUpdate();
        void onDownloadComplete(File apkFile);
        void onError(String message);
    }

    public UpdateChecker(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.prefs = context.getSharedPreferences("update_checker", Context.MODE_PRIVATE);
        this.currentVersion = getVersionName();
    }

    private String getVersionName() {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName != null ? info.versionName : "0.0.0";
        } catch (PackageManager.NameNotFoundException e) {
            return "0.0.0";
        }
    }

    public void check() {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_API).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                int code = conn.getResponseCode();
                if (code != 200) {
                    post(() -> callback.onError("HTTP " + code));
                    return;
                }
                InputStream is = conn.getInputStream();
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[1024];
                int n;
                while ((n = is.read(buf)) != -1) sb.append(new String(buf, 0, n));
                is.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                String tagName = json.optString("tag_name", "").replaceFirst("^v", "");
                String body = json.optString("body", "");
                JSONArray assets = json.optJSONArray("assets");
                String downloadUrl = null;
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.optString("browser_download_url", "");
                            break;
                        }
                    }
                }

                if (tagName.isEmpty() || downloadUrl == null) {
                    post(() -> callback.onNoUpdate());
                    return;
                }

                if (tagName.equals(currentVersion)) {
                    post(() -> callback.onNoUpdate());
                    return;
                }

                if (compareVersions(tagName, currentVersion) <= 0) {
                    post(() -> callback.onNoUpdate());
                    return;
                }

                if (tagName.equals(prefs.getString(PREF_SKIP_VERSION, ""))) {
                    post(() -> callback.onNoUpdate());
                    return;
                }

                String finalUrl = downloadUrl;
                String finalTag = tagName;
                String finalBody = body;
                post(() -> callback.onUpdateAvailable(finalTag, finalBody));
            } catch (Exception e) {
                Log.w(TAG, "Update check failed: " + e.getMessage());
                post(() -> callback.onNoUpdate());
            }
        }).start();
    }

    public void startDownload(String downloadUrl) {
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_PATH);
        if (!dir.exists()) dir.mkdirs();
        File apkFile = new File(dir, "系统观测台.apk");
        if (apkFile.exists()) apkFile.delete();

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setTitle("系统观测台更新");
        request.setDescription("正在下载新版本...");
        request.setDestinationUri(Uri.fromFile(apkFile));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);

        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        downloadId = manager.enqueue(request);

        context.registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    public void skipVersion(String version) {
        prefs.edit().putString(PREF_SKIP_VERSION, version).apply();
    }

    public void installApk(File apkFile) {
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".provider", apkFile);
        } else {
            uri = Uri.fromFile(apkFile);
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id != downloadId) return;
            context.unregisterReceiver(this);

            File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_PATH);
            File apkFile = new File(dir, "系统观测台.apk");
            if (apkFile.exists() && apkFile.length() > 0) {
                post(() -> callback.onDownloadComplete(apkFile));
            } else {
                post(() -> callback.onError("下载失败，请稍后重试"));
            }
        }
    };

    public void destroy() {
        try { context.unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
    }

    private void post(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }

    private static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? safeParseInt(pa[i]) : 0;
            int vb = i < pb.length ? safeParseInt(pb[i]) : 0;
            if (va > vb) return 1;
            if (va < vb) return -1;
        }
        return 0;
    }

    private static int safeParseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
