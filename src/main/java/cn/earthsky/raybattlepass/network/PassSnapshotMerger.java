package cn.earthsky.raybattlepass.network;

import cn.earthsky.raybattlepass.network.dto.*;

import java.util.ArrayList;
import java.util.List;

public final class PassSnapshotMerger {

    private PassSnapshotMerger() {
    }

    public static void mergeAssets(PassSnapshot snapshot, AssetHint hint) {
        if (snapshot != null && hint != null) snapshot.assetManifest = hint.assets;
    }

    public static void mergePartner(PassSnapshot snapshot, PartnerFeature feature) {
        if (snapshot == null || snapshot.seasonInfo == null || feature == null) return;
        List<String> partnerIds = new ArrayList<>();
        if (feature.partners != null) {
            for (PartnerFeature.Partner partner : feature.partners) {
                if (partner != null && partner.partnerId != null) partnerIds.add(partner.partnerId);
            }
        }
        snapshot.seasonInfo.featuredPartnerIds = partnerIds;
        if (feature.storyNodes != null) snapshot.seasonInfo.storyNodes = feature.storyNodes;
    }

    public static void mergeArchives(PassSnapshot snapshot, ArchiveData data) {
        if (snapshot == null || data == null) return;
        List<PassSnapshot.ArchiveSeason> archives = new ArrayList<>();
        if (data.archives != null) {
            for (ArchiveData.Entry entry : data.archives) {
                PassSnapshot.ArchiveSeason archive = new PassSnapshot.ArchiveSeason();
                archive.seasonId = entry.seasonId;
                archive.displayName = entry.displayName;
                archive.subtitle = entry.subtitle;
                archive.playerLevel = entry.finalLevel;
                archive.maxLevel = entry.maxLevel > 0 ? entry.maxLevel : entry.finalLevel;
                archive.paidTier = entry.highestTier;
                archive.earnedTrophies = entry.trophies;
                archive.startTimeMs = entry.startTime;
                archive.endTimeMs = entry.endTime;
                archives.add(archive);
            }
        }
        snapshot.archiveSeasons = archives;
    }

    public static void mergeOverflow(PassSnapshot snapshot, OverflowStatus status) {
        if (snapshot == null || snapshot.playerState == null || status == null) return;
        snapshot.playerState.isMaxLevel = status.isMaxLevel;
        snapshot.playerState.overflowExpRequired = status.expPerBox;
        snapshot.playerState.overflowExp = status.currentOverflowExp;
        snapshot.playerState.overflowCount = status.overflowBoxes;
    }

    public static void mergeGuildTasks(PassSnapshot snapshot, GuildTaskData data) {
        if (snapshot == null || data == null) return;
        if (snapshot.trackedTasks == null) snapshot.trackedTasks = new ArrayList<>();
        snapshot.trackedTasks.removeIf(task -> task != null && task.isGuild);
        int completed = 0;
        if (data.tasks != null) {
            for (GuildTaskData.Task source : data.tasks) {
                PassSnapshot.TrackedTask task = new PassSnapshot.TrackedTask();
                task.taskId = source.taskId;
                task.title = source.title;
                task.description = source.description;
                task.progress = source.currentValue;
                task.targetValue = source.targetValue;
                task.group = "guild";
                task.isGuild = true;
                snapshot.trackedTasks.add(task);
                if (source.completed) completed++;
            }
        }
        if (snapshot.taskSummary != null) {
            snapshot.taskSummary.guildCompleted = completed;
            snapshot.taskSummary.guildTotal = data.tasks == null ? 0 : data.tasks.size();
        }
    }
}
