package com.systemobservatory.mobile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class SnapshotDto {
    public String diannaomingcheng = "";
    public String zhimingcheng = "Windows";
    public String neihe = "";
    public long yunxingshijianmiao = 0;
    public double zonggonghaowazhi = -1;
    public boolean zonggonghaogusuan = true;
    public CpuInfo chuliqi3 = new CpuInfo();
    public MemInfo neicun = new MemInfo();
    public final List<DiskInfo> cipan2 = new ArrayList<>();
    public final List<GpuInfo> xianqia7 = new ArrayList<>();
    public final List<FanInfo> fengshan3 = new ArrayList<>();
    public final List<NetInfo> wangluo = new ArrayList<>();
    public TokenInfo tokenjiankong = new TokenInfo();
    public String receivedAt = "";

    public static SnapshotDto fromJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        String receivedAt = root.optString("receivedAt", "");
        if (root.has("snapshot") && root.opt("snapshot") instanceof JSONObject) {
            root = root.getJSONObject("snapshot");
        }

        SnapshotDto dto = new SnapshotDto();
        dto.receivedAt = receivedAt;
        dto.diannaomingcheng = root.optString("diannaomingcheng", "");
        dto.zhimingcheng = root.optString("zhimingcheng", "Windows");
        dto.neihe = root.optString("neihe", "");
        dto.yunxingshijianmiao = root.optLong("yunxingshijianmiao", 0);
        dto.zonggonghaowazhi = root.optDouble("zonggonghaowazhi", -1);
        dto.zonggonghaogusuan = root.optBoolean("zonggonghaogusuan", true);
        dto.chuliqi3 = CpuInfo.from(root.optJSONObject("chuliqi3"));
        dto.neicun = MemInfo.from(root.optJSONObject("neicun"));
        fillDisks(dto.cipan2, root.optJSONArray("cipan2"));
        fillGpus(dto.xianqia7, root.optJSONArray("xianqia7"));
        fillFans(dto.fengshan3, root.optJSONArray("fengshan3"));
        fillNetworks(dto.wangluo, root.optJSONArray("wangluo"));
        dto.tokenjiankong = TokenInfo.from(root.optJSONObject("tokenjiankong"));
        return dto;
    }

    public static SnapshotDto empty() {
        SnapshotDto dto = new SnapshotDto();
        dto.diannaomingcheng = "未连接设备";
        return dto;
    }

    public int healthScore() {
        int score = 100;
        score -= pressurePenalty(chuliqi3.shiyonglvbaifenbi, 72, 90, 18);
        score -= pressurePenalty(neicun.shiyonglvbaifenbi2, 76, 92, 20);

        GpuInfo gpu = primaryGpu();
        if (gpu != null) {
            score -= pressurePenalty(gpu.shiyonglvbaifenbi4, 80, 95, 14);
            if (gpu.wenduzhi2 > 84) score -= 16;
            else if (gpu.wenduzhi2 > 76) score -= 8;
        }

        if (chuliqi3.wenduzhi > 88) score -= 16;
        else if (chuliqi3.wenduzhi > 78) score -= 8;

        DiskInfo disk = primaryDisk();
        if (disk != null && disk.zongliangzhi4 > 0) {
            double freeRatio = disk.shengyuzhi2 * 100.0 / disk.zongliangzhi4;
            if (freeRatio < 8) score -= 18;
            else if (freeRatio < 15) score -= 9;
        }

        return Math.max(0, Math.min(100, score));
    }

    public String statusTitle() {
        int score = healthScore();
        if (score >= 86) return "运行良好";
        if (score >= 70) return "需要留意";
        return "存在风险";
    }

    public String statusDetail() {
        DiskInfo disk = primaryDisk();
        if (disk != null && disk.zongliangzhi4 > 0) {
            double freeRatio = disk.shengyuzhi2 * 100.0 / disk.zongliangzhi4;
            if (freeRatio < 15) return "磁盘可用空间偏低，建议尽快清理。";
        }
        if (chuliqi3.wenduzhi > 78) return "CPU 温度偏高，建议检查散热。";
        GpuInfo gpu = primaryGpu();
        if (gpu != null && gpu.wenduzhi2 > 76) return "显卡温度偏高，建议留意负载。";
        if (neicun.shiyonglvbaifenbi2 > 85) return "内存占用偏高，后台任务较多。";
        return "各项指标正常，系统运行稳定。";
    }

    public GpuInfo primaryGpu() {
        return xianqia7.isEmpty() ? null : xianqia7.get(0);
    }

    public DiskInfo primaryDisk() {
        return cipan2.isEmpty() ? null : cipan2.get(0);
    }

    public NetInfo primaryNetwork() {
        return wangluo.isEmpty() ? null : wangluo.get(0);
    }

    private static int pressurePenalty(double value, double warn, double danger, int maxPenalty) {
        if (value <= warn) return 0;
        if (value >= danger) return maxPenalty;
        return (int) Math.round((value - warn) / (danger - warn) * maxPenalty);
    }

    private static void fillDisks(List<DiskInfo> target, JSONArray array) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) target.add(DiskInfo.from(item));
        }
    }

    private static void fillGpus(List<GpuInfo> target, JSONArray array) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) target.add(GpuInfo.from(item));
        }
    }

    private static void fillFans(List<FanInfo> target, JSONArray array) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) target.add(FanInfo.from(item));
        }
    }

    private static void fillNetworks(List<NetInfo> target, JSONArray array) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) target.add(NetInfo.from(item));
        }
    }

    public static final class CpuInfo {
        public String xinghao2 = "Unknown CPU";
        public int hexin2;
        public int xiancheng;
        public double shiyonglvbaifenbi;
        public double zhipinlv;
        public double wenduzhi = -1;
        public double gonghaowazhi = -1;
        public boolean gonghaogusuan = true;

        static CpuInfo from(JSONObject json) {
            CpuInfo info = new CpuInfo();
            if (json == null) return info;
            info.xinghao2 = json.optString("xinghao2", info.xinghao2);
            info.hexin2 = json.optInt("hexin2", 0);
            info.xiancheng = json.optInt("xiancheng", 0);
            info.shiyonglvbaifenbi = json.optDouble("shiyonglvbaifenbi", 0);
            info.zhipinlv = json.optDouble("zhipinlv", 0);
            info.wenduzhi = json.optDouble("wenduzhi", -1);
            info.gonghaowazhi = json.optDouble("gonghaowazhi", -1);
            info.gonghaogusuan = json.optBoolean("gonghaogusuan", true);
            return info;
        }
    }

    public static final class MemInfo {
        public long zongliangzhi3;
        public long yiyongzhi;
        public long shengyuzhi;
        public long keyongzhi3;
        public double shiyonglvbaifenbi2;

        static MemInfo from(JSONObject json) {
            MemInfo info = new MemInfo();
            if (json == null) return info;
            info.zongliangzhi3 = json.optLong("zongliangzhi3", 0);
            info.yiyongzhi = json.optLong("yiyongzhi", 0);
            info.shengyuzhi = json.optLong("shengyuzhi", 0);
            info.keyongzhi3 = json.optLong("keyongzhi3", 0);
            info.shiyonglvbaifenbi2 = json.optDouble("shiyonglvbaifenbi2", 0);
            return info;
        }
    }

    public static final class DiskInfo {
        public String cipanmingcheng = "";
        public String shebei2 = "";
        public String zhileixing = "";
        public long zongliangzhi4;
        public long yiyongzhi2;
        public long shengyuzhi2;
        public double shiyonglvbaifenbi3;

        static DiskInfo from(JSONObject json) {
            DiskInfo info = new DiskInfo();
            info.cipanmingcheng = json.optString("cipanmingcheng", "");
            info.shebei2 = json.optString("shebei2", "");
            info.zhileixing = json.optString("zhileixing", "");
            info.zongliangzhi4 = json.optLong("zongliangzhi4", 0);
            info.yiyongzhi2 = json.optLong("yiyongzhi2", 0);
            info.shengyuzhi2 = json.optLong("shengyuzhi2", 0);
            info.shiyonglvbaifenbi3 = json.optDouble("shiyonglvbaifenbi3", 0);
            return info;
        }
    }

    public static final class GpuInfo {
        public String xinghao3 = "";
        public double wenduzhi2 = -1;
        public double shiyonglvbaifenbi4;
        public double hexinpinlv;
        public double neicunpinlv;
        public double fengshanbaifenbi = -1;
        public long zhizongliangzhi;
        public long zhiyiyongzhi;
        public double gonghaowazhi = -1;
        public boolean gonghaogusuan = true;

        static GpuInfo from(JSONObject json) {
            GpuInfo info = new GpuInfo();
            info.xinghao3 = json.optString("xinghao3", "");
            info.wenduzhi2 = json.optDouble("wenduzhi2", -1);
            info.shiyonglvbaifenbi4 = json.optDouble("shiyonglvbaifenbi4", 0);
            info.hexinpinlv = json.optDouble("hexinpinlv", 0);
            info.neicunpinlv = json.optDouble("neicunpinlv", 0);
            info.fengshanbaifenbi = json.optDouble("fengshanbaifenbi", -1);
            info.zhizongliangzhi = json.optLong("zhizongliangzhi", 0);
            info.zhiyiyongzhi = json.optLong("zhiyiyongzhi", 0);
            info.gonghaowazhi = json.optDouble("gonghaowazhi", -1);
            info.gonghaogusuan = json.optBoolean("gonghaogusuan", true);
            return info;
        }
    }

    public static final class FanInfo {
        public String mingcheng9 = "Fan";
        public String leixing2 = "other";
        public double fengshanzhuansu = -1;
        public double baifenbi2 = -1;

        static FanInfo from(JSONObject json) {
            FanInfo info = new FanInfo();
            info.mingcheng9 = json.optString("mingcheng9", "Fan");
            info.leixing2 = json.optString("leixing2", "other");
            info.fengshanzhuansu = json.optDouble("fengshanzhuansu", -1);
            info.baifenbi2 = json.optDouble("baifenbi2", -1);
            return info;
        }
    }

    public static final class NetInfo {
        public String mingcheng10 = "";
        public long xiazaizijie2;
        public long shangchuanzijie2;
        public long xiazaisudu2;
        public long shangchuansudu2;

        static NetInfo from(JSONObject json) {
            NetInfo info = new NetInfo();
            info.mingcheng10 = json.optString("mingcheng10", "");
            info.xiazaizijie2 = json.optLong("xiazaizijie2", 0);
            info.shangchuanzijie2 = json.optLong("shangchuanzijie2", 0);
            info.xiazaisudu2 = json.optLong("xiazaisudu2", 0);
            info.shangchuansudu2 = json.optLong("shangchuansudu2", 0);
            return info;
        }
    }

    public static final class TokenInfo {
        public boolean peizhiwanzheng;
        public boolean yunxing;
        public boolean houtaicaijiqiyunxing;
        public String zhuangtai = "";
        public String tongjiriqi = "";
        public String moxingmingcheng = "";
        public int shurutokens;
        public int shuchutokens;
        public int jinrishurutokens;
        public int jinrishuchutokens;
        public int jinritokens;
        public int leijitokens;
        public int fengzhitokens;
        public int dangqianlianxutianshu;
        public int zuichanglianxutianshu;
        public double zhunquesudu;
        public double shishitokensudu;
        public double zonghaoshi;
        public double shouziziyanmiao;
        public boolean shouziziyangusuan;
        public String gengxinshijian = "";
        public final List<TokenDayInfo> huodongriqi = new ArrayList<>();

        static TokenInfo from(JSONObject json) {
            TokenInfo info = new TokenInfo();
            if (json == null) return info;
            info.peizhiwanzheng = json.optBoolean("peizhiwanzheng", false);
            info.yunxing = json.optBoolean("yunxing", false);
            info.houtaicaijiqiyunxing = json.optBoolean("houtaicaijiqiyunxing", false);
            info.zhuangtai = json.optString("zhuangtai", "");
            info.tongjiriqi = json.optString("tongjiriqi", "");
            info.moxingmingcheng = json.optString("moxingmingcheng", "");
            info.shurutokens = json.optInt("shurutokens", 0);
            info.shuchutokens = json.optInt("shuchutokens", 0);
            info.jinrishurutokens = json.optInt("jinrishurutokens", 0);
            info.jinrishuchutokens = json.optInt("jinrishuchutokens", 0);
            info.jinritokens = json.optInt("jinritokens", 0);
            info.leijitokens = json.optInt("leijitokens", 0);
            info.fengzhitokens = json.optInt("fengzhitokens", 0);
            info.dangqianlianxutianshu = json.optInt("dangqianlianxutianshu", 0);
            info.zuichanglianxutianshu = json.optInt("zuichanglianxutianshu", 0);
            info.zhunquesudu = json.optDouble("zhunquesudu", 0);
            info.shishitokensudu = json.optDouble("shishitokensudu", 0);
            info.zonghaoshi = json.optDouble("zonghaoshi", 0);
            info.shouziziyanmiao = json.optDouble("shouziziyanmiao", 0);
            info.shouziziyangusuan = json.optBoolean("shouziziyangusuan", false);
            info.gengxinshijian = json.optString("gengxinshijian", "");
            JSONArray days = json.optJSONArray("huodongriqi");
            if (days != null) {
                for (int i = 0; i < days.length(); i++) {
                    JSONObject item = days.optJSONObject(i);
                    if (item != null) info.huodongriqi.add(TokenDayInfo.from(item));
                }
            }
            return info;
        }

        public boolean hasData() {
            return jinritokens > 0 || leijitokens > 0 || shurutokens > 0 || shuchutokens > 0 || !huodongriqi.isEmpty();
        }
    }

    public static final class TokenDayInfo {
        public String riqi = "";
        public int shurutokens;
        public int shuchutokens;
        public int zongtokens;
        public double zuichangrenwumiao;

        static TokenDayInfo from(JSONObject json) {
            TokenDayInfo info = new TokenDayInfo();
            info.riqi = json.optString("riqi", "");
            info.shurutokens = json.optInt("shurutokens", 0);
            info.shuchutokens = json.optInt("shuchutokens", 0);
            info.zongtokens = json.optInt("zongtokens", Math.max(0, info.shurutokens) + Math.max(0, info.shuchutokens));
            info.zuichangrenwumiao = json.optDouble("zuichangrenwumiao", 0);
            return info;
        }
    }
}
