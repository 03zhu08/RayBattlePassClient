package cn.earthsky.raybattlepass.network.dto;

import java.util.List;

public class GuildTaskData {
    public String guildId;
    public List<Task> tasks;

    public static class Task {
        public String taskId;
        public String title;
        public String description;
        public int currentValue;
        public int targetValue;
        public boolean completed;
        public int contributorCount;
        public int expReward;
    }
}
