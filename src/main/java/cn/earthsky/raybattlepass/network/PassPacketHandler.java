package cn.earthsky.raybattlepass.network;

import cn.earthsky.raybattlepass.RayBattlePass;
import cn.earthsky.raybattlepass.gui.PassMainScreen;
import cn.earthsky.raybattlepass.network.dto.*;
import cn.earthsky.raybattlepass.util.JsonUtil;
import cn.earthsky.raybattlepass.util.ToastManager;
import cn.earthsky.raybattlepass.util.PassText;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PassPacketHandler {

    public static final String CH_MAIN = "rpass:pass";
    public static final int PROTOCOL_VERSION = 1;

    private FMLEventChannel mainChannel;

    private boolean localMode;
    private PassSnapshot localSnapshot;
    private final Map<String, FragmentAccumulator> fragments = new ConcurrentHashMap<>();

    public void enableLocalMode(PassSnapshot snapshot) {
        this.localMode = true;
        this.localSnapshot = snapshot;
    }

    public void disableLocalMode() {
        this.localMode = false;
        this.localSnapshot = null;
    }

    public boolean isLocalMode() {
        return localMode;
    }

    public PassSnapshot getLocalSnapshot() {
        return localSnapshot;
    }

    public void registerChannels() {
        NetworkRegistry registry = NetworkRegistry.INSTANCE;
        mainChannel = registry.newEventDrivenChannel(CH_MAIN);
        mainChannel.register(this);
    }

    @SubscribeEvent
    public void onMainChannel(FMLNetworkEvent.ClientCustomPacketEvent event) {
        PacketBuffer buf = new PacketBuffer(event.getPacket().payload());
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        String json = new String(bytes, StandardCharsets.UTF_8);
        PassPacketHeader header = JsonUtil.fromJson(json, PassPacketHeader.class);

        if (header == null || header.messageType == null) return;
        if (header.protocolVersion != 0 && header.protocolVersion != PROTOCOL_VERSION) {
            ToastManager.show("通行证协议不兼容",
                    "客户端 " + PROTOCOL_VERSION + " / 服务端 " + header.protocolVersion);
            return;
        }
        if ("PASS_FRAGMENT".equals(header.messageType)) {
            handleFragment(json);
            return;
        }

        Minecraft.getMinecraft().addScheduledTask(() -> {
            switch (header.messageType) {
                case "PASS_OPEN_SNAPSHOT":
                    handleSnapshot(json);
                    break;
                case "PASS_STATE_PATCH":
                    handleStatePatch(json);
                    break;
                case "PASS_TASK_PATCH":
                    handleTaskPatch(json);
                    break;
                case "PASS_REWARD_PREVIEW":
                    handleRewardPreview(json);
                    break;
                case "PASS_CLAIM_RESULT":
                    handleClaimResult(json);
                    break;
                case "PASS_CLAIM_BATCH_RESULT":
                    handleClaimBatchResult(json);
                    break;
                case "PASS_ENTITLEMENT_RESULT":
                    handleEntitlementResult(json);
                    break;
                case "PASS_TOAST":
                    handleToast(json);
                    break;
                case "PASS_ERROR":
                    handleError(json);
                    break;
                case "PASS_CURRENCY_PATCH":
                    handleCurrencyPatch(json);
                    break;
                case "PASS_ASSET_HINT":
                    handleAssetHint(json);
                    break;
                case "PASS_PARTNER_FEATURE":
                    handlePartnerFeature(json);
                    break;
                case "PASS_OVERFLOW_STATUS":
                    handleOverflowStatus(json);
                    break;
                case "PASS_GUILD_TASK_DATA":
                    handleGuildTaskData(json);
                    break;
                case "PASS_CLOSE":
                    handleClose();
                    break;
            }
        });
    }

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(this::sendHandshake);
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        fragments.clear();
        RayBattlePass.instance.updateSnapshot(null);
    }

    private void handleFragment(String json) {
        cleanupExpiredFragments();
        FragmentEnvelope fragment = JsonUtil.fromJson(json, FragmentEnvelope.class);
        if (fragment == null || fragment.requestId == null || fragment.payload == null
                || fragment.sequence < 1 || fragment.totalSequence < 1
                || fragment.totalSequence > 128
                || fragment.sequence > fragment.totalSequence || fragment.payload.data == null) {
            return;
        }

        FragmentAccumulator accumulator = fragments.computeIfAbsent(
            fragment.requestId, id -> new FragmentAccumulator(fragment.totalSequence));
        if (accumulator.total != fragment.totalSequence) {
            fragments.remove(fragment.requestId);
            return;
        }

        try {
            accumulator.parts[fragment.sequence - 1] = Base64.getDecoder().decode(fragment.payload.data);
        } catch (IllegalArgumentException ignored) {
            fragments.remove(fragment.requestId);
            return;
        }

        if (!accumulator.isComplete()) return;
        fragments.remove(fragment.requestId);

        int length = 0;
        for (byte[] part : accumulator.parts) length += part.length;
        byte[] combined = new byte[length];
        int offset = 0;
        for (byte[] part : accumulator.parts) {
            System.arraycopy(part, 0, combined, offset, part.length);
            offset += part.length;
        }

        sendAck(fragment.requestId);
        handleCompleteMessage(new String(combined, StandardCharsets.UTF_8));
    }

    private void cleanupExpiredFragments() {
        long cutoff = System.currentTimeMillis() - 30_000L;
        for (Map.Entry<String, FragmentAccumulator> entry
                : new ArrayList<>(fragments.entrySet())) {
            if (entry.getValue().createdAt < cutoff) fragments.remove(entry.getKey());
        }
    }

    private void handleCompleteMessage(String json) {
        PassPacketHeader header = JsonUtil.fromJson(json, PassPacketHeader.class);
        if (header == null || header.messageType == null) return;
        Minecraft.getMinecraft().addScheduledTask(() -> dispatchMessage(header.messageType, json));
    }

    private void dispatchMessage(String messageType, String json) {
        switch (messageType) {
            case "PASS_OPEN_SNAPSHOT": handleSnapshot(json); break;
            case "PASS_STATE_PATCH": handleStatePatch(json); break;
            case "PASS_TASK_PATCH": handleTaskPatch(json); break;
            case "PASS_REWARD_PREVIEW": handleRewardPreview(json); break;
            case "PASS_CLAIM_RESULT": handleClaimResult(json); break;
            case "PASS_CLAIM_BATCH_RESULT": handleClaimBatchResult(json); break;
            case "PASS_ENTITLEMENT_RESULT": handleEntitlementResult(json); break;
            case "PASS_TOAST": handleToast(json); break;
            case "PASS_ERROR": handleError(json); break;
            case "PASS_CURRENCY_PATCH": handleCurrencyPatch(json); break;
            case "PASS_ASSET_HINT": handleAssetHint(json); break;
            case "PASS_PARTNER_FEATURE": handlePartnerFeature(json); break;
            case "PASS_OVERFLOW_STATUS": handleOverflowStatus(json); break;
            case "PASS_GUILD_TASK_DATA": handleGuildTaskData(json); break;
            case "PASS_CLOSE": handleClose(); break;
            default: break;
        }
    }

    private void handleSnapshot(String json) {
        PassSnapshot snapshot = JsonUtil.fromPayload(json, PassSnapshot.class);
        if (snapshot == null) return;
        // BungeeCord backend switches do not always create a new client connection.
        // Re-announce the client whenever a backend successfully delivers a snapshot.
        sendHandshake();
        RayBattlePass.instance.updateSnapshot(snapshot);
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof PassMainScreen) {
            ((PassMainScreen) current).refreshSnapshot(snapshot);
        } else {
            Minecraft.getMinecraft().displayGuiScreen(new PassMainScreen(snapshot));
        }
        sendRequestAssets();
        sendRequestOverflow();
    }

    private void handleStatePatch(String json) {
        PassStatePatch patch = JsonUtil.fromPayload(json, PassStatePatch.class);
        if (patch == null) return;
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof PassMainScreen) {
            ((PassMainScreen) current).applyStatePatch(patch);
        }
    }

    private void handleTaskPatch(String json) {
        PassTaskPatch patch = JsonUtil.fromPayload(json, PassTaskPatch.class);
        if (patch == null) return;
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof PassMainScreen) {
            ((PassMainScreen) current).applyTaskPatch(patch);
        }
    }

    private void handleRewardPreview(String json) {
        RewardPreview preview = JsonUtil.fromPayload(json, RewardPreview.class);
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof PassMainScreen) {
            ((PassMainScreen) current).showRewardPreview(preview);
        }
    }

    private void handleClaimResult(String json) {
        ClaimResult result = JsonUtil.fromPayload(json, ClaimResult.class);
        if (result == null) return;
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof PassMainScreen) {
            ((PassMainScreen) current).applyClaimResult(result);
        }
    }

    private void handleClaimBatchResult(String json) {
        ClaimBatchResult result = JsonUtil.fromPayload(json, ClaimBatchResult.class);
        if (result == null) return;
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof PassMainScreen) {
            ((PassMainScreen) current).applyClaimBatchResult(result);
        }
    }

    private void handleEntitlementResult(String json) {
        EntitlementResult result = JsonUtil.fromPayload(json, EntitlementResult.class);
        if (result == null) return;
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof PassMainScreen) {
            ((PassMainScreen) current).applyEntitlementResult(result);
        }
    }

    private void handleToast(String json) {
        ToastMessage toast = JsonUtil.fromPayload(json, ToastMessage.class);
        if (toast != null) {
            ToastManager.show(toast.title, toast.body);
        }
    }

    private void handleError(String json) {
        PassError error = JsonUtil.fromPayload(json, PassError.class);
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof PassMainScreen) {
            ((PassMainScreen) current).showError(error);
        }
        if (error != null && error.closeScreen) {
            Minecraft.getMinecraft().displayGuiScreen(null);
        }
    }

    private void handleCurrencyPatch(String json) {
        CurrencyPatch patch = JsonUtil.fromPayload(json, CurrencyPatch.class);
        if (patch == null) return;
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof PassMainScreen) {
            ((PassMainScreen) current).applyCurrencyPatch(patch);
        }
    }

    private void handleAssetHint(String json) {
        AssetHint hint = JsonUtil.fromPayload(json, AssetHint.class);
        PassSnapshot snapshot = RayBattlePass.instance.getCachedSnapshot();
        if (hint == null || snapshot == null) return;
        PassSnapshotMerger.mergeAssets(snapshot, hint);
        refreshOpenScreen(snapshot);
    }

    private void handlePartnerFeature(String json) {
        PartnerFeature feature = JsonUtil.fromPayload(json, PartnerFeature.class);
        PassSnapshot snapshot = RayBattlePass.instance.getCachedSnapshot();
        if (feature == null || snapshot == null) return;
        PassSnapshotMerger.mergePartner(snapshot, feature);
        refreshOpenScreen(snapshot);
    }

    private void handleOverflowStatus(String json) {
        OverflowStatus status = JsonUtil.fromPayload(json, OverflowStatus.class);
        PassSnapshot snapshot = RayBattlePass.instance.getCachedSnapshot();
        if (status == null || snapshot == null || snapshot.playerState == null) return;
        PassSnapshotMerger.mergeOverflow(snapshot, status);
        refreshOpenScreen(snapshot);
    }

    private void handleGuildTaskData(String json) {
        GuildTaskData data = JsonUtil.fromPayload(json, GuildTaskData.class);
        PassSnapshot snapshot = RayBattlePass.instance.getCachedSnapshot();
        if (data == null || snapshot == null) return;
        PassSnapshotMerger.mergeGuildTasks(snapshot, data);
        refreshOpenScreen(snapshot);
    }

    private void refreshOpenScreen(PassSnapshot snapshot) {
        RayBattlePass.instance.updateSnapshot(snapshot);
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof PassMainScreen) {
            ((PassMainScreen) current).refreshSnapshot(snapshot);
        }
    }

    private void handleClose() {
        Minecraft.getMinecraft().displayGuiScreen(null);
    }

    // ─── Local simulation ─────────────────────────────────────

    private void localClaimReward(String rewardId) {
        if (localSnapshot == null || localSnapshot.rewardWindow == null) return;
        for (PassSnapshot.RewardCard card : localSnapshot.rewardWindow) {
            if (card.rewardId.equals(rewardId)) {
                card.status = "claimed";
                if (localSnapshot.playerState != null) {
                    localSnapshot.playerState.claimableCount = Math.max(0, localSnapshot.playerState.claimableCount - 1);
                }
                applyToScreen();
                ToastManager.show("领取成功", card.displayName);
                return;
            }
        }
        ToastManager.show("领取失败", "奖励不存在或不可领取");
    }

    private void localClaimAll() {
        if (localSnapshot == null || localSnapshot.rewardWindow == null) return;
        int count = 0;
        for (PassSnapshot.RewardCard card : localSnapshot.rewardWindow) {
            if ("claimable".equals(card.status)) {
                card.status = "claimed";
                count++;
            }
        }
        if (localSnapshot.playerState != null) {
            localSnapshot.playerState.claimableCount = 0;
        }
        applyToScreen();
        ToastManager.show("一键领取", "已领取 " + count + " 项奖励");
    }

    private void localBuyAdvanced() {
        if (localSnapshot == null || localSnapshot.playerState == null) return;
        localSnapshot.playerState.paidTier = "advanced";
        // Unlock advanced rewards
        if (localSnapshot.rewardWindow != null) {
            for (PassSnapshot.RewardCard card : localSnapshot.rewardWindow) {
                if ("advanced".equals(card.track) && "locked".equals(card.status)) {
                    if (card.level <= localSnapshot.playerState.level) {
                        card.status = "claimable";
                        localSnapshot.playerState.claimableCount++;
                    }
                }
            }
        }
        applyToScreen();
        ToastManager.show("购买成功", "已激活进阶通行证");
    }

    private void localBuyLegendary() {
        if (localSnapshot == null || localSnapshot.playerState == null) return;
        localSnapshot.playerState.paidTier = "legendary";
        if (localSnapshot.rewardWindow != null) {
            for (PassSnapshot.RewardCard card : localSnapshot.rewardWindow) {
                if (("advanced".equals(card.track) || "legendary".equals(card.track))
                    && "locked".equals(card.status)) {
                    if (card.level <= localSnapshot.playerState.level) {
                        card.status = "claimable";
                        localSnapshot.playerState.claimableCount++;
                    }
                }
            }
        }
        applyToScreen();
        ToastManager.show("购买成功", "已激活典藏通行证");
    }

    private void localRewardPreview(String rewardId) {
        // Show a simple toast with reward info in local mode
        if (localSnapshot == null || localSnapshot.rewardWindow == null) return;
        for (PassSnapshot.RewardCard card : localSnapshot.rewardWindow) {
            if (card.rewardId.equals(rewardId)) {
                ToastManager.show(card.displayName,
                    "等级: " + card.level + " | " + PassText.rarity(card.rarity)
                        + " | " + PassText.track(card.track));
                return;
            }
        }
    }

    private void applyToScreen() {
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof PassMainScreen) {
            if (localSnapshot != null) {
                ((PassMainScreen) current).refreshSnapshot(localSnapshot);
            }
        }
    }

    // ─── Public send methods ──────────────────────────────────

    public void sendHandshake() {
        if (localMode) return;
        sendMessage("{\"messageType\":\"PASS_HANDSHAKE\",\"protocolVersion\":"
                + PROTOCOL_VERSION + ",\"modVersion\":\"1.0.0\"}");
    }

    public void sendOpenRequest() {
        if (localMode) return;
        sendMessage("{\"messageType\":\"PASS_OPEN\",\"protocolVersion\":"
                + PROTOCOL_VERSION + "}");
    }

    public void sendChangeTab(String tab) {
        if (localMode) return;
        sendMessage("{\"messageType\":\"PASS_CHANGE_TAB\",\"tab\":\"" + escape(tab) + "\"}");
    }

    public void sendClaimReward(String rewardId) {
        if (localMode) {
            Minecraft.getMinecraft().addScheduledTask(() -> localClaimReward(rewardId));
            return;
        }
        sendMessage("{\"messageType\":\"PASS_CLAIM_REWARD\",\"rewardId\":\"" + escape(rewardId) + "\"}");
    }

    public void sendClaimAll() {
        if (localMode) {
            Minecraft.getMinecraft().addScheduledTask(this::localClaimAll);
            return;
        }
        sendMessage("{\"messageType\":\"PASS_CLAIM_ALL\"}");
    }

    public void sendBuyAdvanced() {
        if (localMode) {
            Minecraft.getMinecraft().addScheduledTask(this::localBuyAdvanced);
            return;
        }
        sendMessage("{\"messageType\":\"PASS_BUY_ADVANCED\"}");
    }

    public void sendBuyLegendary() {
        if (localMode) {
            Minecraft.getMinecraft().addScheduledTask(this::localBuyLegendary);
            return;
        }
        sendMessage("{\"messageType\":\"PASS_BUY_LEGENDARY\"}");
    }

    public void sendTrackTask(String taskId) {
        if (localMode) return;
        sendMessage("{\"messageType\":\"PASS_TRACK_TASK\",\"taskId\":\"" + escape(taskId) + "\"}");
    }

    public void sendGotoAction(String action) {
        if (localMode) {
            ToastManager.show("前往", "本地模式: " + action);
            return;
        }
        sendMessage("{\"messageType\":\"PASS_GOTO_ACTION\",\"action\":\"" + escape(action) + "\"}");
    }

    public void sendClaimTask(String taskId) {
        if (localMode) {
            ToastManager.show("领取任务", "本地模式: " + taskId);
            return;
        }
        sendMessage("{\"messageType\":\"PASS_CLAIM_TASK\",\"taskId\":\"" + escape(taskId) + "\"}");
    }

    public void sendRequestRewardPreview(String rewardId) {
        if (localMode) {
            Minecraft.getMinecraft().addScheduledTask(() -> localRewardPreview(rewardId));
            return;
        }
        sendMessage("{\"messageType\":\"PASS_REQUEST_REWARD_PREVIEW\",\"rewardId\":\"" + escape(rewardId) + "\"}");
    }

    public void sendPageRequest(int page) {
        if (localMode) return;
        sendMessage("{\"messageType\":\"PASS_PAGE_REQUEST\",\"page\":" + page + "}");
    }

    public void sendRequestAssets() {
        if (localMode) return;
        sendMessage("{\"messageType\":\"PASS_REQUEST_ASSETS\"}");
    }

    public void sendRequestPartnerData() {
        if (localMode) return;
        sendMessage("{\"messageType\":\"PASS_REQUEST_PARTNER_DATA\"}");
    }

    public void sendRequestOverflow() {
        if (localMode) return;
        sendMessage("{\"messageType\":\"PASS_REQUEST_OVERFLOW\"}");
    }

    public void sendClaimOverflow() {
        if (localMode) return;
        sendMessage("{\"messageType\":\"PASS_CLAIM_OVERFLOW\"}");
    }

    public void sendRequestGuildTasks(String guildId) {
        if (localMode || guildId == null || guildId.trim().isEmpty()) return;
        sendMessage("{\"messageType\":\"PASS_REQUEST_GUILD_TASKS\",\"guildId\":\""
                + escape(guildId.trim()) + "\"}");
    }

    public void sendAck(String requestId) {
        if (localMode) return;
        sendMessage("{\"messageType\":\"PASS_ACK\",\"requestId\":\"" + escape(requestId) + "\"}");
    }

    public void sendCloseClient() {
        if (localMode) return;
        sendMessage("{\"messageType\":\"PASS_CLOSE_CLIENT\"}");
    }

    private void sendMessage(String json) {
        if (mainChannel == null) return;
        PacketBuffer buf = new PacketBuffer(Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.UTF_8)));
        mainChannel.sendToServer(new FMLProxyPacket(buf, CH_MAIN));
    }

    private static class FragmentEnvelope extends PassPacketHeader {
        FragmentPayload payload;
    }

    private static class FragmentPayload {
        String data;
    }

    private static class FragmentAccumulator {
        final int total;
        final byte[][] parts;
        final long createdAt;

        FragmentAccumulator(int total) {
            this.total = total;
            this.parts = new byte[total][];
            this.createdAt = System.currentTimeMillis();
        }

        boolean isComplete() {
            for (byte[] part : parts) {
                if (part == null) return false;
            }
            return true;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
