package cn.earthsky.raybattlepass.network.dto;

import java.util.List;

public class PassTaskPatch {
    public List<TaskUpdate> updates;
    public int totalExpEarned;
    public String eventKey;

    public static class TaskUpdate {
        public String taskId;
        public int currentValue;
        public int targetValue;
        public boolean completed;
        public int expReward;
    }
}
