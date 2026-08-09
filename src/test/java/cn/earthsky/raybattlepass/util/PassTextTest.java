package cn.earthsky.raybattlepass.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PassTextTest {

    @Test
    public void translatesProtocolRarityAndTrackValues() {
        assertEquals("普通", PassText.rarity("common"));
        assertEquals("稀有", PassText.rarity("rare"));
        assertEquals("史诗", PassText.rarity("epic"));
        assertEquals("传说", PassText.rarity("legendary"));
        assertEquals("赛季限定", PassText.rarity("season"));

        assertEquals("免费线", PassText.track("free"));
        assertEquals("进阶线", PassText.track("advanced"));
        assertEquals("典藏线", PassText.track("legendary"));
    }

    @Test
    public void translatesPurchaseTiersAndUsesChineseFallbacks() {
        assertEquals("基础档", PassText.tier("none"));
        assertEquals("进阶档", PassText.tier("advanced"));
        assertEquals("典藏档", PassText.tier("legendary"));
        assertEquals("未知档位", PassText.tier("future_tier"));
        assertEquals("未知", PassText.rarity("future_rarity"));
    }

    @Test
    public void hidesTechnicalProviderNamesFromPlayers() {
        assertEquals("服务端占位符接口", PassText.provider("placeholderapi"));
        assertEquals("服务端数据接口", PassText.provider("custom_provider"));
    }
}
