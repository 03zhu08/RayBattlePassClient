package cn.earthsky.raybattlepass.network;

import cn.earthsky.raybattlepass.network.dto.ArchiveData;
import cn.earthsky.raybattlepass.network.dto.GuildTaskData;
import cn.earthsky.raybattlepass.network.dto.OverflowStatus;
import cn.earthsky.raybattlepass.network.dto.PassSnapshot;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class PassSnapshotMergerTest {

    @Test
    public void mergesArchiveProtocolFieldsIntoUiModel() {
        PassSnapshot snapshot = new PassSnapshot();
        ArchiveData data = new ArchiveData();
        ArchiveData.Entry entry = new ArchiveData.Entry();
        entry.seasonId = "s0";
        entry.finalLevel = 42;
        entry.highestTier = "advanced";
        entry.trophies = Arrays.asList("先锋");
        data.archives = Arrays.asList(entry);

        PassSnapshotMerger.mergeArchives(snapshot, data);

        assertEquals(1, snapshot.archiveSeasons.size());
        assertEquals(42, snapshot.archiveSeasons.get(0).playerLevel);
        assertEquals("advanced", snapshot.archiveSeasons.get(0).paidTier);
    }

    @Test
    public void replacesGuildTasksAndUpdatesSummary() {
        PassSnapshot snapshot = new PassSnapshot();
        snapshot.taskSummary = new PassSnapshot.TaskSummary();
        PassSnapshot.TrackedTask old = new PassSnapshot.TrackedTask();
        old.isGuild = true;
        snapshot.trackedTasks = new java.util.ArrayList<>(Arrays.asList(old));

        GuildTaskData data = new GuildTaskData();
        GuildTaskData.Task task = new GuildTaskData.Task();
        task.taskId = "guild_one";
        task.currentValue = 10;
        task.targetValue = 10;
        task.completed = true;
        data.tasks = Arrays.asList(task);

        PassSnapshotMerger.mergeGuildTasks(snapshot, data);

        assertEquals(1, snapshot.trackedTasks.size());
        assertEquals("guild_one", snapshot.trackedTasks.get(0).taskId);
        assertEquals(1, snapshot.taskSummary.guildCompleted);
        assertEquals(1, snapshot.taskSummary.guildTotal);
    }

    @Test
    public void refreshesOverflowState() {
        PassSnapshot snapshot = new PassSnapshot();
        snapshot.playerState = new PassSnapshot.PlayerState();
        OverflowStatus status = new OverflowStatus();
        status.isMaxLevel = true;
        status.expPerBox = 1000;
        status.currentOverflowExp = 250;
        status.overflowBoxes = 3;

        PassSnapshotMerger.mergeOverflow(snapshot, status);

        assertTrue(snapshot.playerState.isMaxLevel);
        assertEquals(250, snapshot.playerState.overflowExp);
        assertEquals(3, snapshot.playerState.overflowCount);
    }
}
