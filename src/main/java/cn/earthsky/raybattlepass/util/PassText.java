package cn.earthsky.raybattlepass.util;

import java.util.Locale;

/**
 * Converts protocol identifiers into player-facing Chinese text.
 * Protocol values themselves remain unchanged for client/server compatibility.
 */
public final class PassText {

    private PassText() {
    }

    public static String rarity(String value) {
        if (value == null) return "未知";
        switch (value.toLowerCase(Locale.ROOT)) {
            case "common": return "普通";
            case "rare": return "稀有";
            case "epic": return "史诗";
            case "legendary": return "传说";
            case "season": return "赛季限定";
            default: return "未知";
        }
    }

    public static String track(String value) {
        if (value == null) return "未知";
        switch (value.toLowerCase(Locale.ROOT)) {
            case "free": return "免费线";
            case "advanced": return "进阶线";
            case "legendary": return "典藏线";
            default: return "未知";
        }
    }

    public static String tier(String value) {
        if (value == null) return "基础档";
        switch (value.toLowerCase(Locale.ROOT)) {
            case "none":
            case "free": return "基础档";
            case "advanced": return "进阶档";
            case "legendary": return "典藏档";
            default: return "未知档位";
        }
    }

    public static String provider(String value) {
        if (value == null || value.trim().isEmpty()) return "服务端";
        if ("placeholderapi".equalsIgnoreCase(value)) return "服务端占位符接口";
        return "服务端数据接口";
    }
}
