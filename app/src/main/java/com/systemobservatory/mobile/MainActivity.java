package com.systemobservatory.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String PREFS = "system_observatory_mobile";
    private static final String PREF_RELAY_URL = "relay_url";
    private static final String PREF_DEVICE_KEY = "device_key";
    private static final String PREF_USER_NAME = "user_name";
    private static final String PREF_AVATAR_FILE = "avatar_file";
    private static final long REFRESH_FAST_MS = 3000;
    private static final long REFRESH_IDLE_MS = 30000;
    private static final long HTTP_FALLBACK_INTERVAL_MS = 5000;
    private static final int PAGE_BACKGROUND = Color.rgb(248, 250, 252);
    private static final int SURFACE = Color.rgb(255, 255, 255);
    private static final int SURFACE_TINT = Color.rgb(243, 245, 250);
    private static final int SURFACE_ACCENT = Color.rgb(232, 234, 246);
    private static final int PRIMARY = Color.rgb(63, 81, 181);
    private static final int PRIMARY_SOFT = Color.rgb(159, 168, 218);
    private static final int PRIMARY_DEEP = Color.rgb(48, 63, 159);
    private static final int TEXT_PRIMARY = Color.rgb(26, 28, 35);
    private static final int TEXT_MUTED = Color.rgb(90, 95, 105);
    private static final int TEXT_SOFT = Color.rgb(140, 145, 155);
    private static final int DIVIDER = Color.rgb(224, 228, 240);
    private static final int WARNING = Color.rgb(191, 111, 0);
    private static final int SUCCESS = Color.rgb(0, 150, 136);
    private static final int AVATAR_REQUEST = 9001;
    private static final int CROP_REQUEST = 9002;

    private static final String[] QUOTES = {
        "我追索人心的深度，却看到了人心的浅薄。 ——《云雀叫了一整天》（木心）",
        "人生如逆旅，我亦是行人。 ——《临江仙》（苏轼）",
        "世界以痛吻我，要我报之以歌。 ——《飞鸟集》（泰戈尔）",
        "凡是过去，皆为序章。 ——《暴风雨》（莎士比亚）",
        "一个人的行走范围，就是他的世界。 ——《青灯》（北岛）",
        "生活的最佳状态是冷冷清清的风风火火。 ——木心",
        "只有用心灵才能看清，本质的东西眼睛是看不见的。 ——《小王子》",
        "要有最朴素的生活与最遥远的梦想。 ——《被窝是青春的坟墓》（七堇年）",
        "云山苍苍，江水泱泱。先生之风，山高水长。 ——范仲淹",
        "你来人间一趟，你要看看太阳。 ——《夏天的太阳》（海子）",
        "万物皆有裂痕，那是光照进来的地方。 ——莱昂纳德·科恩",
        "满地都是六便士，他却抬头看见了月亮。 ——《月亮与六便士》",
        "我曾踏足山巅，也曾进入低谷，二者都让我受益良多。 ——《英雄联盟》",
        "活在这珍贵的人间，太阳强烈，水波温柔。 ——《活在珍贵的人间》（海子）",
        "山重水复疑无路，柳暗花明又一村。 ——《游山西村》（陆游）",
        "夜色难免黑凉，前行必有曙光。 ——《人民日报》",
        "山河远阔，人间烟火。无一是你，无一不是你。 ——《江海共余生》",
        "日出之美在于它脱胎于最深的黑暗。 ——《日出》",
        "鲸落海底，哺暗界众生十五年。 ——加里·斯奈德",
        "所有随风而逝的都属于昨天，所有历经风雨的才是面向未来。 ——《飘》",
    };

    private enum MobileScreen {
        OVERVIEW,
        TOKEN,
        SETTINGS
    }

    private enum TokenHeatmapMode {
        DAILY,
        WEEKLY
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RelayClient relayClient = new RelayClient();
    private final RelayWebSocketClient wsClient = new RelayWebSocketClient();
    private ExecutorService executor;
    private boolean resumed;
    private boolean loading;
    private boolean wsConnected;
    private UpdateChecker updateChecker;
    private MobileScreen currentScreen = MobileScreen.OVERVIEW;
    private TokenHeatmapMode tokenHeatmapMode = TokenHeatmapMode.DAILY;
    private String relayUrl = "";
    private String deviceKey = "";
    private String userName = "";
    private String avatarFilePath = "";
    private static long quoteUpdatedAt;
    private static String lastQuote = "";
    private SnapshotDto snapshot = SnapshotDto.empty();
    private long lastFetchMillis;
    private String connectionStatus = "等待设置";
    private final Random random = new Random();

    private TextView relayText;
    private TextView deviceStatusText;
    private TextView lastUpdateText;
    private TextView computerNameText;
    private RoundAvatarView avatarView;
    private TextView healthTitleText;
    private TextView healthDetailText;
    private TextView tokenStatusText;
    private TextView tokenTodayText;
    private TextView tokenTotalText;
    private TextView tokenModelText;
    private TextView tokenSpeedText;
    private TextView tokenRoundText;
    private TextView tokenStreakText;
    private TokenHeatmapView tokenHeatmapView;
    private Button tokenDailyButton;
    private Button tokenWeeklyButton;
    private MetricRow cpuRow;
    private MetricRow memoryRow;
    private MetricRow gpuRow;
    private MetricRow diskRow;
    private MetricRow networkRow;
    private TextView settingsRelayValue;
    private TextView settingsKeyValue;
    private EditText settingsRelayInput;
    private EditText settingsKeyInput;
    private EditText settingsNameInput;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchSnapshot();
        }
    };

    private final Runnable httpFallbackRunnable = new Runnable() {
        @Override
        public void run() {
            if (!resumed) return;
            if (wsConnected) {
                handler.postDelayed(this, HTTP_FALLBACK_INTERVAL_MS);
                return;
            }
            fetchSnapshot();
            handler.postDelayed(this, HTTP_FALLBACK_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindowForSoftTheme();
        executor = Executors.newSingleThreadExecutor();
        loadConnection();
        checkForUpdates(false);
        navigateTo(MobileScreen.OVERVIEW);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        connectWebSocket();
        handler.postDelayed(httpFallbackRunnable, HTTP_FALLBACK_INTERVAL_MS);
        fetchSnapshot();
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refreshRunnable);
        handler.removeCallbacks(httpFallbackRunnable);
        disconnectWebSocket();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshRunnable);
        handler.removeCallbacks(httpFallbackRunnable);
        disconnectWebSocket();
        if (updateChecker != null) updateChecker.destroy();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void configureWindowForSoftTheme() {
        Window window = getWindow();
        window.setStatusBarColor(PAGE_BACKGROUND);
        window.setNavigationBarColor(PAGE_BACKGROUND);

        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void applySafeArea(FrameLayout root) {
        root.setBackgroundColor(PAGE_BACKGROUND);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int bottomInset = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets.getDisplayCutout() != null) {
                topInset = Math.max(topInset, insets.getDisplayCutout().getSafeInsetTop());
            }
            view.setPadding(0, Math.max(topInset, dp(6)), 0, Math.max(bottomInset, dp(8)));
            return insets;
        });
        root.post(() -> root.requestApplyInsets());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (currentScreen != MobileScreen.OVERVIEW) {
            navigateTo(MobileScreen.OVERVIEW);
            return;
        }
        super.onBackPressed();
    }

    private void navigateTo(MobileScreen screen) {
        currentScreen = screen;
        if (screen == MobileScreen.TOKEN) {
            setContentView(buildTokenView());
        } else if (screen == MobileScreen.SETTINGS) {
            setContentView(buildSettingsView());
        } else {
            setContentView(buildQuietOverviewView());
        }
        updateUi();
    }

    private void clearScreenRefs() {
        relayText = null;
        deviceStatusText = null;
        lastUpdateText = null;
        computerNameText = null;
        avatarView = null;
        healthTitleText = null;
        healthDetailText = null;
        tokenStatusText = null;
        tokenTodayText = null;
        tokenTotalText = null;
        tokenModelText = null;
        tokenSpeedText = null;
        tokenRoundText = null;
        tokenStreakText = null;
        tokenHeatmapView = null;
        tokenDailyButton = null;
        tokenWeeklyButton = null;
        cpuRow = null;
        memoryRow = null;
        gpuRow = null;
        diskRow = null;
        networkRow = null;
        settingsRelayValue = null;
        settingsKeyValue = null;
        settingsRelayInput = null;
        settingsKeyInput = null;
        settingsNameInput = null;
    }

    private View buildQuietOverviewView() {
        clearScreenRefs();
        FrameLayout root = new FrameLayout(this);
        applySafeArea(root);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView);

        LinearLayout hero = card(SURFACE_ACCENT, dp(14));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleCopy = new LinearLayout(this);
        titleCopy.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("REMOTE OBSERVATORY", 11, PRIMARY_DEEP, Typeface.BOLD);
        TextView title = text("系统观测台", 26, TEXT_PRIMARY, Typeface.BOLD);
        computerNameText = text("等待电脑端上传快照", 14, TEXT_MUTED, Typeface.NORMAL);
        computerNameText.setPadding(0, dp(4), 0, 0);
        titleCopy.addView(eyebrow);
        titleCopy.addView(title);
        titleCopy.addView(computerNameText);
        titleRow.addView(titleCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button settingsButton = new Button(this);
        settingsButton.setText("设置");
        settingsButton.setAllCaps(false);
        settingsButton.setTextColor(Color.WHITE);
        settingsButton.setBackground(rounded(PRIMARY, dp(12)));
        settingsButton.setOnClickListener(v -> navigateTo(MobileScreen.SETTINGS));
        titleRow.addView(settingsButton, new LinearLayout.LayoutParams(dp(76), dp(40)));
        hero.addView(titleRow);
        hero.addView(spacer(14));

        LinearLayout statusStrip = new LinearLayout(this);
        statusStrip.setOrientation(LinearLayout.HORIZONTAL);
        statusStrip.setGravity(Gravity.CENTER_VERTICAL);
        relayText = statusCell(statusStrip, "公网地址", "未设置", TEXT_SOFT, TEXT_PRIMARY);
        deviceStatusText = statusCell(statusStrip, "设备状态", "未连接", TEXT_SOFT, TEXT_PRIMARY);
        lastUpdateText = statusCell(statusStrip, "最后更新", "--", TEXT_SOFT, TEXT_PRIMARY);
        hero.addView(statusStrip);
        content.addView(hero);
        content.addView(spacer(14));

        LinearLayout healthCard = card(SURFACE, dp(16));
        LinearLayout healthRow = new LinearLayout(this);
        healthRow.setGravity(Gravity.CENTER_VERTICAL);
        avatarView = new RoundAvatarView(this);
        avatarView.setOnClickListener(v -> pickAvatar());
        if (avatarFilePath != null && !avatarFilePath.isEmpty()) {
            avatarView.setImageFile(avatarFilePath);
        }
        healthRow.addView(avatarView, new LinearLayout.LayoutParams(dp(100), dp(100)));
        LinearLayout healthCopy = new LinearLayout(this);
        healthCopy.setOrientation(LinearLayout.VERTICAL);
        healthCopy.setPadding(dp(18), 0, 0, 0);
        healthTitleText = text(getGreeting(), 20, TEXT_PRIMARY, Typeface.BOLD);
        healthDetailText = text(getRandomQuote(), 14, TEXT_MUTED, Typeface.NORMAL);
        healthDetailText.setPadding(0, dp(8), 0, 0);
        healthDetailText.setMaxLines(3);
        healthCopy.addView(healthTitleText);
        healthCopy.addView(healthDetailText);
        healthRow.addView(healthCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        healthCard.addView(healthRow);
        content.addView(healthCard);
        content.addView(spacer(14));

        content.addView(navCard("Token 查询", "热力图与用量明细", SURFACE_ACCENT, TEXT_PRIMARY, v -> navigateTo(MobileScreen.TOKEN)));
        content.addView(spacer(14));

        LinearLayout metricPanel = card(SURFACE, dp(16));
        metricPanel.addView(sectionTitle("硬件指标"));
        cpuRow = new MetricRow(this, "CPU");
        memoryRow = new MetricRow(this, "内存");
        gpuRow = new MetricRow(this, "显卡");
        diskRow = new MetricRow(this, "磁盘");
        networkRow = new MetricRow(this, "网络");
        metricPanel.addView(cpuRow);
        metricPanel.addView(divider());
        metricPanel.addView(memoryRow);
        metricPanel.addView(divider());
        metricPanel.addView(gpuRow);
        metricPanel.addView(divider());
        metricPanel.addView(diskRow);
        metricPanel.addView(divider());
        metricPanel.addView(networkRow);
        content.addView(metricPanel);

        return root;
    }

    private View buildTokenView() {
        clearScreenRefs();
        FrameLayout root = new FrameLayout(this);
        applySafeArea(root);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView);

        LinearLayout hero = card(SURFACE_ACCENT, dp(14));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button backButton = pageButton("返回", SURFACE_TINT, TEXT_PRIMARY);
        backButton.setOnClickListener(v -> navigateTo(MobileScreen.OVERVIEW));
        header.addView(backButton, new LinearLayout.LayoutParams(dp(72), dp(40)));
        LinearLayout titleCopy = new LinearLayout(this);
        titleCopy.setOrientation(LinearLayout.VERTICAL);
        titleCopy.setPadding(dp(12), 0, 0, 0);
        titleCopy.addView(text("Token 查询", 25, TEXT_PRIMARY, Typeface.BOLD));
        tokenStatusText = text("等待电脑端 Token 数据", 14, TEXT_MUTED, Typeface.NORMAL);
        tokenStatusText.setPadding(0, dp(4), 0, 0);
        titleCopy.addView(tokenStatusText);
        header.addView(titleCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button refreshButton = pageButton("刷新", PRIMARY, Color.WHITE);
        refreshButton.setOnClickListener(v -> fetchSnapshot());
        header.addView(refreshButton, new LinearLayout.LayoutParams(dp(72), dp(40)));
        hero.addView(header);

        LinearLayout tokenStats = new LinearLayout(this);
        tokenStats.setOrientation(LinearLayout.HORIZONTAL);
        tokenStats.setPadding(0, dp(12), 0, 0);
        tokenTodayText = tokenStat(tokenStats, "今日", "--");
        tokenTotalText = tokenStat(tokenStats, "累计", "--");
        tokenSpeedText = tokenStat(tokenStats, "速度", "--");
        hero.addView(tokenStats);
        content.addView(hero);
        content.addView(spacer(14));

        LinearLayout heatmapCard = card(SURFACE, dp(16));
        LinearLayout heatmapHead = new LinearLayout(this);
        heatmapHead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heatmapTitle = new LinearLayout(this);
        heatmapTitle.setOrientation(LinearLayout.VERTICAL);
        heatmapTitle.addView(text("Token 热力图", 20, TEXT_PRIMARY, Typeface.BOLD));
        TextView heatmapSub = text("按父项目同款 53 周网格显示活动强度", 14, TEXT_MUTED, Typeface.NORMAL);
        heatmapSub.setPadding(0, dp(4), 0, 0);
        heatmapTitle.addView(heatmapSub);
        heatmapHead.addView(heatmapTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tokenDailyButton = pageButton("日", Color.TRANSPARENT, TEXT_PRIMARY);
        tokenWeeklyButton = pageButton("周", Color.TRANSPARENT, TEXT_PRIMARY);
        tokenDailyButton.setOnClickListener(v -> setTokenHeatmapMode(TokenHeatmapMode.DAILY));
        tokenWeeklyButton.setOnClickListener(v -> setTokenHeatmapMode(TokenHeatmapMode.WEEKLY));
        tabs.addView(tokenDailyButton, new LinearLayout.LayoutParams(dp(52), dp(38)));
        tabs.addView(tokenWeeklyButton, new LinearLayout.LayoutParams(dp(52), dp(38)));
        heatmapHead.addView(tabs);
        heatmapCard.addView(heatmapHead);

        tokenHeatmapView = new TokenHeatmapView(this);
        LinearLayout.LayoutParams heatmapParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(184));
        heatmapParams.setMargins(0, dp(18), 0, dp(8));
        heatmapCard.addView(tokenHeatmapView, heatmapParams);
        content.addView(heatmapCard);
        content.addView(spacer(14));

        LinearLayout detailCard = card(SURFACE, dp(16));
        detailCard.addView(sectionTitle("查询明细"));
        tokenModelText = plainDetail(detailCard, "模型", "--");
        tokenRoundText = plainDetail(detailCard, "最近一轮", "--");
        tokenStreakText = plainDetail(detailCard, "连续记录", "--");
        content.addView(detailCard);

        return root;
    }

    private View buildSettingsView() {
        clearScreenRefs();
        FrameLayout root = new FrameLayout(this);
        applySafeArea(root);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView);

        LinearLayout hero = card(SURFACE, dp(14));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button backButton = pageButton("返回", SURFACE_TINT, TEXT_PRIMARY);
        backButton.setOnClickListener(v -> navigateTo(MobileScreen.OVERVIEW));
        header.addView(backButton, new LinearLayout.LayoutParams(dp(72), dp(40)));
        LinearLayout titleCopy = new LinearLayout(this);
        titleCopy.setOrientation(LinearLayout.VERTICAL);
        titleCopy.setPadding(dp(12), 0, 0, 0);
        titleCopy.addView(text("连接设置", 25, TEXT_PRIMARY, Typeface.BOLD));
        TextView subtitle = text("公网地址、设备密钥和 MSLFrp 入口", 14, TEXT_MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(4), 0, 0);
        titleCopy.addView(subtitle);
        header.addView(titleCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        hero.addView(header);
        hero.addView(spacer(12));
        LinearLayout statusStrip = new LinearLayout(this);
        statusStrip.setOrientation(LinearLayout.HORIZONTAL);
        relayText = statusCell(statusStrip, "公网地址", "未设置");
        deviceStatusText = statusCell(statusStrip, "状态", "未连接");
        lastUpdateText = statusCell(statusStrip, "更新", "--");
        hero.addView(statusStrip);
        content.addView(hero);
        content.addView(spacer(14));

        LinearLayout settingsCard = card(SURFACE, dp(16));
        settingsCard.addView(sectionTitle("当前配置"));
        settingsRelayInput = settingsInput("http://公网地址:端口", relayUrl, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        settingsKeyInput = settingsInput("设备密钥", deviceKey, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        EditText settingsNameInput = settingsInput("你的昵称", userName, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        this.settingsNameInput = settingsNameInput;
        settingsCard.addView(inputBlock("公网地址", settingsRelayInput));
        settingsCard.addView(spacer(10));
        settingsCard.addView(inputBlock("设备密钥", settingsKeyInput));
        settingsCard.addView(spacer(10));
        settingsCard.addView(inputBlock("你的昵称", settingsNameInput));
        TextView inlineHelp = text("Windows 父项目开启 Android 连接后，用 MSLFrp 暴露 127.0.0.1:8787；这里填写公网地址和 Windows 端显示的设备密钥。", 14, TEXT_MUTED, Typeface.NORMAL);
        inlineHelp.setPadding(0, dp(10), 0, dp(12));
        settingsCard.addView(inlineHelp);
        Button saveButton = pageButton("保存连接", PRIMARY, Color.WHITE);
        saveButton.setOnClickListener(v -> saveConnectionFromSettings());
        settingsCard.addView(saveButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        content.addView(settingsCard);
        content.addView(spacer(14));

        LinearLayout helpCard = card(SURFACE_ACCENT, dp(16));
        helpCard.addView(text("MSLFrp 接入", 20, TEXT_PRIMARY, Typeface.BOLD));
        TextView help = text("不再需要独立 Relay。Windows 父项目内置服务器监听 8787，手机不在同一网络时，用 MSLFrp 把这个端口映射到公网。", 15, TEXT_MUTED, Typeface.NORMAL);
        help.setPadding(0, dp(10), 0, dp(14));
        helpCard.addView(help);
        Button helpButton = pageButton("查看连接说明", PRIMARY, Color.WHITE);
        helpButton.setOnClickListener(v -> showHelpDialog());
        helpCard.addView(helpButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        content.addView(helpCard);
        content.addView(spacer(14));

        LinearLayout updateCard = card(SURFACE, dp(16));
        updateCard.addView(text("关于与更新", 20, TEXT_PRIMARY, Typeface.BOLD));
        String verName = "0.0.0";
        try { verName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        TextView versionText = text("当前版本 v" + verName, 15, TEXT_MUTED, Typeface.NORMAL);
        versionText.setPadding(0, dp(10), 0, dp(14));
        updateCard.addView(versionText);
        Button updateButton = pageButton("检查更新", PRIMARY, Color.WHITE);
        updateButton.setOnClickListener(v -> {
            checkForUpdates(true);
            showToast("正在检查更新...");
        });
        updateCard.addView(updateButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        content.addView(updateCard);

        return root;
    }

    private TextView statusCell(LinearLayout parent, String label, String value) {
        return statusCell(parent, label, value, TEXT_MUTED, TEXT_PRIMARY);
    }

    private TextView statusCell(LinearLayout parent, String label, String value, int labelColor, int valueColor) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(4), 0, dp(8), 0);
        TextView labelView = text(label, 13, labelColor, Typeface.NORMAL);
        TextView valueView = text(value, 16, valueColor, Typeface.BOLD);
        valueView.setPadding(0, dp(6), 0, 0);
        cell.addView(labelView);
        cell.addView(valueView);
        parent.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return valueView;
    }

    private LinearLayout navCard(String title, String subtitle, int background, int titleColor, View.OnClickListener listener) {
        LinearLayout item = card(background, dp(16));
        item.setOnClickListener(listener);
        TextView titleView = text(title, 18, titleColor, Typeface.BOLD);
        TextView subtitleView = text(subtitle, 13, TEXT_MUTED, Typeface.NORMAL);
        subtitleView.setPadding(0, dp(8), 0, 0);
        item.addView(titleView);
        item.addView(subtitleView);
        return item;
    }

    private Button pageButton(String label, int background, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(color);
        button.setBackground(rounded(background, dp(12)));
        return button;
    }

    private TextView plainDetail(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        TextView labelView = text(label, 15, TEXT_MUTED, Typeface.NORMAL);
        TextView valueView = text(value, 16, TEXT_PRIMARY, Typeface.BOLD);
        valueView.setGravity(Gravity.END);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(valueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        parent.addView(row);
        parent.addView(divider());
        return valueView;
    }

    private void setTokenHeatmapMode(TokenHeatmapMode mode) {
        tokenHeatmapMode = mode;
        updateTokenHeatmapModeButtons();
        if (tokenHeatmapView != null) {
            tokenHeatmapView.setMode(mode);
        }
    }

    private void updateTokenHeatmapModeButtons() {
        if (tokenDailyButton == null || tokenWeeklyButton == null) return;
        boolean daily = tokenHeatmapMode == TokenHeatmapMode.DAILY;
        tokenDailyButton.setTextColor(daily ? Color.WHITE : TEXT_PRIMARY);
        tokenWeeklyButton.setTextColor(!daily ? Color.WHITE : TEXT_PRIMARY);
        tokenDailyButton.setBackground(rounded(daily ? PRIMARY : SURFACE_TINT, dp(12)));
        tokenWeeklyButton.setBackground(rounded(!daily ? PRIMARY : SURFACE_TINT, dp(12)));
    }

    private TextView settingsRow(LinearLayout content, String label, String value, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(16), 0, dp(16));
        row.setOnClickListener(listener);
        TextView labelView = text(label, 17, TEXT_PRIMARY, Typeface.NORMAL);
        TextView valueView = text(value, 16, TEXT_MUTED, Typeface.NORMAL);
        valueView.setGravity(Gravity.END);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(valueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(row);
        content.addView(divider());
        return valueView;
    }

    private LinearLayout inputBlock(String label, EditText input) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        TextView labelView = text(label, 15, TEXT_MUTED, Typeface.NORMAL);
        labelView.setPadding(0, 0, 0, dp(6));
        block.addView(labelView);
        block.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return block;
    }

    private EditText settingsInput(String hint, String value, int inputType) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setTextSize(16);
        input.setTextColor(TEXT_PRIMARY);
        input.setHintTextColor(TEXT_SOFT);
        input.setInputType(inputType);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(rounded(SURFACE_TINT, dp(12)));
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setCursorVisible(true);
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                input.postDelayed(() -> showKeyboard(input), 80);
            }
        });
        input.setOnClickListener(view -> showKeyboard(input));
        input.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                input.requestFocus();
                input.post(() -> showKeyboard(input));
            }
            return false;
        });
        return input;
    }

    private void showKeyboard(EditText input) {
        input.requestFocus();
        input.setSelection(input.getText().length());
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private TextView tokenStat(LinearLayout parent, String label, String value) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(12), dp(10), dp(12), dp(10));
        cell.setBackground(rounded(SURFACE_TINT, dp(12)));
        TextView labelView = text(label, 13, TEXT_SOFT, Typeface.NORMAL);
        TextView valueView = text(value, 20, TEXT_PRIMARY, Typeface.BOLD);
        valueView.setPadding(0, dp(6), 0, 0);
        cell.addView(labelView);
        cell.addView(valueView);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        parent.addView(cell, params);
        return valueView;
    }

    private TextView tokenDetail(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        TextView labelView = text(label, 14, TEXT_SOFT, Typeface.NORMAL);
        TextView valueView = text(value, 15, TEXT_PRIMARY, Typeface.NORMAL);
        valueView.setGravity(Gravity.END);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(valueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        parent.addView(row);
        return valueView;
    }

    private LinearLayout card(int color, int padding) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(padding, padding, padding, padding);
        card.setBackground(rounded(color, dp(18)));
        return card;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void fetchSnapshot() {
        handler.removeCallbacks(refreshRunnable);
        if (!resumed || loading) return;
        if (relayUrl.trim().isEmpty()) {
            connectionStatus = "等待设置";
            updateUi();
            scheduleNext(REFRESH_IDLE_MS);
            return;
        }

        loading = true;
        connectionStatus = "同步中";
        updateUi();
        executor.execute(() -> {
            try {
                SnapshotDto latest = relayClient.fetchLatest(relayUrl, deviceKey);
                handler.post(() -> {
                    snapshot = latest;
                    lastFetchMillis = System.currentTimeMillis();
                    connectionStatus = "在线";
                    loading = false;
                    updateUi();
                    scheduleNext(REFRESH_FAST_MS);
                });
            } catch (Exception ex) {
                handler.post(() -> {
                    connectionStatus = "连接失败";
                    loading = false;
                    updateUi();
                    scheduleNext(REFRESH_IDLE_MS);
                });
            }
        });
    }

    private void scheduleNext(long delayMillis) {
        if (resumed) handler.postDelayed(refreshRunnable, delayMillis);
    }

    private void connectWebSocket() {
        if (relayUrl.trim().isEmpty() || deviceKey.trim().isEmpty()) return;
        wsClient.connect(relayUrl, deviceKey, new RelayWebSocketClient.Listener() {
            @Override
            public void onOpen() {
                wsConnected = true;
                connectionStatus = "实时连接";
                updateUi();
            }

            @Override
            public void onMessage(String json) {
                try {
                    SnapshotDto latest = SnapshotDto.fromJson(json);
                    snapshot = latest;
                    lastFetchMillis = System.currentTimeMillis();
                    connectionStatus = "实时在线";
                    loading = false;
                    updateUi();
                } catch (Exception e) {
                    // 解析失败，忽略此消息
                }
            }

            @Override
            public void onClose(int code, String reason) {
                wsConnected = false;
                if (resumed) {
                    connectionStatus = "重连中";
                    updateUi();
                }
            }

            @Override
            public void onError(String message) {
                wsConnected = false;
                if (resumed && !relayUrl.trim().isEmpty()) {
                    connectionStatus = "连接失败";
                    updateUi();
                }
            }
        });
    }

    private void disconnectWebSocket() {
        wsClient.disconnect();
        wsConnected = false;
    }

    private void updateUi() {
        if (relayText != null) {
            relayText.setText(relayUrl.trim().isEmpty() ? "未设置" : compactRelay(relayUrl));
        }
        if (deviceStatusText != null) {
            deviceStatusText.setText(connectionStatus);
        }
        if (lastUpdateText != null) {
            lastUpdateText.setText(lastFetchMillis == 0 ? "--" : relativeAge(lastFetchMillis));
        }
        if (computerNameText != null) {
            computerNameText.setText(snapshot.diannaomingcheng == null || snapshot.diannaomingcheng.trim().isEmpty()
                    ? "等待电脑端上传快照"
                    : snapshot.diannaomingcheng + " · " + snapshot.zhimingcheng);
        }
        if (settingsRelayValue != null) {
            settingsRelayValue.setText(relayUrl.trim().isEmpty() ? "未设置" : compactRelay(relayUrl));
        }
        if (settingsKeyValue != null) {
            settingsKeyValue.setText(deviceKey.trim().isEmpty() ? "未设置" : "••••••••••••••••");
        }
        if (settingsRelayInput != null && !settingsRelayInput.hasFocus()) {
            settingsRelayInput.setText(relayUrl);
        }
        if (settingsKeyInput != null && !settingsKeyInput.hasFocus()) {
            settingsKeyInput.setText(deviceKey);
        }

        if (healthTitleText != null) {
            healthTitleText.setText(getGreeting());
        }
        if (healthDetailText != null) {
            healthDetailText.setText(getRandomQuote());
        }
        if (tokenStatusText != null) {
            bindTokenUi(snapshot.tokenjiankong);
        }

        SnapshotDto.GpuInfo gpu = snapshot.primaryGpu();
        SnapshotDto.DiskInfo disk = snapshot.primaryDisk();
        SnapshotDto.NetInfo net = snapshot.primaryNetwork();

        if (cpuRow != null) {
            cpuRow.bind(snapshot.chuliqi3.xinghao2,
                    pct(snapshot.chuliqi3.shiyonglvbaifenbi),
                    cpuDetail(snapshot.chuliqi3),
                    snapshot.chuliqi3.shiyonglvbaifenbi,
                    "",
                    PRIMARY);
        }

        if (memoryRow != null) {
            memoryRow.bind(memoryTotal(snapshot.neicun),
                    pct(snapshot.neicun.shiyonglvbaifenbi2),
                    memoryDetail(snapshot.neicun),
                    snapshot.neicun.shiyonglvbaifenbi2,
                    "",
                    PRIMARY);
        }

        if (gpuRow != null) {
            gpuRow.bind(gpu == null || gpu.xinghao3.isEmpty() ? "未检测到显卡" : gpu.xinghao3,
                    pct(gpu == null ? 0 : gpu.shiyonglvbaifenbi4),
                    gpuDetail(gpu),
                    gpu == null ? 0 : gpu.shiyonglvbaifenbi4,
                    "",
                    PRIMARY);
        }

        if (diskRow != null) {
            String diskWarning = diskWarning(disk);
            diskRow.bind(disk == null || disk.cipanmingcheng.isEmpty() ? "未检测到磁盘" : disk.cipanmingcheng,
                    pct(disk == null ? 0 : disk.shiyonglvbaifenbi3),
                    diskDetail(disk),
                    disk == null ? 0 : disk.shiyonglvbaifenbi3,
                    diskWarning,
                    diskWarning.isEmpty() ? PRIMARY : WARNING);
        }

        if (networkRow != null) {
            networkRow.bind(net == null || net.mingcheng10.isEmpty() ? "未检测到网络" : net.mingcheng10,
                    net == null ? "--" : networkSpeed(net),
                    net == null ? "--" : "下载 / 上传",
                    0,
                    "",
                    SUCCESS);
        }
    }

    private void bindTokenUi(SnapshotDto.TokenInfo token) {
        updateTokenHeatmapModeButtons();
        if (token == null || !token.hasData()) {
            tokenStatusText.setText("等待电脑端 Token 数据");
            tokenTodayText.setText("--");
            tokenTotalText.setText("--");
            tokenSpeedText.setText("--");
            tokenModelText.setText("--");
            tokenRoundText.setText("电脑端上传快照后自动同步");
            tokenStreakText.setText("--");
            if (tokenHeatmapView != null) {
                tokenHeatmapView.setDays(null);
                tokenHeatmapView.setMode(tokenHeatmapMode);
            }
            return;
        }

        String status = token.yunxing || token.houtaicaijiqiyunxing
                ? "采集中 · " + emptyFallback(token.zhuangtai, "Token 数据在线")
                : emptyFallback(token.zhuangtai, "已同步历史统计");
        tokenStatusText.setText(status);
        tokenTodayText.setText(tokenAmount(token.jinritokens));
        tokenTotalText.setText(tokenAmount(token.leijitokens));
        tokenSpeedText.setText(tokenSpeed(token.zhunquesudu > 0 ? token.zhunquesudu : token.shishitokensudu));
        tokenModelText.setText(emptyFallback(token.moxingmingcheng, "未知模型"));
        tokenRoundText.setText("输入 " + tokenAmount(token.shurutokens)
                + " / 输出 " + tokenAmount(token.shuchutokens)
                + " / 耗时 " + durationText(token.zonghaoshi));
        tokenStreakText.setText("当前 " + token.dangqianlianxutianshu
                + " 天，最长 " + token.zuichanglianxutianshu + " 天");
        if (tokenHeatmapView != null) {
            tokenHeatmapView.setDays(token.huodongriqi);
            tokenHeatmapView.setMode(tokenHeatmapMode);
        }
    }

    private void saveConnectionFromSettings() {
        String nextRelayUrl = settingsRelayInput == null ? relayUrl : settingsRelayInput.getText().toString();
        String nextDeviceKey = settingsKeyInput == null ? deviceKey : settingsKeyInput.getText().toString();
        String nextUserName = settingsNameInput == null ? userName : settingsNameInput.getText().toString();
        saveConnection(nextRelayUrl, nextDeviceKey);
        saveUserName(nextUserName);
        showToast("连接配置已保存");
        fetchSnapshot();
    }

    private String getGreeting() {
        String name = userName == null || userName.trim().isEmpty() ? "用户" : userName.trim();
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String prefix;
        if (hour >= 5 && hour < 8) {
            prefix = "早上好";
        } else if (hour >= 8 && hour < 11) {
            prefix = "上午好";
        } else if (hour >= 11 && hour < 13) {
            prefix = "中午好";
        } else if (hour >= 13 && hour < 18) {
            prefix = "下午好";
        } else if (hour >= 18 && hour < 22) {
            prefix = "晚上好";
        } else {
            prefix = "夜深了";
        }
        return prefix + ", " + name;
    }

    private String getRandomQuote() {
        long now = System.currentTimeMillis();
        if (lastQuote.isEmpty() || (now - quoteUpdatedAt) > 3600_000L) {
            lastQuote = QUOTES[random.nextInt(QUOTES.length)];
            quoteUpdatedAt = now;
        }
        return lastQuote;
    }

    private void pickAvatar() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, AVATAR_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AVATAR_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri sourceUri = data.getData();
            try {
                Intent cropIntent = new Intent("com.android.camera.action.CROP");
                cropIntent.setDataAndType(sourceUri, "image/*");
                cropIntent.putExtra("crop", "true");
                cropIntent.putExtra("aspectX", 1);
                cropIntent.putExtra("aspectY", 1);
                cropIntent.putExtra("outputX", 400);
                cropIntent.putExtra("outputY", 400);
                cropIntent.putExtra("return-data", true);
                startActivityForResult(cropIntent, CROP_REQUEST);
            } catch (Exception e) {
                saveBitmapFromUri(sourceUri);
            }
        } else if (requestCode == CROP_REQUEST && resultCode == RESULT_OK && data != null) {
            Bitmap cropped = data.getParcelableExtra("data");
            if (cropped != null) {
                saveBitmapToAvatar(cropped);
            }
        }
    }

    private void saveBitmapFromUri(Uri uri) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            if (input == null) return;
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            input.close();
            if (bitmap != null) saveBitmapToAvatar(bitmap);
        } catch (Exception e) {
            showToast("头像保存失败");
        }
    }

    private void saveBitmapToAvatar(Bitmap bitmap) {
        try {
            File avatarFile = new File(getFilesDir(), "avatar.jpg");
            FileOutputStream output = new FileOutputStream(avatarFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output);
            output.close();
            saveAvatarPath(avatarFile.getAbsolutePath());
            showToast("头像已更新");
        } catch (Exception e) {
            showToast("头像保存失败");
        }
    }

    private void showHelpDialog() {
        showToast("Windows 端开启 Android 连接，MSLFrp 映射 127.0.0.1:8787，手机填写公网地址和设备密钥。");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void loadConnection() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        relayUrl = prefs.getString(PREF_RELAY_URL, "");
        deviceKey = prefs.getString(PREF_DEVICE_KEY, "");
        userName = prefs.getString(PREF_USER_NAME, "");
        avatarFilePath = prefs.getString(PREF_AVATAR_FILE, "");
    }

    private void saveUserName(String name) {
        userName = name == null ? "" : name.trim();
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(PREF_USER_NAME, userName)
                .apply();
        updateUi();
    }

    private void saveAvatarPath(String path) {
        avatarFilePath = path == null ? "" : path;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(PREF_AVATAR_FILE, avatarFilePath)
                .apply();
        if (avatarView != null) {
            avatarView.setImageFile(path);
        }
    }

    private void saveConnection(String nextRelayUrl, String nextDeviceKey) {
        relayUrl = nextRelayUrl == null ? "" : nextRelayUrl.trim();
        deviceKey = nextDeviceKey == null ? "" : nextDeviceKey.trim();
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(PREF_RELAY_URL, relayUrl)
                .putString(PREF_DEVICE_KEY, deviceKey)
                .apply();
        disconnectWebSocket();
        if (resumed) {
            connectWebSocket();
        }
        updateUi();
    }

    private void checkForUpdates(boolean manualCheck) {
        updateChecker = new UpdateChecker(this, new UpdateChecker.Callback() {
            @Override
            public void onUpdateAvailable(String newVersion, String body, String downloadUrl) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("发现新版本 v" + newVersion)
                        .setMessage(body != null && !body.isEmpty()
                                ? body.substring(0, Math.min(body.length(), 200))
                                : "是否更新到最新版本？")
                        .setPositiveButton("立即更新", (dialog, which) -> {
                            dialog.dismiss();
                            updateChecker.startDownload();
                        })
                        .setNegativeButton("跳过此版本", (dialog, which) ->
                                updateChecker.skipVersion(newVersion))
                        .setNeutralButton("稍后", null)
                        .show();
            }

            @Override
            public void onNoUpdate() {
                if (manualCheck) {
                    try {
                        String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                        showToast("已是最新版本 v" + v);
                    } catch (Exception e) {
                        showToast("已是最新版本");
                    }
                }
            }

            @Override
            public void onDownloadStart() {
                showToast("正在下载更新...");
            }

            @Override
            public void onDownloadProgress(int percent) { }

            @Override
            public void onDownloadComplete(File apkFile) {
                showToast("下载完成，正在安装...");
            }

            @Override
            public void onError(String message) {
                showToast("更新失败: " + message);
            }
        });
        updateChecker.check();
    }

    private TextView sectionTitle(String text) {
        TextView view = text(text, 17, TEXT_MUTED, Typeface.NORMAL);
        view.setPadding(0, 0, 0, dp(6));
        return view;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(true);
        return view;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(DIVIDER);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return view;
    }

    private View spacer(int dpValue) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dpValue)));
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String pct(double value) {
        return String.format(Locale.CHINA, "%.0f%%", Math.max(0, value));
    }

    private static String compactRelay(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() <= 24) return trimmed;
        return trimmed.substring(0, 21) + "...";
    }

    private static String relativeAge(long millis) {
        long seconds = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
        if (seconds < 60) return seconds + " 秒前";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " 分钟前";
        return (minutes / 60) + " 小时前";
    }

    private static String cpuDetail(SnapshotDto.CpuInfo cpu) {
        if (cpu.zhipinlv <= 0) return "--";
        return String.format(Locale.CHINA, "%.1f GHz", cpu.zhipinlv / 1000.0);
    }

    private static String memoryTotal(SnapshotDto.MemInfo memory) {
        if (memory.zongliangzhi3 <= 0) return "未检测到内存";
        return String.format(Locale.CHINA, "%.0f GB", kbToGb(memory.zongliangzhi3));
    }

    private static String memoryDetail(SnapshotDto.MemInfo memory) {
        if (memory.zongliangzhi3 <= 0) return "--";
        return String.format(Locale.CHINA, "%.1f GB / %.0f GB",
                kbToGb(memory.yiyongzhi),
                kbToGb(memory.zongliangzhi3));
    }

    private static String gpuDetail(SnapshotDto.GpuInfo gpu) {
        if (gpu == null) return "--";
        String temp = gpu.wenduzhi2 >= 0 ? String.format(Locale.CHINA, "%.0f °C", gpu.wenduzhi2) : "--";
        String fan = gpu.fengshanbaifenbi >= 0 ? String.format(Locale.CHINA, "风扇 %.0f%%", gpu.fengshanbaifenbi) : "风扇 --";
        return temp + " / " + fan;
    }

    private static String diskDetail(SnapshotDto.DiskInfo disk) {
        if (disk == null || disk.zongliangzhi4 <= 0) return "--";
        return String.format(Locale.CHINA, "%.0f GB / %.0f GB",
                kbToGb(disk.yiyongzhi2),
                kbToGb(disk.zongliangzhi4));
    }

    private static String diskWarning(SnapshotDto.DiskInfo disk) {
        if (disk == null || disk.zongliangzhi4 <= 0) return "";
        double freeRatio = disk.shengyuzhi2 * 100.0 / disk.zongliangzhi4;
        return freeRatio < 15 ? "可用空间较低" : "";
    }

    private static String networkSpeed(SnapshotDto.NetInfo net) {
        return String.format(Locale.CHINA, "↓ %s  |  ↑ %s",
                bytesPerSecond(net.xiazaisudu2),
                bytesPerSecond(net.shangchuansudu2));
    }

    private static String tokenAmount(int value) {
        int safe = Math.max(0, value);
        if (safe >= 100_000_000) return String.format(Locale.CHINA, "%.1f亿", safe / 100_000_000.0);
        if (safe >= 10_000) return String.format(Locale.CHINA, "%.1f万", safe / 10_000.0);
        return String.valueOf(safe);
    }

    private static String tokenSpeed(double value) {
        if (!Double.isFinite(value) || value <= 0) return "--";
        return String.format(Locale.CHINA, "%.1f/s", value);
    }

    private static String durationText(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) return "--";
        if (seconds < 60) return String.format(Locale.CHINA, "%.1f 秒", seconds);
        double minutes = seconds / 60.0;
        if (minutes < 60) return String.format(Locale.CHINA, "%.1f 分钟", minutes);
        return String.format(Locale.CHINA, "%.1f 小时", minutes / 60.0);
    }

    private static String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String bytesPerSecond(long value) {
        double mbps = value * 8.0 / 1_000_000.0;
        if (mbps >= 1) return String.format(Locale.CHINA, "%.1f Mbps", mbps);
        return String.format(Locale.CHINA, "%.0f Kbps", value * 8.0 / 1000.0);
    }

    private static double kbToGb(long kb) {
        return kb / 1024.0 / 1024.0;
    }

    private final class MetricRow extends LinearLayout {
        private final TextView subtitle;
        private final TextView value;
        private final TextView detail;
        private final TextView warning;
        private final ProgressBar bar;

        MetricRow(Context context, String title) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(0, dp(18), 0, dp(18));

            LinearLayout left = new LinearLayout(context);
            left.setOrientation(VERTICAL);
            TextView titleView = text(title, 21, TEXT_PRIMARY, Typeface.BOLD);
            subtitle = text("", 15, TEXT_MUTED, Typeface.NORMAL);
            subtitle.setPadding(0, dp(4), 0, 0);
            warning = text("", 14, WARNING, Typeface.NORMAL);
            warning.setPadding(0, dp(4), 0, 0);
            left.addView(titleView);
            left.addView(subtitle);
            left.addView(warning);
            addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            LinearLayout right = new LinearLayout(context);
            right.setOrientation(VERTICAL);
            right.setGravity(Gravity.END);
            value = text("", 24, TEXT_PRIMARY, Typeface.BOLD);
            value.setGravity(Gravity.END);
            bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(118), dp(8));
            barParams.setMargins(0, dp(10), 0, dp(8));
            detail = text("", 15, TEXT_MUTED, Typeface.NORMAL);
            detail.setGravity(Gravity.END);
            right.addView(value);
            right.addView(bar, barParams);
            right.addView(detail);
            addView(right, new LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        void bind(String subtitleValue, String valueText, String detailText, double percent, String warningText, int accent) {
            subtitle.setText(subtitleValue);
            value.setText(valueText);
            detail.setText(detailText);
            warning.setText(warningText == null ? "" : warningText);
            warning.setVisibility(warningText == null || warningText.isEmpty() ? GONE : VISIBLE);
            bar.setProgress((int) Math.max(0, Math.min(100, percent)));
            bar.getProgressDrawable().setTint(accent);
        }
    }

    public final class TokenHeatmapView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<SnapshotDto.TokenDayInfo> days;
        private TokenHeatmapMode mode = TokenHeatmapMode.DAILY;

        public TokenHeatmapView(Context context) {
            super(context);
        }

        public void setDays(List<SnapshotDto.TokenDayInfo> nextDays) {
            days = nextDays;
            invalidate();
        }

        public void setMode(TokenHeatmapMode nextMode) {
            mode = nextMode;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (days == null || days.isEmpty()) {
                paint.setColor(TEXT_SOFT);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(dp(13));
                canvas.drawText("暂无 Token 活动热力图", getWidth() / 2f, getHeight() / 2f, paint);
                return;
            }

            int totalCells = 53 * 7;
            int count = Math.min(totalCells, days.size());
            int start = Math.max(0, days.size() - count);
            int[] values = new int[totalCells];
            for (int i = 0; i < count; i++) {
                values[totalCells - count + i] = Math.max(0, days.get(start + i).zongtokens);
            }

            int[] weekly = new int[53];
            int max = 1;
            for (int column = 0; column < 53; column++) {
                int sum = 0;
                for (int row = 0; row < 7; row++) {
                    int value = values[column * 7 + row];
                    sum += value;
                    if (mode == TokenHeatmapMode.DAILY) max = Math.max(max, value);
                }
                weekly[column] = sum;
                if (mode == TokenHeatmapMode.WEEKLY) max = Math.max(max, sum);
            }

            float gap = Math.max(2f, dp(2));
            float left = dp(4);
            float top = dp(12);
            float right = getWidth() - dp(4);
            float gridWidth = right - left;
            float cell = Math.min((gridWidth - gap * 52) / 53f, (getHeight() - dp(50) - gap * 6) / 7f);
            cell = Math.max(dp(3), cell);
            float gridHeight = cell * 7 + gap * 6;
            float gridTop = top;

            for (int column = 0; column < 53; column++) {
                for (int row = 0; row < 7; row++) {
                    int index = column * 7 + row;
                    int value = mode == TokenHeatmapMode.WEEKLY ? weekly[column] : values[index];
                    paint.setColor(heatColor(value, max));
                    float x = left + column * (cell + gap);
                    float y = gridTop + row * (cell + gap);
                    RectF rect = new RectF(x, y, x + cell, y + cell);
                    canvas.drawRoundRect(rect, Math.max(2f, cell / 3f), Math.max(2f, cell / 3f), paint);
                }
            }

            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(dp(11));
            paint.setColor(TEXT_MUTED);
            canvas.drawText("少", left, gridTop + gridHeight + dp(22), paint);

            float legendX = left + dp(24);
            float legendY = gridTop + gridHeight + dp(13);
            for (int i = 0; i < 5; i++) {
                paint.setColor(heatColor(i, 4));
                RectF rect = new RectF(legendX + i * (cell + gap), legendY, legendX + i * (cell + gap) + cell, legendY + cell);
                canvas.drawRoundRect(rect, Math.max(2f, cell / 3f), Math.max(2f, cell / 3f), paint);
            }

            paint.setColor(TEXT_MUTED);
            canvas.drawText("多", legendX + 5 * (cell + gap) + dp(4), gridTop + gridHeight + dp(22), paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(mode == TokenHeatmapMode.DAILY ? "每日 Token 活动" : "每周 Token 聚合",
                    getWidth() - dp(4),
                    gridTop + gridHeight + dp(22),
                    paint);
        }

        private int heatColor(int value, int max) {
            if (value <= 0) return Color.rgb(237, 240, 250);
            float ratio = Math.min(1f, value / (float) Math.max(1, max));
            if (ratio < 0.25f) return Color.rgb(197, 202, 233);
            if (ratio < 0.50f) return PRIMARY_SOFT;
            if (ratio < 0.75f) return PRIMARY;
            return PRIMARY_DEEP;
        }
    }

    public static final class RoundAvatarView extends ImageView {
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private Bitmap avatar;
        private String filePath;
        private final int borderW;

        public RoundAvatarView(Context context) {
            super(context);
            borderW = dp(context, 3);
            setScaleType(ScaleType.CENTER_CROP);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(borderW);
            borderPaint.setColor(PRIMARY);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    int size = Math.min(view.getWidth(), view.getHeight());
                    int radius = size / 2;
                    outline.setRoundRect(0, 0, size, size, radius);
                }
            });
            setClipToOutline(true);
        }

        public void setImageFile(String path) {
            filePath = path;
            if (path == null || path.isEmpty()) {
                avatar = null;
                invalidate();
                return;
            }
            try {
                File f = new File(path);
                if (f.exists()) {
                    avatar = BitmapFactory.decodeFile(path);
                    if (avatar != null) {
                        setImageBitmap(createCircleBitmap(avatar));
                        return;
                    }
                }
            } catch (Exception ignored) {}
            avatar = null;
            invalidate();
        }

        private Bitmap createCircleBitmap(Bitmap source) {
            int size = Math.min(source.getWidth(), source.getHeight());
            Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            float radius = size / 2f;
            canvas.drawCircle(radius, radius, radius, paint);
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            float left = (source.getWidth() - size) / 2f;
            float top = (source.getHeight() - size) / 2f;
            canvas.drawBitmap(source, -left, -top, paint);
            return output;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (avatar != null && w > 0 && h > 0) {
                setImageBitmap(createCircleBitmap(avatar));
            }
            clipPath.reset();
            int size = Math.min(w, h);
            int cx = w / 2;
            int cy = h / 2;
            int radius = size / 2;
            clipPath.addCircle(cx, cy, radius, Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.clipPath(clipPath);
            super.onDraw(canvas);
            if (avatar == null) {
                Paint placeholder = new Paint(Paint.ANTI_ALIAS_FLAG);
                placeholder.setColor(SURFACE_ACCENT);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int r = Math.min(getWidth(), getHeight()) / 2;
                canvas.drawCircle(cx, cy, r, placeholder);
                Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
                text.setColor(PRIMARY);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(getWidth() * 0.35f);
                text.setTypeface(Typeface.DEFAULT_BOLD);
                Paint.FontMetrics fm = text.getFontMetrics();
                float y = cy - (fm.ascent + fm.descent) / 2f;
                canvas.drawText("头", cx, y, text);
                text.setTextSize(getWidth() * 0.22f);
                canvas.drawText("像", cx, y + getWidth() * 0.15f, text);
            }
            int borderCx = getWidth() / 2;
            int borderCy = getHeight() / 2;
            int borderR = Math.min(getWidth(), getHeight()) / 2 - borderW / 2;
            canvas.drawCircle(borderCx, borderCy, borderR, borderPaint);
        }

        private int PrimarySoftCopy() {
            return Color.rgb(159, 168, 218);
        }
    }
}
