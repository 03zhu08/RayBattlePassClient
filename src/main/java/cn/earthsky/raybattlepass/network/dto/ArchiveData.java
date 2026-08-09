package cn.earthsky.raybattlepass.network.dto;

import java.util.List;

public class ArchiveData {
    public List<Entry> archives;

    public static class Entry {
        public String seasonId;
        public String displayName;
        public String subtitle;
        public long startTime;
        public long endTime;
        public int finalLevel;
        public int maxLevel;
        public int totalExp;
        public String highestTier;
        public int totalRewardsClaimed;
        public int overflowBoxesClaimed;
        public List<String> trophies;
    }
}
