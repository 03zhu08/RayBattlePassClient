package cn.earthsky.raybattlepass.util;

import cn.earthsky.raybattlepass.network.dto.PassSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SampleDataGenerator {

    private SampleDataGenerator() {}

    public static PassSnapshot create() {
        PassSnapshot snapshot = new PassSnapshot();

        snapshot.seasonInfo = createSeasonInfo();
        snapshot.playerState = createPlayerState();
        snapshot.rewardWindow = createRewardWindow();
        snapshot.keyRewards = createKeyRewards();
        snapshot.taskSummary = createTaskSummary();
        snapshot.trackedTasks = createTrackedTasks();
        snapshot.currencySummary = createCurrencySummary();
        snapshot.uiHints = createUiHints();
        snapshot.assetManifest = createAssetManifest();
        snapshot.archiveSeasons = createArchiveSeasons();
        snapshot.hiddenTasks = createHiddenTasks();

        return snapshot;
    }

    private static PassSnapshot.SeasonInfo createSeasonInfo() {
        PassSnapshot.SeasonInfo info = new PassSnapshot.SeasonInfo();
        info.seasonId = "battle_pass_1";
        info.displayName = "S1 故土回声";
        info.subtitle = "双界联络作战记录";
        // 45 days remaining
        info.endTimeMs = System.currentTimeMillis() + 45L * 86400000L;
        info.themeId = "s1_hometown_echo";
        info.bannerAssetId = "season/s1_hometown_echo/bg_main";
        info.backgroundAssetId = "season/s1_hometown_echo/bg_main";
        info.rewardTrackBgAssetId = "season/s1_hometown_echo/bg_reward_track";
        info.taskBgAssetId = "season/s1_hometown_echo/bg_tasks";
        info.partnerFeatureBgAssetId = "season/s1_hometown_echo/bg_partner_feature";
        info.emblemAssetId = "season/s1_hometown_echo/emblem";
        info.featuredPartnerIds = Arrays.asList("test_partner");

        // Story nodes
        List<PassSnapshot.StoryNode> nodes = new ArrayList<>();
        PassSnapshot.StoryNode n1 = new PassSnapshot.StoryNode();
        n1.id = "s1_intro";
        n1.title = "裂隙信号";
        n1.unlockLevel = 1;
        nodes.add(n1);

        PassSnapshot.StoryNode n2 = new PassSnapshot.StoryNode();
        n2.id = "s1_training";
        n2.title = "故土训练区";
        n2.unlockLevel = 20;
        nodes.add(n2);

        PassSnapshot.StoryNode n3 = new PassSnapshot.StoryNode();
        n3.id = "s1_beacon";
        n3.title = "双界信标";
        n3.unlockLevel = 50;
        nodes.add(n3);

        info.storyNodes = nodes;
        return info;
    }

    private static PassSnapshot.PlayerState createPlayerState() {
        PassSnapshot.PlayerState state = new PassSnapshot.PlayerState();
        state.level = 32;
        state.exp = 620;
        state.expToNextLevel = 1000;
        state.totalExp = 32000;
        state.paidTier = "advanced";
        state.claimableCount = 3;
        state.overflowCount = 0;
        state.isMaxLevel = false;
        state.overflowExp = 0;
        state.overflowExpRequired = 1000;
        return state;
    }

    private static List<PassSnapshot.RewardCard> createRewardWindow() {
        List<PassSnapshot.RewardCard> cards = new ArrayList<>();
        // Generate reward cards for levels 1-80
        for (int level = 1; level <= 80; level++) {
            // Free track
            cards.add(createCard("free", level));
            // Advanced track
            cards.add(createCard("advanced", level));
            // Legendary track (only every 10 levels or so)
            if (level % 10 == 0) {
                cards.add(createCard("legendary", level));
            }
        }
        return cards;
    }

    private static PassSnapshot.RewardCard createCard(String track, int level) {
        PassSnapshot.RewardCard card = new PassSnapshot.RewardCard();
        card.track = track;
        card.level = level;
        card.highlight = level % 10 == 0;

        // Determine status based on player level (32)
        if (level < 32) {
            card.status = "claimed";
        } else if (level == 32) {
            card.status = Math.random() > 0.5 ? "claimable" : "claimed";
        } else if (level == 33) {
            card.status = "claimable";
        } else {
            card.status = "locked";
        }

        // Name, icon and rarity based on track and level
        switch (track) {
            case "free":
                card.rewardId = "free_" + String.format("%03d", level) + "_reward";
                card.iconAssetId = "item/partner_exp_potion/icon";
                if (level == 1) {
                    card.displayName = "故土凭证 x100";
                    card.rarity = "common";
                } else if (level == 5) {
                    card.displayName = "测试伙伴碎片 x5";
                    card.rarity = "rare";
                    card.highlight = true;
                } else if (level == 10) {
                    card.displayName = "伙伴寻访券 x1";
                    card.rarity = "rare";
                    card.highlight = true;
                } else if (level == 20) {
                    card.displayName = "史诗芯片箱";
                    card.rarity = "epic";
                    card.highlight = true;
                    card.iconAssetId = "item/chip_box_epic/icon";
                } else if (level == 30) {
                    card.displayName = "赛季币 x500";
                    card.rarity = "rare";
                } else if (level == 40) {
                    card.displayName = "强化材料箱";
                    card.rarity = "epic";
                } else if (level == 50) {
                    card.displayName = "普通奖杯";
                    card.rarity = "season";
                    card.iconAssetId = "trophy/s1_dual_world_beacon/icon";
                    card.highlight = true;
                } else if (level == 80) {
                    card.displayName = "大型材料箱";
                    card.rarity = "legendary";
                    card.highlight = true;
                } else {
                    card.displayName = "通行证" + level + "级奖励";
                    card.rarity = "common";
                }
                break;

            case "advanced":
                card.rewardId = "advanced_" + String.format("%03d", level) + "_reward";
                if (level == 1) {
                    card.displayName = "称号：故土联络者";
                    card.rarity = "rare";
                    card.iconAssetId = "season/s1_hometown_echo/emblem";
                    card.highlight = true;
                } else if (level == 5) {
                    card.displayName = "测试伙伴碎片 x20";
                    card.rarity = "rare";
                    card.iconAssetId = "item/partner_shard/icon";
                } else if (level == 10) {
                    card.displayName = "测试伙伴皮肤";
                    card.rarity = "season";
                    card.iconAssetId = "partner/test_partner/portrait";
                    card.clientTags = Arrays.asList("伙伴皮肤", "赛季限定");
                    card.highlight = true;
                } else if (level == 20) {
                    card.displayName = "地球科技宿舍家具";
                    card.rarity = "epic";
                    card.iconAssetId = "furniture/hometown_dorm_set/icon";
                    card.highlight = true;
                } else if (level == 30) {
                    card.displayName = "伙伴动作：战术待命";
                    card.rarity = "epic";
                    card.highlight = true;
                } else if (level == 40) {
                    card.displayName = "战术终端界面皮肤";
                    card.rarity = "epic";
                    card.iconAssetId = "ui/skin/tactical_terminal_hometown/preview";
                    card.highlight = true;
                } else if (level == 60) {
                    card.displayName = "伙伴技能特效";
                    card.rarity = "legendary";
                    card.highlight = true;
                } else if (level == 70) {
                    card.displayName = "展示背景：训练前哨";
                    card.rarity = "season";
                    card.iconAssetId = "island/display_bg/training_outpost";
                    card.highlight = true;
                } else if (level == 80) {
                    card.displayName = "大型伙伴自选箱";
                    card.rarity = "legendary";
                    card.iconAssetId = "item/partner_shard/icon";
                    card.highlight = true;
                } else {
                    card.displayName = "进阶线" + level + "级奖励";
                    card.rarity = "rare";
                }
                break;

            case "legendary":
                card.rewardId = "legendary_" + String.format("%03d", level) + "_reward";
                if (level == 1) {
                    card.displayName = "典藏头像框";
                    card.rarity = "legendary";
                    card.highlight = true;
                } else if (level == 10) {
                    card.displayName = "入场特效：双界扫描";
                    card.rarity = "legendary";
                    card.iconAssetId = "fx/pass/legendary_unlock/frame_0001";
                    card.highlight = true;
                } else if (level == 30) {
                    card.displayName = "展示背景：训练前哨";
                    card.rarity = "season";
                    card.iconAssetId = "island/display_bg/training_outpost";
                    card.highlight = true;
                } else if (level == 50) {
                    card.displayName = "典藏奖杯：双界信标";
                    card.rarity = "season";
                    card.iconAssetId = "trophy/s1_dual_world_beacon/icon";
                    card.highlight = true;
                } else if (level == 80) {
                    card.displayName = "S1典藏终点礼盒";
                    card.rarity = "legendary";
                    card.iconAssetId = "season/s1_hometown_echo/emblem";
                    card.highlight = true;
                } else {
                    card.displayName = "典藏线" + level + "级奖励";
                    card.rarity = "legendary";
                }
                break;
        }

        return card;
    }

    private static List<PassSnapshot.KeyReward> createKeyRewards() {
        List<PassSnapshot.KeyReward> list = new ArrayList<>();

        list.add(makeKeyReward(1, "free_001_reward", "故土凭证×100", "currency/season_coin/icon", "free", "common"));
        list.add(makeKeyReward(10, "advanced_010_reward", "测试伙伴皮肤", "partner/test_partner/portrait", "advanced", "season"));
        list.add(makeKeyReward(20, "advanced_020_reward", "地球科技宿舍家具", "furniture/hometown_dorm_set/icon", "advanced", "epic"));
        list.add(makeKeyReward(30, "advanced_030_reward", "伙伴动作：战术待命", "ui/pass/card/advanced_frame", "advanced", "epic"));
        list.add(makeKeyReward(40, "advanced_040_reward", "战术终端界面皮肤", "ui/skin/tactical_terminal_hometown/preview", "advanced", "epic"));
        list.add(makeKeyReward(50, "legendary_050_reward", "典藏奖杯：双界信标", "trophy/s1_dual_world_beacon/icon", "legendary", "season"));
        list.add(makeKeyReward(80, "legendary_080_reward", "S1典藏终点礼盒", "season/s1_hometown_echo/emblem", "legendary", "legendary"));

        return list;
    }

    private static PassSnapshot.KeyReward makeKeyReward(int level, String rewardId, String displayName,
                                                         String iconAssetId, String track, String rarity) {
        PassSnapshot.KeyReward kr = new PassSnapshot.KeyReward();
        kr.level = level;
        kr.rewardId = rewardId;
        kr.displayName = displayName;
        kr.iconAssetId = iconAssetId;
        kr.track = track;
        kr.rarity = rarity;
        return kr;
    }

    private static PassSnapshot.TaskSummary createTaskSummary() {
        PassSnapshot.TaskSummary summary = new PassSnapshot.TaskSummary();
        summary.dailyCompleted = 3;
        summary.dailyTotal = 5;
        summary.weeklyCompleted = 1;
        summary.weeklyTotal = 4;
        summary.seasonCompleted = 2;
        summary.seasonTotal = 4;
        summary.guildCompleted = 2;
        summary.guildTotal = 3;
        summary.limitedCompleted = 1;
        summary.limitedTotal = 2;
        return summary;
    }

    private static void setLimited(PassSnapshot.TrackedTask t, long endMs) {
        t.isLimited = true;
        t.endTimeMs = endMs;
    }

    private static void setHidden(PassSnapshot.TrackedTask t) {
        t.isHidden = true;
    }

    private static void setGuild(PassSnapshot.TrackedTask t) {
        t.isGuild = true;
    }

    private static List<PassSnapshot.TrackedTask> createTrackedTasks() {
        List<PassSnapshot.TrackedTask> tasks = new ArrayList<>();

        // Daily tasks
        tasks.add(makeTask("daily_001", "今日整备", "daily", "打开通行证一次", 1, 1, "pass_open"));
        tasks.add(makeTask("daily_002", "小队出勤", "daily", "派遣伙伴远征1次", 1, 1, "partner_expedition"));
        tasks.add(makeTask("daily_003", "训练模拟", "daily", "通关肉鸽玩法1次", 0, 1, "roguelike"));
        tasks.add(makeTask("daily_004", "岛屿维护", "daily", "收取空岛资源5次", 3, 5, "island"));
        tasks.add(makeTask("daily_005", "装备强化", "daily", "强化装备1次", 1, 1, "enhance"));

        // Weekly tasks
        tasks.add(makeTask("weekly_001", "裂隙压制", "weekly", "无尽爬塔累计通过20层", 12, 20, "tower"));
        tasks.add(makeTask("weekly_002", "防卫演练", "weekly", "岛屿入侵成功3次", 2, 3, "island_defense"));
        tasks.add(makeTask("weekly_003", "伙伴成长", "weekly", "任意伙伴提升5级", 3, 5, "partner"));
        tasks.add(makeTask("weekly_004", "公会协作", "weekly", "完成公会任务3次", 1, 3, "guild"));

        // Season tasks
        tasks.add(makeTask("season_001", "故土回访", "season", "完成S1第二章主线", 0, 1, "story_ch2"));
        tasks.add(makeTask("season_002", "双界信标", "season", "累计通关肉鸽玩法30次", 17, 30, "roguelike"));

        // Limited-time tasks
        PassSnapshot.TrackedTask limited1 = makeTask("limited_001", "裂隙突袭", "limited",
            "周末限定: 通关肉鸽玩法3次", 1, 3, "roguelike");
        setLimited(limited1, System.currentTimeMillis() + 2L * 86400000L);
        tasks.add(limited1);

        PassSnapshot.TrackedTask limited2 = makeTask("limited_002", "双倍收益", "limited",
            "限时: 完成爬塔10层", 6, 10, "tower");
        setLimited(limited2, System.currentTimeMillis() + 3L * 86400000L);
        tasks.add(limited2);

        // Guild tasks
        PassSnapshot.TrackedTask g1 = makeTask("guild_001", "公会远征", "guild",
            "公会成员完成远征10次", 7, 10, "partner_expedition");
        setGuild(g1);
        tasks.add(g1);

        PassSnapshot.TrackedTask g2 = makeTask("guild_002", "公会首领挑战", "guild",
            "参与公会首领战3次", 2, 3, "guild_boss");
        setGuild(g2);
        tasks.add(g2);

        PassSnapshot.TrackedTask g3 = makeTask("guild_003", "公会物资捐献", "guild",
            "捐献物资5000", 3500, 5000, "guild_donate");
        setGuild(g3);
        tasks.add(g3);

        return tasks;
    }

    private static List<PassSnapshot.TrackedTask> createHiddenTasks() {
        List<PassSnapshot.TrackedTask> tasks = new ArrayList<>();
        PassSnapshot.TrackedTask h1 = makeTask("hidden_001", "秘密信标", "hidden",
            "发现3个隐藏信标", 0, 3, null);
        setHidden(h1);
        tasks.add(h1);
        PassSnapshot.TrackedTask h2 = makeTask("hidden_002", "裂隙深处的呼唤", "hidden",
            "在肉鸽玩法中触发隐藏首领", 0, 1, null);
        setHidden(h2);
        tasks.add(h2);
        return tasks;
    }

    private static List<PassSnapshot.ArchiveSeason> createArchiveSeasons() {
        List<PassSnapshot.ArchiveSeason> list = new ArrayList<>();

        // Previous season: S0 裂隙序章
        PassSnapshot.ArchiveSeason s0 = new PassSnapshot.ArchiveSeason();
        s0.seasonId = "battle_pass_0";
        s0.displayName = "S0 裂隙序章";
        s0.subtitle = "首次裂隙探测记录";
        s0.maxLevel = 50;
        s0.playerLevel = 48;
        s0.paidTier = "advanced";
        s0.emblemAssetId = "season/s0_rift_prologue/emblem";
        s0.earnedTrophies = Arrays.asList("裂隙探测奖杯", "首季参与纪念章");
        s0.tasksCompleted = 28;
        s0.totalTasks = 30;
        s0.startTimeMs = System.currentTimeMillis() - 120L * 86400000L;
        s0.endTimeMs = System.currentTimeMillis() - 60L * 86400000L;
        list.add(s0);

        return list;
    }

    private static PassSnapshot.TrackedTask makeTask(String id, String title, String group,
                                                      String description, int progress, int target, String gotoAction) {
        PassSnapshot.TrackedTask task = new PassSnapshot.TrackedTask();
        task.taskId = id;
        task.title = title;
        task.group = group;
        task.progress = progress;
        task.targetValue = target;
        task.gotoAction = gotoAction;
        return task;
    }

    private static PassSnapshot.CurrencySummary createCurrencySummary() {
        PassSnapshot.CurrencySummary currency = new PassSnapshot.CurrencySummary();
        currency.currencyId = "season_coin";
        currency.displayName = "故土凭证";
        currency.iconAssetId = "currency/season_coin/icon";
        currency.provider = "placeholderapi";
        currency.placeholder = "%rpass_season_coin%";
        currency.currentValue = 12350;
        currency.refreshIntervalTicks = 40;
        currency.showOnTopBar = true;
        return currency;
    }

    private static PassSnapshot.UiHints createUiHints() {
        PassSnapshot.UiHints hints = new PassSnapshot.UiHints();
        hints.primaryColor = "#F2C94C";
        hints.accentColor = "#55D6FF";
        hints.layout = "terminal_radar";
        hints.themeId = "s1_hometown_echo";
        return hints;
    }

    private static List<String> createAssetManifest() {
        return Arrays.asList(
            "season/s1_hometown_echo/bg_main",
            "season/s1_hometown_echo/bg_reward_track",
            "season/s1_hometown_echo/emblem",
            "partner/test_partner/portrait",
            "ui/pass/card/free_frame",
            "ui/pass/card/advanced_frame",
            "ui/pass/card/legendary_frame",
            "ui/pass/home/radar_base",
            "ui/pass/home/radar_scan",
            "ui/pass/panel/top_bar",
            "ui/pass/nav/button_normal",
            "ui/pass/nav/button_selected",
            "ui/pass/task/card_normal",
            "ui/pass/task/progress_bg",
            "ui/pass/task/progress_fill_yellow",
            "ui/pass/button/claim_all",
            "currency/season_coin/icon",
            "item/partner_exp_potion/icon",
            "item/partner_shard/icon",
            "item/partner_ticket/icon",
            "item/chip_box_epic/icon",
            "trophy/s1_dual_world_beacon/icon"
        );
    }
}
