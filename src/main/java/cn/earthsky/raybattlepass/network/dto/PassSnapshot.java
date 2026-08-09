package cn.earthsky.raybattlepass.network.dto;

import java.util.List;

public class PassSnapshot {
    public SeasonInfo seasonInfo;
    public PlayerState playerState;
    public List<RewardCard> rewardWindow;
    public List<KeyReward> keyRewards;
    public TaskSummary taskSummary;
    public List<TrackedTask> trackedTasks;
    public CurrencySummary currencySummary;
    public UiHints uiHints;
    public List<String> assetManifest;
    public List<ArchiveSeason> archiveSeasons;
    public List<TrackedTask> hiddenTasks;

    public static class ArchiveSeason {
        public String seasonId;
        public String displayName;
        public String subtitle;
        public int maxLevel;
        public int playerLevel;
        public String paidTier;
        public String emblemAssetId;
        public List<String> earnedTrophies;
        public int tasksCompleted;
        public int totalTasks;
        public long startTimeMs;
        public long endTimeMs;
    }

    public static class SeasonInfo {
        public String seasonId;
        public String displayName;
        public String subtitle;
        public long endTimeMs;
        public String themeId;
        public int maxLevel;
        public String bannerAssetId;
        public String backgroundAssetId;
        public String rewardTrackBgAssetId;
        public String taskBgAssetId;
        public String partnerFeatureBgAssetId;
        public String emblemAssetId;
        public List<String> featuredPartnerIds;
        public List<StoryNode> storyNodes;
    }

    public static class StoryNode {
        public String id;
        public String title;
        public int unlockLevel;
    }

    public static class PlayerState {
        public int level;
        public int exp;
        public int expToNextLevel;
        public int totalExp;
        public String paidTier;
        public int claimableCount;
        public int overflowCount;
        public boolean isMaxLevel;
        public int overflowExp;
        public int overflowExpRequired;
    }

    public static class RewardCard {
        public String rewardId;
        public String track;
        public int level;
        public String displayName;
        public String iconAssetId;
        public String rarity;
        public String status;
        public boolean highlight;
        public List<String> clientTags;
    }

    public static class KeyReward {
        public int level;
        public String rewardId;
        public String displayName;
        public String iconAssetId;
        public String track;
        public String rarity;
    }

    public static class TaskSummary {
        public int dailyCompleted;
        public int dailyTotal;
        public int weeklyCompleted;
        public int weeklyTotal;
        public int seasonCompleted;
        public int seasonTotal;
        public int guildCompleted;
        public int guildTotal;
        public int limitedCompleted;
        public int limitedTotal;
    }

    public static class TrackedTask {
        public String taskId;
        public String title;
        public String description;
        public int progress;
        public int targetValue;
        public String group;
        public String gotoAction;
        public boolean isLimited;
        public long endTimeMs;
        public boolean isHidden;
        public boolean isGuild;
    }

    public static class CurrencySummary {
        public String currencyId;
        public String displayName;
        public String iconAssetId;
        public String provider;
        public String placeholder;
        public int currentValue;
        public int refreshIntervalTicks;
        public boolean showOnTopBar;
    }

    public static class UiHints {
        public String primaryColor;
        public String accentColor;
        public String layout;
        public String themeId;
    }
}
