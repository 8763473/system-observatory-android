package com.systemobservatory.mobile;

import android.content.Context;
import android.content.Intent;
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
import java.io.FileOutputStream;
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
    private Callback callback;
    private String latestDownloadUrl;
    private boolean downloading;

    public interface Callback {
        void onUpdateAvailable(String newVersion, String body, String downloadUrl);
        void onNoUpdate();
        void onDownloadStart();
        void onDownloadProgress(int percent);
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
                conn.setRequestProperty("User-Agent", "SystemObservatory-Android");
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
                latestDownloadUrl = downloadUrl;
                post(() -> callback.onUpdateAvailable(finalTag, finalBody, finalUrl));
            } catch (Exception e) {
                Log.w(TAG, "Update check failed: " + e.getMessage());
                post(() -> callback.onNoUpdate());
            }
        }).start();
    }

    public void startDownload() {
        if (downloading) return;
        String url = latestDownloadUrl;
        if (url == null || url.isEmpty()) return;

        downloading = true;
        post(() -> callback.onDownloadStart());

        new Thread(() -> {
            File apkFile = null;
            try {
                File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_PATH);
                if (!dir.exists()) dir.mkdirs();
                apkFile = new File(dir, "系统观测台.apk");
                if (apkFile.exists()) apkFile.delete();

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("User-Agent", "SystemObservatory-Android");
                conn.connect();

                int total = conn.getContentLength();
                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(apkFile);
                byte[] buf = new byte[8192];
                int read;
                int downloaded = 0;
                int lastPercent = -1;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                    downloaded += read;
                    if (total > 0) {
                        int pct = downloaded * 100 / total;
                        if (pct != lastPercent) {
                            lastPercent = pct;
                            int finalPct = pct;
                            post(() -> callback.onDownloadProgress(finalPct));
                        }
                    }
                }
                out.close();
                in.close();
                conn.disconnect();

                if (apkFile.exists() && apkFile.length() > 0) {
                    File finalApk = apkFile;
                    post(() -> {
                        callback.onDownloadComplete(finalApk);
                        installApk(finalApk);
                    });
                } else {
                    post(() -> callback.onError("下载失败"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Download failed: " + e.getMessage());
                post(() -> callback.onError("下载失败: " + e.getMessage()));
                if (apkFile != null) apkFile.delete();
            }
            downloading = false;
        }).start();
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

    public void destroy() {
        callback = null;
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
