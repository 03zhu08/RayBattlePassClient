package cn.earthsky.raybattlepass.gui;

import cn.earthsky.raybattlepass.RayBattlePass;
import cn.earthsky.raybattlepass.animation.PassAnimation;
import cn.earthsky.raybattlepass.network.PassPacketHandler;
import cn.earthsky.raybattlepass.network.dto.*;
import cn.earthsky.raybattlepass.util.GuiHelper;
import cn.earthsky.raybattlepass.util.PassFontUtil;
import cn.earthsky.raybattlepass.util.PassText;
import cn.earthsky.raybattlepass.util.ToastManager;

import java.io.IOException;
import java.util.*;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class PassMainScreen extends GuiScreen {

    private static final int NAV_WIDTH = 80;
    private static final int RIGHT_PANEL_WIDTH = 200;
    private static final int TOP_BAR_HEIGHT = 52;

    private static final int PANEL_BG = 0xAA0F0F0F;

    private PassSnapshot snapshot;
    private final PassPacketHandler handler;

    private int currentTab = 0;
    private static final int TAB_HOME = 0, TAB_REWARDS = 1, TAB_TASKS = 2;
    private static final String[] TAB_LABELS = {
        "首页", "奖励", "任务"
    };

    // Theme colors (from uiHints, with defaults)
    private int primaryColor = 0xFFF2C94C;
    private int accentColor = 0xFF55D6FF;

    // Smooth scroll state - reward track
    private float rewardScrollOffset;
    private float rewardScrollVelocity;
    private float rewardRenderOffset;
    private boolean rewardSpringBack;
    private long rewardLastScrollTime;
    private long rewardLastRenderTime;
    private boolean rewardPointerDown;
    private boolean rewardDragged;
    private int rewardPointerStartX;
    private int rewardPointerLastX;
    private long rewardPointerLastTime;
    private PassSnapshot.RewardCard pendingRewardCard;

    // Smooth scroll state - task list
    private float taskScrollOffset;
    private float taskScrollVelocity;
    private float taskRenderOffset;
    private boolean taskSpringBack;
    private long taskLastScrollTime;
    private long taskLastRenderTime;

    private int taskCategory = 0;
    private static final String[] TASK_CATEGORIES = {
        "每日任务", "每周任务", "赛季任务"
    };
    private boolean showHiddenTasks;
    private int hiddenRevealIndex;

    // Reward detail popup
    private PassSnapshot.RewardCard selectedRewardCard;

    // Purchase preview popup
    private boolean purchasePreviewOpen;
    private String purchasePreviewTier; // "advanced" or "legendary"

    // Right panel collapse
    private boolean rightPanelCollapsed;
    private float rightPanelSlide;

    // Animation state
    private PassAnimation openAnim;
    private PassAnimation levelUpAnim;
    private PassAnimation claimAnim;
    private PassAnimation purchaseAnim;
    private PassAnimation hiddenRevealAnim;
    private PassAnimation tabSwitchAnim;
    private float tabFromPosition;
    private int hoverMouseX, hoverMouseY;
    private long openStartTimeMs;

    // Adaptive layout dimensions
    private int guiW, guiH;
    private float uiScale = 1f;
    private boolean compactLayout;
    private boolean layoutInitialized;

    // Settlement screen flag
    private boolean showSettlement;

    public PassMainScreen(PassSnapshot snapshot) {
        this.snapshot = snapshot;
        this.handler = RayBattlePass.instance.getPacketHandler();
        this.openAnim = PassAnimation.openSequence();
        this.openStartTimeMs = System.currentTimeMillis();
        applyThemeColors();
        initRewardScrollPosition();
    }

    private void initRewardScrollPosition() {
        if (snapshot != null && snapshot.playerState != null) {
            int cardW = 72;
            int currentLv = snapshot.playerState.level;
            rewardScrollOffset = Math.max(0, (currentLv - 3) * (cardW + 4));
            rewardRenderOffset = rewardScrollOffset;
        }
    }

    private void applyThemeColors() {
        if (snapshot != null && snapshot.uiHints != null) {
            if (snapshot.uiHints.primaryColor != null)
                primaryColor = parseHex(snapshot.uiHints.primaryColor, 0xFFF2C94C);
            if (snapshot.uiHints.accentColor != null)
                accentColor = parseHex(snapshot.uiHints.accentColor, 0xFF55D6FF);
        }
    }

    private static int parseHex(String hex, int defaultColor) {
        if (hex == null || hex.isEmpty()) return defaultColor;
        if (hex.startsWith("#")) hex = hex.substring(1);
        try { return ((int) Long.parseLong(hex, 16)) | 0xFF000000; }
        catch (NumberFormatException e) { return defaultColor; }
    }

    public void refreshSnapshot(PassSnapshot snapshot) {
        if (this.snapshot != null && snapshot != null && snapshot.playerState != null
            && this.snapshot.playerState != null) {
            if (snapshot.playerState.level > this.snapshot.playerState.level)
                levelUpAnim = PassAnimation.levelUpEffect();
            if (snapshot.playerState.claimableCount < this.snapshot.playerState.claimableCount)
                claimAnim = PassAnimation.claimRewardEffect();
            if (snapshot.playerState.paidTier != null
                && !snapshot.playerState.paidTier.equals(this.snapshot.playerState.paidTier))
                purchaseAnim = PassAnimation.purchaseEffect();
        }
        this.snapshot = snapshot;
        applyThemeColors();
    }

    public void applyStatePatch(PassStatePatch patch) {
        if (snapshot == null || snapshot.playerState == null) return;
        int oldLevel = snapshot.playerState.level;
        String oldTier = snapshot.playerState.paidTier;
        if (patch.level > 0) snapshot.playerState.level = patch.level;
        snapshot.playerState.exp = patch.exp;
        snapshot.playerState.expToNextLevel = patch.expToNextLevel;
        if (patch.paidTier != null && !patch.paidTier.equals(oldTier))
            purchaseAnim = PassAnimation.purchaseEffect();
        if (patch.paidTier != null) snapshot.playerState.paidTier = patch.paidTier;
        if (snapshot.playerState.level > oldLevel) levelUpAnim = PassAnimation.levelUpEffect();
        snapshot.playerState.claimableCount = patch.claimableCount;
        snapshot.playerState.overflowCount = patch.overflowCount;
    }

    public void applyTaskPatch(PassTaskPatch patch) {
        if (snapshot == null || snapshot.trackedTasks == null
                || patch == null || patch.updates == null) return;
        for (PassTaskPatch.TaskUpdate update : patch.updates) {
            for (PassSnapshot.TrackedTask task : snapshot.trackedTasks) {
                if (task.taskId != null && task.taskId.equals(update.taskId)) {
                    task.progress = update.currentValue;
                    task.targetValue = update.targetValue;
                    break;
                }
            }
        }
    }
    public void showRewardPreview(RewardPreview preview) {
        if (preview == null) return;
        PassSnapshot.RewardCard card = new PassSnapshot.RewardCard();
        card.rewardId = preview.rewardId; card.displayName = preview.displayName;
        card.track = preview.track; card.level = preview.level;
        card.rarity = preview.rarity; card.iconAssetId = preview.iconAssetId;
        card.status = preview.claimed ? "claimed" : (preview.claimable ? "claimable" : "locked");
        card.clientTags = preview.clientTags;
        selectedRewardCard = card;
    }
    public void applyClaimResult(ClaimResult r) {
        if (r != null && r.success) {
            markRewardsClaimed(java.util.Collections.singletonList(r.rewardId));
            claimAnim = PassAnimation.claimRewardEffect();
            if (r.statePatch != null) applyStatePatch(r.statePatch);
        }
    }
    public void applyClaimBatchResult(ClaimBatchResult r) {
        if (r != null && r.success) {
            markRewardsClaimed(r.claimedRewardIds);
            claimAnim = PassAnimation.claimRewardEffect();
            if (r.statePatch != null) applyStatePatch(r.statePatch);
        }
    }
    public void applyEntitlementResult(EntitlementResult r) {
        if (r.success) { purchaseAnim = PassAnimation.purchaseEffect(); purchasePreviewOpen = false;
            if (r.statePatch != null) applyStatePatch(r.statePatch); }
    }
    public void applyCurrencyPatch(CurrencyPatch patch) {
        if (snapshot == null || snapshot.currencySummary == null) return;
        snapshot.currencySummary.currentValue = patch.currentValue;
    }
    public void showError(PassError error) {
        if (error != null) ToastManager.show("操作失败",
            error.message != null ? error.message : "");
    }

    private void markRewardsClaimed(List<String> rewardIds) {
        if (rewardIds == null || snapshot == null || snapshot.rewardWindow == null) return;
        for (PassSnapshot.RewardCard card : snapshot.rewardWindow) {
            if (rewardIds.contains(card.rewardId)) card.status = "claimed";
        }
    }

    private void updateAdaptiveLayout() {
        AdaptiveLayout.Result layout = AdaptiveLayout.calculate(width, height);
        uiScale = layout.scale;
        guiW = layout.width;
        guiH = layout.height;
        boolean nextCompact = layout.compact;
        if ((!layoutInitialized || nextCompact != compactLayout) && nextCompact) {
            rightPanelCollapsed = true;
            rightPanelSlide = 1f;
        }
        compactLayout = nextCompact;
        layoutInitialized = true;
    }

    private int getVirtualScreenWidth() {
        return Math.max(guiW, (int) (width / uiScale));
    }

    private int getVirtualScreenHeight() {
        return Math.max(guiH, (int) (height / uiScale));
    }

    private int getGuiLeft() {
        return (getVirtualScreenWidth() - guiW) / 2;
    }

    private int getGuiTop() {
        return (getVirtualScreenHeight() - guiH) / 2;
    }

    private int toUiCoordinate(int value) {
        return (int) (value / uiScale);
    }

    private void enableUiScissor(int x, int y, int w, int h) {
        int framebufferScale = new ScaledResolution(mc).getScaleFactor();
        int sx = (int) Math.floor(x * uiScale * framebufferScale);
        int sy = mc.displayHeight
                - (int) Math.ceil((y + h) * uiScale * framebufferScale);
        int sw = (int) Math.ceil(w * uiScale * framebufferScale);
        int sh = (int) Math.ceil(h * uiScale * framebufferScale);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, Math.max(0, sw), Math.max(0, sh));
    }

    private void disableUiScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ─── Main draw ─────────────────────────────────────────────

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        updateAdaptiveLayout();
        updateAllScrollPhysics();

        if (openAnim != null) { openAnim.update(); if (openAnim.isComplete()) openAnim = null; }
        if (levelUpAnim != null) { levelUpAnim.update(); if (levelUpAnim.isComplete()) levelUpAnim = null; }
        if (claimAnim != null) { claimAnim.update(); if (claimAnim.isComplete()) claimAnim = null; }
        if (purchaseAnim != null) { purchaseAnim.update(); if (purchaseAnim.isComplete()) purchaseAnim = null; }
        if (hiddenRevealAnim != null) { hiddenRevealAnim.update(); if (hiddenRevealAnim.isComplete()) hiddenRevealAnim = null; }
        if (tabSwitchAnim != null) { tabSwitchAnim.update(); if (tabSwitchAnim.isComplete()) tabSwitchAnim = null; }

        int uiMouseX = toUiCoordinate(mouseX);
        int uiMouseY = toUiCoordinate(mouseY);
        hoverMouseX = uiMouseX;
        hoverMouseY = uiMouseY;
        int guiLeft = getGuiLeft(), guiTop = getGuiTop();

        GlStateManager.pushMatrix();
        GlStateManager.scale(uiScale, uiScale, 1f);
        try {
        // Opening scan line
        if (openAnim != null && !openAnim.isComplete()) {
            float sy = openAnim.get("fadeIn");
            GuiHelper.drawRect(guiLeft, guiTop + (int) (sy * guiH) - 2, guiLeft + guiW,
                guiTop + (int) (sy * guiH) + 2, primaryColor);
        }

        // Purchase dim overlay
        if (purchaseAnim != null && !purchaseAnim.isComplete()) {
            int alpha = (int) (purchaseAnim.get("dimOverlay") * 150);
            GuiHelper.drawRect(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, (alpha << 24) | 0x000000);
        }

        // Purchase preview modal
        if (purchasePreviewOpen) {
            drawPurchasePreview(guiLeft, guiTop);
            return;
        }

        // Reward detail modal
        if (selectedRewardCard != null) {
            drawRewardDetailPopup(guiLeft, guiTop);
            return;
        }

        // Settlement screen
        if (showSettlement) {
            drawSettlementScreen(guiLeft, guiTop);
            return;
        }

        drawTopBar(guiLeft, guiTop);
        drawLeftNav(guiLeft, guiTop, uiMouseX, uiMouseY);
        rightPanelSlide = AdaptiveLayout.nextPanelSlide(rightPanelSlide, rightPanelCollapsed);

        switch (currentTab) {
            case TAB_HOME:    drawHomeTab(guiLeft, guiTop); break;
            case TAB_REWARDS: drawRewardsTab(guiLeft, guiTop); break;
            case TAB_TASKS:   drawTasksTab(guiLeft, guiTop); break;
        }

        if (tabSwitchAnim != null) {
            float progress = tabSwitchAnim.get("transition");
            int alpha = (int) ((1f - progress) * 175);
            int contentX = guiLeft + NAV_WIDTH;
            GuiHelper.drawRect(contentX, guiTop + TOP_BAR_HEIGHT,
                    contentX + getContentWidth(), guiTop + guiH, alpha << 24);
        }

        drawRightPanel(guiLeft, guiTop, uiMouseX, uiMouseY);

        // Level-up flash
        if (levelUpAnim != null && !levelUpAnim.isComplete()) {
            float fa = levelUpAnim.get("flashAlpha") * (1 - levelUpAnim.get("flashFade"));
            if (fa > 0.01f)
                GuiHelper.drawRect(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ((int) (fa * 60) << 24) | 0xF2C94C);
        }

        // Track light-up

        // Hidden task reveal flash
        if (hiddenRevealAnim != null && !hiddenRevealAnim.isComplete()) {
            float fa = hiddenRevealAnim.get("flashAlpha") * (1 - hiddenRevealAnim.get("flashFade"));
            if (fa > 0.01f)
                GuiHelper.drawRect(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ((int) (fa * 50) << 24) | 0xAA44FF);
        }

        } finally {
            GlStateManager.popMatrix();
            ToastManager.render(new ScaledResolution(mc), partialTicks);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }
    }

    // ─── Top Bar ───────────────────────────────────────────────

    private void drawTopBar(int guiLeft, int guiTop) {
        int x = guiLeft, y = guiTop, w = guiW;
        GuiHelper.drawRect(x, y, x + w, y + TOP_BAR_HEIGHT, PANEL_BG);
        GuiHelper.drawRect(x, y + TOP_BAR_HEIGHT - 1, x + w, y + TOP_BAR_HEIGHT, primaryColor);
        if (snapshot == null) return;

        // A single full-width level progress indicator remains visible on every tab.
        if (snapshot.playerState != null) {
            PassSnapshot.PlayerState state = snapshot.playerState;
            int amount = state.isMaxLevel ? state.overflowExp : state.exp;
            int required = state.isMaxLevel ? state.overflowExpRequired : state.expToNextLevel;
            float progress = required > 0 ? Math.max(0f, Math.min(1f, amount / (float) required)) : 0f;
            int barX = x + 8, barW = Math.max(1, w - 16);
            GuiHelper.drawRect(barX, y + 2, barX + barW, y + 6, 0xFF343434);
            GuiHelper.drawRect(barX, y + 2, barX + Math.round(barW * progress), y + 6,
                    state.isMaxLevel ? accentColor : primaryColor);
        }

        if (compactLayout) {
            if (snapshot.seasonInfo != null) {
                String seasonName = PassFontUtil.truncateString(
                        snapshot.seasonInfo.displayName, Math.max(90, w / 3));
                PassFontUtil.drawStringWithShadow(seasonName, x + 8, y + 8, primaryColor);
                if (snapshot.seasonInfo.endTimeMs > 0) {
                    String time = formatRemainingTime(
                            snapshot.seasonInfo.endTimeMs - System.currentTimeMillis());
                    PassFontUtil.drawStringWithShadow(time, x + 8, y + 27, 0xFF888888);
                }
            }
            if (snapshot.playerState != null) {
                String level = "等级 " + snapshot.playerState.level
                        + (snapshot.playerState.isMaxLevel ? "（满级）" : "");
                PassFontUtil.drawCenteredString(level, x + w / 2, y + 8, primaryColor);
                String progress = snapshot.playerState.isMaxLevel
                        ? snapshot.playerState.overflowExp + "/" + snapshot.playerState.overflowExpRequired
                        : snapshot.playerState.exp + "/" + snapshot.playerState.expToNextLevel;
                PassFontUtil.drawCenteredString(progress, x + w / 2, y + 27, 0xFFAAAAAA);
            }
            if (snapshot.playerState != null && snapshot.playerState.claimableCount > 0) {
                String claim = "领取(" + snapshot.playerState.claimableCount + ")";
                int claimW = PassFontUtil.getStringWidth(claim) + 10;
                int bx = x + w - claimW - 8;
                drawActionButton(bx, y + 14, claimW, 18, primaryColor, 0xFFFFFFFF);
                PassFontUtil.drawCenteredString(claim, bx + claimW / 2, y + 19, 0xFF000000);
            }
            return;
        }

        if (snapshot.seasonInfo != null) {
            PassFontUtil.drawStringWithShadow(snapshot.seasonInfo.displayName, x + 10, y + 8, primaryColor);
            PassFontUtil.drawStringWithShadow(snapshot.seasonInfo.subtitle, x + 10, y + 24, 0xFFAAAAAA);
        }

        if (snapshot.seasonInfo != null && snapshot.seasonInfo.endTimeMs > 0) {
            String ts = formatRemainingTime(snapshot.seasonInfo.endTimeMs - System.currentTimeMillis());
            int tw = PassFontUtil.getStringWidth(ts);
            PassFontUtil.drawStringWithShadow(ts, x + w / 2 - tw / 2, y + 18, 0xFFCCCCCC);
        }

        if (snapshot.playerState != null) {
            int lx = x + w / 2 - 80;
            String lvStr = "等级 " + snapshot.playerState.level;
            if (snapshot.playerState.isMaxLevel) lvStr += "（满级）";
            PassFontUtil.drawStringWithShadow(lvStr, lx, y + 8, primaryColor);

            if (snapshot.playerState.isMaxLevel) {
                // Overflow display
                String ovStr = "循环宝箱 " + snapshot.playerState.overflowExp + "/"
                    + snapshot.playerState.overflowExpRequired;
                PassFontUtil.drawStringWithShadow(ovStr, lx, y + 28, accentColor);
            } else {
                String expStr = snapshot.playerState.exp + "/" + snapshot.playerState.expToNextLevel;
                PassFontUtil.drawStringWithShadow(expStr, lx, y + 28, 0xFFAAAAAA);
            }
        }

        if (snapshot.playerState != null && snapshot.playerState.claimableCount > 0) {
            String claimStr = "一键领取 (" + snapshot.playerState.claimableCount + ")";
            int cw = PassFontUtil.getStringWidth(claimStr);
            int bx = x + w - cw - 16;
            drawActionButton(bx, y + 14, cw + 8, 16, primaryColor, 0xFFFFFFFF);
            PassFontUtil.drawString(claimStr, bx + 4, y + 17, 0xFF000000);
        }
    }

    // ─── Left Navigation ───────────────────────────────────────

    private void drawLeftNav(int guiLeft, int guiTop, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop + TOP_BAR_HEIGHT;
        int h = guiH - TOP_BAR_HEIGHT;
        float navSlide = openAnim != null ? openAnim.get("navSlide") : 0;
        int ox = (int) navSlide;
        GuiHelper.drawRect(x, y, x + NAV_WIDTH, y + h, PANEL_BG);
        float selectedRow = currentTab;
        if (tabSwitchAnim != null) {
            float progress = tabSwitchAnim.get("transition");
            selectedRow = tabFromPosition + (currentTab - tabFromPosition) * progress;
        }
        int indicatorY = y + 10 + Math.round(selectedRow * 28);
        for (int i = 0; i < TAB_LABELS.length; i++) {
            int ty = y + 10 + i * 28;
            boolean sel = i == currentTab;
            boolean hov = !sel && GuiHelper.isMouseInRect(mouseX, mouseY, x, ty, NAV_WIDTH, 18);
            if (sel) {
                GuiHelper.drawRect(x, ty, x + NAV_WIDTH, ty + 18, 0x33222222);
            } else if (hov) {
                GuiHelper.drawRect(x, ty, x + NAV_WIDTH, ty + 18, 0x1AFFFFFF);
            }
            int textColor = sel ? primaryColor : hov ? 0xFFCCCCCC : 0xFF888888;
            int textW = PassFontUtil.getStringWidth(TAB_LABELS[i]);
            PassFontUtil.drawStringWithShadow(TAB_LABELS[i], x + (NAV_WIDTH - textW) / 2 + ox, ty + 5, textColor);
        }
        GuiHelper.drawRect(x + 2 + ox, indicatorY, x + 5 + ox, indicatorY + 18, primaryColor);
    }

    private boolean isHovered(int x, int y, int w, int h) {
        if (!GuiHelper.isMouseInRect(hoverMouseX, hoverMouseY, x, y, w, h)) return false;
        return purchasePreviewOpen || selectedRewardCard != null || showSettlement
                || !isPointCoveredByRightPanel(hoverMouseX, hoverMouseY);
    }

    private boolean isPointCoveredByRightPanel(int mouseX, int mouseY) {
        int top = getGuiTop() + TOP_BAR_HEIGHT;
        int height = guiH - TOP_BAR_HEIGHT;
        int visibleW = getRenderedRightPanelWidth();
        int panelX = getGuiLeft() + guiW - visibleW;
        int arrowX = panelX - 16;
        int arrowY = top + height / 2 - 12;
        return (visibleW > 0 && GuiHelper.isMouseInRect(mouseX, mouseY, panelX, top, visibleW, height))
                || GuiHelper.isMouseInRect(mouseX, mouseY, arrowX, arrowY, 16, 24);
    }

    private void drawActionButton(int x, int y, int w, int h, int color, int highlight) {
        GuiHelper.drawRect(x, y, x + w, y + h, color);
        if (isHovered(x, y, w, h)) {
            GuiHelper.drawRect(x, y, x + w, y + h, 0x38FFFFFF);
            GuiHelper.drawRect(x, y, x + w, y + 2, highlight);
            GuiHelper.drawRect(x, y + h - 1, x + w, y + h, highlight);
        }
    }

    private int taskListTop() {
        return 75;
    }

    private int[] taskCategoryBounds(int x, int y, int w, int index) {
        int gap = 6;
        int buttonW = Math.max(1, (w - 20 - gap * 2) / 3);
        return new int[] {x + 10 + index * (buttonW + gap), y + 7, buttonW, 26};
    }

    // ─── Right Panel ───────────────────────────────────────────

    private int getEffectiveRightPanelWidth() {
        if (compactLayout) return 0;
        return getRenderedRightPanelWidth();
    }

    private int getRightPanelMaximumWidth() {
        return compactLayout
                ? Math.min(RIGHT_PANEL_WIDTH, guiW - NAV_WIDTH - 20)
                : RIGHT_PANEL_WIDTH;
    }

    private int getRenderedRightPanelWidth() {
        return AdaptiveLayout.visiblePanelWidth(
                Math.max(0, getRightPanelMaximumWidth()), rightPanelSlide);
    }

    private int getContentWidth() {
        return guiW - NAV_WIDTH - getEffectiveRightPanelWidth();
    }

    private int getPurchasePopupWidth() { return Math.min(420, guiW - 20); }
    private int getPurchasePopupHeight() { return Math.min(340, guiH - 20); }
    private int getRewardPopupWidth() { return Math.min(380, guiW - 20); }
    private int getRewardPopupHeight() { return Math.min(280, guiH - 20); }
    private int getSettlementWidth() { return Math.min(500, guiW - 20); }
    private int getSettlementHeight() { return Math.min(380, guiH - 20); }

    private void drawRightPanel(int guiLeft, int guiTop, int mouseX, int mouseY) {
        int panelW = getRightPanelMaximumWidth();
        int visibleW = getRenderedRightPanelWidth();
        int x = guiLeft + guiW - visibleW, y = guiTop + TOP_BAR_HEIGHT;
        int h = guiH - TOP_BAR_HEIGHT;

        // Toggle arrow button
        int arrowX = x - 16, arrowY = y + h / 2 - 12;
        boolean arrowHov = GuiHelper.isMouseInRect(mouseX, mouseY, arrowX, arrowY, 16, 24);
        GuiHelper.drawRect(arrowX, arrowY, arrowX + 16, arrowY + 24, arrowHov ? 0xDD2A2A2A : 0xDD1A1A1A);
        GuiHelper.drawRect(arrowX, arrowY, arrowX + 1, arrowY + 24, arrowHov ? 0xFF666666 : 0xFF444444);
        GuiHelper.drawRect(arrowX, arrowY, arrowX + 16, arrowY + 1, arrowHov ? 0xFF666666 : 0xFF444444);
        GuiHelper.drawRect(arrowX, arrowY + 23, arrowX + 16, arrowY + 24, arrowHov ? 0xFF666666 : 0xFF444444);
        String arrow = rightPanelCollapsed ? "<" : ">";
        PassFontUtil.drawCenteredString(arrow, arrowX + 8, arrowY + 8, arrowHov ? 0xFFFFFFFF : primaryColor);

        if (visibleW < 2) return;

        enableUiScissor(guiLeft + guiW - visibleW, y, visibleW, h);
        try {
        GuiHelper.drawRect(x, y, x + panelW, y + h, PANEL_BG);
        GuiHelper.drawRect(x, y, x + 1, y + h, 0xFF333333);
        if (snapshot == null) return;

        int ty = y + 12;
        PassFontUtil.drawStringWithShadow("关键奖励", x + 10, ty, primaryColor);
        ty += 18;
        if (snapshot.keyRewards != null) {
            for (PassSnapshot.KeyReward kr : snapshot.keyRewards) {
                String line = "等级 " + kr.level + " " + kr.displayName;
                int c = "legendary".equals(kr.rarity) || "season".equals(kr.rarity) ? primaryColor : 0xFFCCCCCC;
                PassFontUtil.drawString(PassFontUtil.truncateString(line, panelW - 20), x + 10, ty, c);
                ty += 14;
            }
        }
        ty += 10;
        PassFontUtil.drawStringWithShadow("可领取", x + 10, ty, primaryColor);
        ty += 14;
        if (snapshot.playerState != null && snapshot.playerState.claimableCount > 0) {
            PassFontUtil.drawStringWithShadow("共 " + snapshot.playerState.claimableCount + " 项", x + 10, ty, 0xFFCCCCCC);
            ty += 14;
        }

        if (snapshot.playerState != null && snapshot.playerState.isMaxLevel) {
            PassFontUtil.drawStringWithShadow("循环宝箱: " + snapshot.playerState.overflowCount,
                x + 10, ty, accentColor);
            ty += 14;
            if (snapshot.playerState.overflowCount > 0) {
                boolean hovered = GuiHelper.isMouseInRect(mouseX, mouseY, x + 8, ty, panelW - 16, 20);
                GuiHelper.drawRect(x + 8, ty, x + panelW - 8, ty + 20,
                        hovered ? 0xFF3B5750 : 0xFF283833);
                if (hovered) GuiHelper.drawRect(x + 8, ty, x + panelW - 8, ty + 1, accentColor);
                PassFontUtil.drawCenteredString("领取循环宝箱", x + panelW / 2, ty + 5,
                        hovered ? 0xFFFFFFFF : accentColor);
                ty += 26;
            }
        }

        ty += 10;
        String tierText = "档位: " + PassText.tier(
            snapshot.playerState != null ? snapshot.playerState.paidTier : "free");
        PassFontUtil.drawStringWithShadow(tierText, x + 10, ty, accentColor);
        ty += 24;

        if (snapshot.playerState != null && !"advanced".equals(snapshot.playerState.paidTier)
            && !"legendary".equals(snapshot.playerState.paidTier)) {
            boolean advHov = GuiHelper.isMouseInRect(mouseX, mouseY, x + 8, ty, panelW - 16, 20);
            GuiHelper.drawRect(x + 8, ty, x + panelW - 8, ty + 20, advHov ? 0xFF444444 : 0xFF333333);
            if (advHov) GuiHelper.drawRect(x + 8, ty, x + panelW - 8, ty + 1, primaryColor);
            PassFontUtil.drawCenteredString("进阶通行证", x + panelW / 2, ty + 5, advHov ? 0xFFFFFFFF : primaryColor);
            ty += 28;
        }
        if (snapshot.playerState != null && !"legendary".equals(snapshot.playerState.paidTier)) {
            boolean legHov = GuiHelper.isMouseInRect(mouseX, mouseY, x + 8, ty, panelW - 16, 20);
            int bc = purchaseAnim != null && purchaseAnim.get("trackLightUp") > 0.5f ? 0xFF443300 : (legHov ? 0xFF444444 : 0xFF333333);
            GuiHelper.drawRect(x + 8, ty, x + panelW - 8, ty + 20, bc);
            if (legHov) GuiHelper.drawRect(x + 8, ty, x + panelW - 8, ty + 1, 0xFFFFCC00);
            PassFontUtil.drawCenteredString("典藏升级", x + panelW / 2, ty + 5, legHov ? 0xFFFFFFFF : 0xFFFFCC00);
        }
        } finally {
            disableUiScissor();
        }
    }

    // ─── Purchase Preview Popup (P2) ───────────────────────────

    private void drawPurchasePreview(int guiLeft, int guiTop) {
        int pw = getPurchasePopupWidth(), ph = getPurchasePopupHeight();
        int px = guiLeft + (guiW - pw) / 2, py = guiTop + (guiH - ph) / 2;

        GuiHelper.drawRect(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, 0xCC000000);
        GuiHelper.drawRect(px, py, px + pw, py + ph, 0xFF1A1A1A);
        GuiHelper.drawRect(px, py, px + pw, py + 1, primaryColor);
        GuiHelper.drawRect(px, py + ph - 1, px + pw, py + ph, primaryColor);

        boolean isLegendary = "legendary".equals(purchasePreviewTier);
        String title = isLegendary ? "典藏通行证" : "进阶通行证";
        int titleColor = isLegendary ? 0xFFFFCC00 : primaryColor;

        PassFontUtil.drawStringWithShadow(title, px + 14, py + 10, titleColor);
        PassFontUtil.drawStringWithShadow("购买后可立即补领已达等级的奖励", px + 14, py + 28, 0xFFAAAAAA);

        // Show retro-claimable rewards
        if (snapshot != null && snapshot.rewardWindow != null && snapshot.playerState != null) {
            int iy = py + 52;
            int count = 0;
            int maxVisible = Math.max(1, Math.min(8, (ph - 110) / 14));
            for (PassSnapshot.RewardCard card : snapshot.rewardWindow) {
                if (card.level > snapshot.playerState.level) continue;
                boolean matches = isLegendary
                    ? ("advanced".equals(card.track) || "legendary".equals(card.track))
                    : "advanced".equals(card.track);
                if (!matches || !"locked".equals(card.status)) continue;

                if (count >= maxVisible) {
                    PassFontUtil.drawStringWithShadow("... 等更多奖励", px + 14, iy, 0xFF888888);
                    break;
                }

                int lc = "legendary".equals(card.rarity) ? 0xFFFFCC00 :
                    "season".equals(card.rarity) ? primaryColor : 0xFFCCCCCC;
                PassFontUtil.drawStringWithShadow("等级 " + card.level + " " + card.displayName, px + 14, iy, lc);
                iy += 14;
                count++;
            }
        }

        // Buttons
        int btnY = py + ph - 36;
        drawActionButton(px + 10, btnY, 110, 24, primaryColor, 0xFFFFFFFF);
        PassFontUtil.drawCenteredString("确认购买", px + 65, btnY + 7, 0xFF000000);

        drawActionButton(px + pw - 80, btnY, 70, 24, 0xFF333333, primaryColor);
        PassFontUtil.drawCenteredString("取消", px + pw - 45, btnY + 7, 0xFFCCCCCC);
    }

    // ─── Home Tab ──────────────────────────────────────────────

    private void drawHomeTab(int guiLeft, int guiTop) {
        int x = guiLeft + NAV_WIDTH, y = guiTop + TOP_BAR_HEIGHT;
        int w = getContentWidth();
        int h = guiH - TOP_BAR_HEIGHT;
        GuiHelper.drawRect(x, y, x + w, y + h, PANEL_BG);

        int cx = x + w / 2, cy = y + h / 2;
        float radarScale = openAnim != null ? openAnim.get("radarScale") : 1f;
        int maxRadarRadius = Math.max(24, Math.min(100, Math.min(w / 2 - 18, h / 2 - 38)));
        int rr = (int) (maxRadarRadius * radarScale);

        drawCircle(cx, cy, rr, 0x33555555);
        if (rr > 10) drawCircle(cx, cy, rr - 10, 0x22444444);
        if (rr > 30) drawCircle(cx, cy, rr - 30, 0x11333333);
        drawCircle(cx, cy, 4, primaryColor);
        if (radarScale > 0.5f) {
            GuiHelper.drawHorizontalLine(cx - rr, cx + rr, cy, 0x44333333);
            GuiHelper.drawVerticalLine(cx, cy - rr, cy + rr, 0x44333333);
            // Diagonal crosshairs
            for (int i = -rr; i <= rr; i += 4) {
                GuiHelper.drawRect(cx + i, cy + i, cx + i + 1, cy + i + 1, 0x22333333);
                GuiHelper.drawRect(cx + i, cy - i, cx + i + 1, cy - i + 1, 0x22333333);
            }
            // Tick marks on outer ring
            for (int deg = 0; deg < 360; deg += 15) {
                double rad = Math.toRadians(deg);
                int tx = cx + (int) (Math.cos(rad) * (rr - 2));
                int ty = cy + (int) (Math.sin(rad) * (rr - 2));
                GuiHelper.drawRect(tx, ty, tx + 1, ty + 1, 0x55666666);
            }
        }

        if (snapshot != null && snapshot.playerState != null) {
            String lv = String.valueOf(snapshot.playerState.level);
            PassFontUtil.drawCenteredString(lv, cx, cy - 30, primaryColor);
            if (snapshot.playerState.isMaxLevel) {
                String ov = "循环 " + snapshot.playerState.overflowExp + "/" + snapshot.playerState.overflowExpRequired;
                PassFontUtil.drawCenteredString(ov, cx, cy + 22, accentColor);
            }
        }

        // Story nodes
        if (snapshot != null && snapshot.seasonInfo != null && snapshot.seasonInfo.storyNodes != null) {
            int playerLv = snapshot.playerState != null ? snapshot.playerState.level : 1;
            for (PassSnapshot.StoryNode node : snapshot.seasonInfo.storyNodes) {
                float angle = (node.unlockLevel / 80f) * (float) (Math.PI * 2) - (float) Math.PI / 2;
                int nr = (int) (rr * 0.85f);
                int nx = cx + (int) (Math.cos(angle) * nr);
                int ny = cy + (int) (Math.sin(angle) * nr);
                int nc = node.unlockLevel <= playerLv ? primaryColor : 0xFF444444;
                drawCircle(nx, ny, 5, nc);
                PassFontUtil.drawCenteredString(node.title, nx, ny + 10,
                    node.unlockLevel <= playerLv ? 0xFFCCCCCC : 0xFF666666);
            }
        }

        int[][] actions = homeActionBounds(x, y, w, h);
        drawHomeAction(actions[0], "查看奖励", primaryColor, true);
        drawHomeAction(actions[1], "查看任务", accentColor, false);

        int recY = y + h - 34;
        PassFontUtil.drawStringWithShadow("任务进度", x + 10, recY, primaryColor);
        String progressText = snapshot != null && snapshot.taskSummary != null
                ? "每日 " + snapshot.taskSummary.dailyCompleted + "/" + snapshot.taskSummary.dailyTotal
                    + "  每周 " + snapshot.taskSummary.weeklyCompleted + "/" + snapshot.taskSummary.weeklyTotal
                : "每日、每周与赛季任务自动接取";
        PassFontUtil.drawStringWithShadow(
                PassFontUtil.truncateString(
                        progressText, Math.max(40, w - 90)),
                x + 80, recY, 0xFFAAAAAA);

        float cf = openAnim != null ? openAnim.get("contentFade") : 1f;
        if (cf < 1f) GuiHelper.drawRect(x, y, x + w, y + h, ((int) ((1 - cf) * 200) << 24) | 0x000000);
    }

    private int[][] homeActionBounds(int x, int y, int w, int h) {
        int cx = x + w / 2, cy = y + h / 2;
        int radius = Math.max(24, Math.min(100, Math.min(w / 2 - 18, h / 2 - 38)));
        int buttonW = Math.max(48, Math.min(116, w / 2 - radius - 14));
        int buttonH = 46;
        return new int[][] {
                {cx - radius - buttonW - 6, cy - buttonH / 2, buttonW, buttonH},
                {cx + radius + 6, cy - buttonH / 2, buttonW, buttonH}
        };
    }

    private void drawHomeAction(int[] bounds, String label, int color, boolean reward) {
        int x = bounds[0], y = bounds[1], w = bounds[2], h = bounds[3];
        boolean hovered = isHovered(x, y, w, h);
        int border = hovered ? color : (color & 0x00FFFFFF) | 0x88000000;
        int backgroundTop = hovered ? 0xEE293334 : 0xE6191E20;
        int backgroundBottom = hovered ? 0xEE1D2526 : 0xE6101415;

        // A compact chamfered "control pod" that visually docks with the radar.
        GuiHelper.drawRect(x + 5, y, x + w - 5, y + h, border);
        GuiHelper.drawRect(x, y + 5, x + w, y + h - 5, border);
        GuiHelper.drawGradientRect(x + 6, y + 1, x + w - 6, y + h - 1,
                backgroundTop, backgroundBottom);
        GuiHelper.drawGradientRect(x + 1, y + 6, x + w - 1, y + h - 6,
                backgroundTop, backgroundBottom);
        GuiHelper.drawRect(x + 7, y + 4, x + w - 7, y + 6, hovered ? color : 0x55444444);

        int iconX = x + Math.min(19, Math.max(13, w / 5));
        int iconY = y + h / 2;
        if (w >= 78) {
            if (reward) {
                drawDiamond(iconX, iconY, hovered ? 8 : 7, hovered ? color : 0xAA8A7330);
                drawDiamond(iconX, iconY, 3, color);
            } else {
                int lineColor = hovered ? color : 0xAA47757E;
                for (int i = -1; i <= 1; i++) {
                    int ly = iconY + i * 6;
                    GuiHelper.drawRect(iconX - 7, ly - 1, iconX - 4, ly + 2, color);
                    GuiHelper.drawRect(iconX - 1, ly, iconX + 8, ly + 1, lineColor);
                }
            }
        }

        int textX = x + (w < 78 ? w / 2 : 32);
        int textW = w < 78 ? w : w - 35;
        PassFontUtil.drawCenteredString(PassFontUtil.truncateString(label, textW - 4),
                textX + textW / 2, y + 13, hovered ? 0xFFFFFFFF : color);
        if (w >= 78) {
            String hint = reward ? "赛季轨道" : "周期目标";
            PassFontUtil.drawCenteredString(hint, textX + textW / 2, y + 27,
                    hovered ? 0xFFCCD7D9 : 0xFF707A7C);
        }
        int arrowX = x + w - 7;
        GuiHelper.drawRect(arrowX - 2, iconY - 3, arrowX, iconY - 1, hovered ? color : 0xFF666666);
        GuiHelper.drawRect(arrowX, iconY - 1, arrowX + 2, iconY + 1, hovered ? color : 0xFF666666);
        GuiHelper.drawRect(arrowX - 2, iconY + 1, arrowX, iconY + 3, hovered ? color : 0xFF666666);
    }

    private void drawCircle(int cx, int cy, int radius, int color) {
        for (int i = -radius; i <= radius; i++) {
            int hh = (int) Math.sqrt(radius * radius - i * i);
            GuiHelper.drawHorizontalLine(cx + i - 1, cx + i + 1, cy - hh, color);
            GuiHelper.drawHorizontalLine(cx + i - 1, cx + i + 1, cy + hh, color);
        }
    }

    private void drawDiamond(int cx, int cy, int size, int color) {
        for (int i = 0; i <= size; i++) {
            GuiHelper.drawHorizontalLine(cx - i, cx + i, cy - size + i, color);
            GuiHelper.drawHorizontalLine(cx - i, cx + i, cy + size - i, color);
        }
    }

    // ─── Rewards Tab ───────────────────────────────────────────

    private void drawRewardsTab(int guiLeft, int guiTop) {
        int x = guiLeft + NAV_WIDTH, y = guiTop + TOP_BAR_HEIGHT;
        int w = getContentWidth();
        int h = guiH - TOP_BAR_HEIGHT;
        GuiHelper.drawRect(x, y, x + w, y + h, PANEL_BG);

        if (snapshot == null || snapshot.rewardWindow == null) return;

        int labelW = 58;
        int cardW = 72;
        int cardSpacing = cardW + 4;
        int trackAreaX = x + labelW;
        int trackAreaW = w - labelW - 10;
        int currentLv = snapshot.playerState != null ? snapshot.playerState.level : 1;
        int rowH = (h - 40) / 3;
        int cardH = Math.min(88, rowH - 8);

        int maxLevel = 1;
        for (PassSnapshot.RewardCard card : snapshot.rewardWindow) {
            if (card.level > maxLevel) maxLevel = card.level;
        }

        float pixelOffset = rewardRenderOffset;
        int firstVisibleLevel = Math.max(1, (int)(pixelOffset / cardSpacing) + 1);
        int visibleCount = trackAreaW / cardSpacing + 2;
        int lastVisibleLevel = Math.min(maxLevel, firstVisibleLevel + visibleCount);

        String[] tracks = {"free", "advanced", "legendary"};
        String[] trackNames = {"免费", "进阶", "典藏"};
        int[] trackColors = {0xFF888888, accentColor, 0xFFFFCC00};

        enableUiScissor(x, y, w, h);
        // Draw cards first
        for (int r = 0; r < 3; r++) {
            int rowY = y + 28 + r * rowH;
            GuiHelper.drawRect(trackAreaX, rowY + rowH / 2, trackAreaX + trackAreaW, rowY + rowH / 2 + 1, 0x33555555);

            for (int lv = firstVisibleLevel; lv <= lastVisibleLevel; lv++) {
                float cx = trackAreaX + (lv - 1) * cardSpacing - pixelOffset;
                if (cx + cardW < trackAreaX || cx > trackAreaX + trackAreaW) continue;

                if (r == 0) {
                    boolean isCur = lv == currentLv;
                    PassFontUtil.drawCenteredString(String.valueOf(lv), (int)cx + cardW / 2, y + 18,
                        isCur ? primaryColor : 0xFF666666);
                }

                PassSnapshot.RewardCard card = findCardForTrackLevel(tracks[r], lv);
                if (card != null)
                    drawRewardCard((int)cx, rowY + (rowH - cardH) / 2, cardW, cardH, card);
                else
                    GuiHelper.drawRect((int)cx + 2, rowY + (rowH - cardH) / 2 + 2,
                        (int)cx + cardW - 2, rowY + (rowH + cardH) / 2 - 2, 0x22333333);

                if (lv == currentLv && r == 0) {
                    GuiHelper.drawRect((int)cx - 1, y + 26, (int)cx + cardW + 1, y + 27, primaryColor);
                }
            }
        }

        // Draw left label overlay ON TOP of cards to block overflow
        GuiHelper.drawRect(x, y, x + labelW, y + h, PANEL_BG);
        PassFontUtil.drawStringWithShadow("奖励轨道", x + 4, y + 10, primaryColor);
        for (int r = 0; r < 3; r++) {
            int rowY = y + 28 + r * rowH;
            GuiHelper.drawRect(x + 4, rowY + 2, x + labelW - 2, rowY + rowH - 4, 0xFF111111);
            GuiHelper.drawRect(x + 4, rowY + 2, x + 6, rowY + rowH - 4, trackColors[r]);
            PassFontUtil.drawCenteredString(trackNames[r], x + labelW / 2, rowY + rowH / 2 - 4, trackColors[r]);
        }

        disableUiScissor();
        GuiHelper.drawRect(x, y + h - 22, x + w, y + h, PANEL_BG);
        PassFontUtil.drawCenteredString("滚轮或左键拖动  |  单击详情  |  右键领取", x + w / 2, y + h - 14, 0xFF666666);
    }

    private PassSnapshot.RewardCard findCardForTrackLevel(String track, int level) {
        if (snapshot == null || snapshot.rewardWindow == null) return null;
        for (PassSnapshot.RewardCard card : snapshot.rewardWindow) {
            if (card.level == level && track.equals(card.track)) return card;
        }
        return null;
    }

    private void drawRewardCard(int cx, int cy, int cw, int ch, PassSnapshot.RewardCard card) {
        int borderColor = 0xFF444444;
        if ("legendary".equals(card.rarity)) borderColor = 0xFFFFCC00;
        else if ("season".equals(card.rarity)) borderColor = primaryColor;
        else if ("epic".equals(card.rarity)) borderColor = 0xFFAA44FF;
        else if ("rare".equals(card.rarity)) borderColor = accentColor;
        if ("claimable".equals(card.status)) borderColor = primaryColor;

        boolean hovered = !rewardDragged && isRewardTrackPoint(hoverMouseX, hoverMouseY)
                && isHovered(cx, cy, cw, ch);
        GuiHelper.drawRect(cx, cy, cx + cw, cy + ch, hovered ? 0xEE303030 : 0xCC222222);
        if (hovered) borderColor = 0xFFFFFFFF;
        GuiHelper.drawRect(cx, cy, cx + cw, cy + 1, borderColor);
        GuiHelper.drawRect(cx, cy, cx + 1, cy + ch, borderColor);
        GuiHelper.drawRect(cx, cy + ch - 1, cx + cw, cy + ch, borderColor);
        GuiHelper.drawRect(cx + cw - 1, cy, cx + cw, cy + ch, borderColor);

        int tc = 0xFF888888; String tl = "免费";
        if ("advanced".equals(card.track)) { tl = "进阶"; tc = accentColor; }
        else if ("legendary".equals(card.track)) { tl = "典藏"; tc = 0xFFFFCC00; }
        PassFontUtil.drawCenteredString(tl, cx + cw / 2, cy + 4, tc);

        if ("legendary".equals(card.rarity) || "season".equals(card.rarity))
            GuiHelper.drawRect(cx + cw - 10, cy + 4, cx + cw - 2, cy + 10, 0xFFFFCC00);

        String name = PassFontUtil.truncateString(card.displayName, cw - 10);
        PassFontUtil.drawCenteredString(name, cx + cw / 2, cy + ch / 2 - 4, 0xFFCCCCCC);

        if ("claimed".equals(card.status))
            PassFontUtil.drawCenteredString("已领取", cx + cw / 2, cy + ch - 16, 0xFF557755);
        else if ("claimable".equals(card.status)) {
            PassFontUtil.drawCenteredString("可领取", cx + cw / 2, cy + ch - 16, primaryColor);
            if (System.currentTimeMillis() % 1000 < 500)
                GuiHelper.drawRect(cx + 2, cy + ch - 18, cx + cw - 2, cy + ch - 2, 0x22333300);
        } else if ("locked".equals(card.status))
            PassFontUtil.drawCenteredString("未解锁", cx + cw / 2, cy + ch - 16, 0xFF555555);

        if (card.clientTags != null && !card.clientTags.isEmpty())
            PassFontUtil.drawCenteredString(PassFontUtil.truncateString(card.clientTags.get(0), cw - 6),
                cx + cw / 2, cy + ch - 28, 0xFFAAAAAA);
    }

    // ─── Reward Detail Popup ───────────────────────────────────

    private void drawRewardDetailPopup(int guiLeft, int guiTop) {
        int pw = getRewardPopupWidth(), ph = getRewardPopupHeight();
        int px = guiLeft + (guiW - pw) / 2, py = guiTop + (guiH - ph) / 2;
        GuiHelper.drawRect(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, 0xCC000000);
        GuiHelper.drawRect(px, py, px + pw, py + ph, 0xFF1A1A1A);
        GuiHelper.drawRect(px, py, px + pw, py + 1, primaryColor);
        GuiHelper.drawRect(px, py + ph - 1, px + pw, py + ph, primaryColor);

        PassSnapshot.RewardCard card = selectedRewardCard;
        int rarityGlow = "legendary".equals(card.rarity) ? 0x44FFCC00
            : "season".equals(card.rarity) ? primaryColor & 0x44FFFFFF
            : "epic".equals(card.rarity) ? 0x44AA44FF
            : "rare".equals(card.rarity) ? 0x4455D6FF : 0x22444444;
        String rl = PassText.rarity(card.rarity);
        int rc = "legendary".equals(card.rarity) ? 0xFFFFCC00 : "season".equals(card.rarity) ? primaryColor :
            "epic".equals(card.rarity) ? 0xFFAA44FF : "rare".equals(card.rarity) ? accentColor : 0xFF888888;

        boolean compactPopup = pw < 360 || ph < 250;
        int ix;
        if (!compactPopup) {
            int preX = px + 10, preY = py + 10, preW = 150, preH = Math.min(180, ph - 60);
            GuiHelper.drawRect(preX, preY, preX + preW, preY + preH, 0xFF0F0F0F);
            GuiHelper.drawRect(preX, preY, preX + preW, preY + 1, 0xFF444444);
            int radius = Math.min(50, preH / 3);
            drawCircle(preX + preW / 2, preY + preH / 2, radius, rarityGlow);
            drawCircle(preX + preW / 2, preY + preH / 2, Math.max(8, radius - 20), rarityGlow);
            drawDiamond(preX + preW / 2, preY + preH / 2, 18, rarityGlow | 0xFF000000);
            PassFontUtil.drawStringWithShadow(rl, preX + 4, preY + 4, rc);
            ix = px + 175;
        } else {
            ix = px + 14;
        }

        int iy = py + 14;
        PassFontUtil.drawStringWithShadow("奖励详情", ix, iy, primaryColor); iy += 22;
        PassFontUtil.drawStringWithShadow(
                PassFontUtil.truncateString(card.displayName, px + pw - ix - 14),
                ix, iy, 0xFFFFFFFF); iy += 20;
        PassFontUtil.drawStringWithShadow("等级: " + card.level, ix, iy, 0xFFCCCCCC); iy += 16;
        PassFontUtil.drawStringWithShadow("稀有度: " + PassText.rarity(card.rarity), ix, iy, rc); iy += 16;
        PassFontUtil.drawStringWithShadow("轨道: " + PassText.track(card.track), ix, iy, 0xFFCCCCCC); iy += 16;
        String st = statusText(card.status);
        PassFontUtil.drawStringWithShadow("状态: " + st, ix, iy,
            "claimable".equals(card.status) ? primaryColor : 0xFFCCCCCC); iy += 22;
        if (card.clientTags != null) for (String t : card.clientTags) {
            if (iy > py + ph - 52) break;
            PassFontUtil.drawStringWithShadow(t, ix, iy, 0xFF888888); iy += 14;
        }

        int btnY = py + ph - 30;
        drawActionButton(px + pw - 70, btnY, 60, 20, 0xFF333333, primaryColor);
        PassFontUtil.drawCenteredString("关闭", px + pw - 40, btnY + 5, 0xFFCCCCCC);
        if ("claimable".equals(card.status)) {
            drawActionButton(px + 10, btnY, 80, 20, primaryColor, 0xFFFFFFFF);
            PassFontUtil.drawCenteredString("领取奖励", px + 50, btnY + 5, 0xFF000000);
        }
    }

    private String statusText(String s) {
        switch (s) { case "claimed": return "已领取"; case "claimable": return "可领取"; case "locked": return "未解锁"; default: return "未知状态"; }
    }

    // ─── Tasks Tab ──────────────────────────────────────────────

    private void drawTasksTab(int guiLeft, int guiTop) {
        int x = guiLeft + NAV_WIDTH, y = guiTop + TOP_BAR_HEIGHT;
        int w = getContentWidth();
        int h = guiH - TOP_BAR_HEIGHT;
        GuiHelper.drawRect(x, y, x + w, y + h, PANEL_BG);

        for (int i = 0; i < TASK_CATEGORIES.length; i++) {
            int[] b = taskCategoryBounds(x, y, w, i);
            boolean selected = i == taskCategory;
            boolean hovered = isHovered(b[0], b[1], b[2], b[3]);
            int border = selected ? primaryColor : hovered ? accentColor : 0xFF555555;
            int background = selected ? 0xDD403514 : hovered ? 0xDD30383A : 0xCC222222;
            GuiHelper.drawRect(b[0], b[1], b[0] + b[2], b[1] + b[3], border);
            GuiHelper.drawRect(b[0] + 1, b[1] + 1, b[0] + b[2] - 1, b[1] + b[3] - 1, background);
            if (selected || hovered) GuiHelper.drawRect(b[0] + 3, b[1] + 3,
                    b[0] + b[2] - 3, b[1] + 5, border);
            PassFontUtil.drawCenteredString(TASK_CATEGORIES[i], b[0] + b[2] / 2, b[1] + 10,
                    selected ? 0xFFFFFFFF : hovered ? 0xFFFFFFFF : 0xFFAAAAAA);
        }
        // Hidden task toggle
        {
            boolean hovered = isHovered(x + w - 50, y + 40, 40, 16);
            if (hovered) GuiHelper.drawRect(x + w - 50, y + 40, x + w - 10, y + 56, 0x33FFFFFF);
            int color = showHiddenTasks ? 0xFFAA44FF : hovered ? 0xFFFFFFFF : 0xFF555555;
            PassFontUtil.drawStringWithShadow("隐藏", x + w - 50, y + 42, color);
        }
        int listTop = taskListTop();
        GuiHelper.drawHorizontalLine(x, x + w, y + listTop - 12, 0xFF444444);

        if (snapshot != null && snapshot.taskSummary != null) {
            String sum = "每日 " + snapshot.taskSummary.dailyCompleted + "/" + snapshot.taskSummary.dailyTotal
                + "  每周 " + snapshot.taskSummary.weeklyCompleted + "/" + snapshot.taskSummary.weeklyTotal
                + "  赛季 " + snapshot.taskSummary.seasonCompleted + "/" + snapshot.taskSummary.seasonTotal;
            PassFontUtil.drawStringWithShadow(
                    PassFontUtil.truncateString(sum, Math.max(80, w - 70)),
                    x + 10, y + 43, 0xFFAAAAAA);
        }

        List<PassSnapshot.TrackedTask> filtered = buildFilteredTasks();

        int sy = y + listTop - (int) taskRenderOffset;
        enableUiScissor(x, y + listTop - 8, w, Math.max(0, h - listTop - 10));
        for (int i = 0; i < filtered.size(); i++) {
            PassSnapshot.TrackedTask t = filtered.get(i);
            int ty = sy + i * 48;
            if (ty < y + listTop - 32 || ty > y + h) continue;
            drawTaskCard(x + 10, ty, w - 20, 42, t);
        }
        disableUiScissor();
        GuiHelper.drawRect(x, y + h - 22, x + w, y + h, PANEL_BG);
        PassFontUtil.drawCenteredString("任务自动接取  |  完成后手动领取", x + w / 2, y + h - 14, 0xFF666666);
    }

    private void drawTaskCard(int cx, int cy, int cw, int ch, PassSnapshot.TrackedTask t) {
        int bg = t.progress >= t.targetValue ? 0xCC1A2A1A : t.isHidden ? 0xCC1A0A2A : 0xCC1A1A1A;
        GuiHelper.drawRect(cx, cy, cx + cw, cy + ch, bg);
        GuiHelper.drawRect(cx, cy, cx + cw, cy + 1, t.isLimited ? accentColor : 0xFF444444);

        // Hidden legacy tasks retain their reveal badge; regular quests are auto-started.
        int bx = cx + 6;
        if (t.isHidden) {
            PassFontUtil.drawStringWithShadow("隐藏", bx, cy + 4, 0xFFAA44FF);
            bx += 34;
        }

        PassFontUtil.drawStringWithShadow(PassFontUtil.truncateString(t.title, Math.max(20, cx + cw - bx - 76)), bx, cy + 4,
            t.progress >= t.targetValue ? 0xFF55AA55 : 0xFFCCCCCC);

        float pct = t.targetValue > 0 ? (float) t.progress / t.targetValue : 0;
        int fc = t.progress >= t.targetValue ? 0xFF55AA55 : primaryColor;
        GuiHelper.drawProgressBar(cx + 6, cy + 22, cw - 100, 8, Math.min(pct, 1f), 0xFF333333, fc);

        String ps = t.progress + "/" + t.targetValue;
        PassFontUtil.drawString(ps, cx + cw - PassFontUtil.getStringWidth(ps) - 10, cy + 22, 0xFF888888);

        if (t.progress >= t.targetValue) {
            if (t.manualClaim && !t.claimed) {
                drawActionButton(cx + cw - 70, cy + 4, 60, 14, 0xFF335533, 0xFF55DD77);
                PassFontUtil.drawCenteredString("领取", cx + cw - 40, cy + 6,
                        isHovered(cx + cw - 70, cy + 4, 60, 14) ? 0xFFFFFFFF : 0xFF55DD77);
            } else {
                GuiHelper.drawRect(cx + cw - 70, cy + 4, cx + cw - 10, cy + 18, 0xFF335533);
                PassFontUtil.drawCenteredString(t.manualClaim ? "已领取" : "已完成", cx + cw - 40, cy + 6, 0xFF55AA55);
            }
        } else if (t.gotoAction != null) {
            drawActionButton(cx + cw - 70, cy + 4, 60, 14, 0xFF333333, primaryColor);
            PassFontUtil.drawCenteredString("前往", cx + cw - 40, cy + 6,
                    isHovered(cx + cw - 70, cy + 4, 60, 14) ? 0xFFFFFFFF : primaryColor);
        }
    }

    // ─── Settlement Screen (P2) ────────────────────────────────

    private void drawSettlementScreen(int guiLeft, int guiTop) {
        int sw = getSettlementWidth(), sh = getSettlementHeight();
        int sx = guiLeft + (guiW - sw) / 2, sy = guiTop + (guiH - sh) / 2;
        GuiHelper.drawRect(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, 0xDD000000);
        GuiHelper.drawRect(sx, sy, sx + sw, sy + sh, 0xFF1A1A1A);
        GuiHelper.drawRect(sx, sy, sx + sw, sy + 1, primaryColor);
        GuiHelper.drawRect(sx, sy + sh - 1, sx + sw, sy + sh, primaryColor);

        PassFontUtil.drawStringWithShadow("赛季结算", sx + sw / 2 - 30, sy + 14, primaryColor);
        if (snapshot != null && snapshot.seasonInfo != null) {
            PassFontUtil.drawStringWithShadow(snapshot.seasonInfo.displayName, sx + 14, sy + 36, 0xFFFFFFFF);
            PassFontUtil.drawStringWithShadow(snapshot.seasonInfo.subtitle, sx + 14, sy + 52, 0xFFAAAAAA);
        }

        int iy = sy + 80;
        if (snapshot != null && snapshot.playerState != null) {
            PassFontUtil.drawStringWithShadow("最终等级: " + snapshot.playerState.level, sx + 20, iy, 0xFFFFFFFF); iy += 20;
            PassFontUtil.drawStringWithShadow("累计经验: " + snapshot.playerState.totalExp, sx + 20, iy, 0xFFCCCCCC); iy += 20;
            PassFontUtil.drawStringWithShadow("付费档位: " + PassText.tier(snapshot.playerState.paidTier), sx + 20, iy, accentColor); iy += 20;
            if (snapshot.playerState.isMaxLevel)
                PassFontUtil.drawStringWithShadow("循环宝箱: " + snapshot.playerState.overflowCount + " 个", sx + 20, iy, accentColor);
            iy += 20;
        }

        if (snapshot != null && snapshot.taskSummary != null) {
            iy += 8;
            PassFontUtil.drawStringWithShadow("任务完成统计", sx + 20, iy, primaryColor); iy += 18;
            PassFontUtil.drawStringWithShadow("每日: " + snapshot.taskSummary.dailyCompleted + "/" + snapshot.taskSummary.dailyTotal,
                sx + 30, iy, 0xFFCCCCCC); iy += 16;
            PassFontUtil.drawStringWithShadow("每周: " + snapshot.taskSummary.weeklyCompleted + "/" + snapshot.taskSummary.weeklyTotal,
                sx + 30, iy, 0xFFCCCCCC); iy += 16;
            PassFontUtil.drawStringWithShadow("赛季: " + snapshot.taskSummary.seasonCompleted + "/" + snapshot.taskSummary.seasonTotal,
                sx + 30, iy, 0xFFCCCCCC); iy += 16;
        }

        iy += 12;
        PassFontUtil.drawCenteredString("赛季奖励已结算，奖杯和称号已发放至账户。", sx + sw / 2, iy, 0xFFAAAAAA);
        iy += 20;
        PassFontUtil.drawCenteredString("赛季记录已保存于服务端", sx + sw / 2, iy, accentColor);

        int btnY = sy + sh - 36;
        drawActionButton(sx + sw / 2 - 40, btnY, 80, 24, primaryColor, 0xFFFFFFFF);
        PassFontUtil.drawCenteredString("关闭", sx + sw / 2, btnY + 7, 0xFF000000);
    }

    // ─── Mouse & Keyboard ──────────────────────────────────────

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        updateAdaptiveLayout();
        mouseX = toUiCoordinate(mouseX);
        mouseY = toUiCoordinate(mouseY);
        int gl = getGuiLeft(), gt = getGuiTop();

        // Settlement screen
        if (showSettlement) {
            int sw = getSettlementWidth(), sh = getSettlementHeight();
            int sx = gl + (guiW - sw) / 2, sy = gt + (guiH - sh) / 2;
            if (GuiHelper.isMouseInRect(mouseX, mouseY, sx + sw / 2 - 40, sy + sh - 36, 80, 24)) {
                showSettlement = false;
            }
            return;
        }

        // Purchase preview
        if (purchasePreviewOpen) {
            int pw = getPurchasePopupWidth(), ph = getPurchasePopupHeight();
            int px = gl + (guiW - pw) / 2, py = gt + (guiH - ph) / 2;
            int btnY = py + ph - 36;
            if (GuiHelper.isMouseInRect(mouseX, mouseY, px + 10, btnY, 110, 24)) {
                if ("legendary".equals(purchasePreviewTier)) handler.sendBuyLegendary();
                else handler.sendBuyAdvanced();
                return;
            }
            if (GuiHelper.isMouseInRect(mouseX, mouseY, px + pw - 80, btnY, 70, 24)) {
                purchasePreviewOpen = false; return;
            }
            if (!GuiHelper.isMouseInRect(mouseX, mouseY, px, py, pw, ph)) purchasePreviewOpen = false;
            return;
        }

        // Reward detail popup
        if (selectedRewardCard != null) {
            int pw = getRewardPopupWidth(), ph = getRewardPopupHeight();
            int px = gl + (guiW - pw) / 2, py = gt + (guiH - ph) / 2;
            int btnY = py + ph - 30;
            if (GuiHelper.isMouseInRect(mouseX, mouseY, px + pw - 70, btnY, 60, 20)) { selectedRewardCard = null; return; }
            if ("claimable".equals(selectedRewardCard.status)
                && GuiHelper.isMouseInRect(mouseX, mouseY, px + 10, btnY, 80, 20)) {
                handler.sendClaimReward(selectedRewardCard.rewardId); selectedRewardCard = null; return;
            }
            if (!GuiHelper.isMouseInRect(mouseX, mouseY, px, py, pw, ph)) selectedRewardCard = null;
            return;
        }

        // Left nav
        int nX = gl, nY = gt + TOP_BAR_HEIGHT;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            if (GuiHelper.isMouseInRect(mouseX, mouseY, nX, nY + 10 + i * 28, NAV_WIDTH, 18)) {
                changeTab(i);
                return;
            }
        }

        // Right panel toggle arrow
        int rpVisibleW = getRenderedRightPanelWidth();
        int rpH = guiH - TOP_BAR_HEIGHT;
        int arrowX = gl + guiW -rpVisibleW - 16;
        int arrowY = gt + TOP_BAR_HEIGHT + rpH / 2 - 12;
        if (GuiHelper.isMouseInRect(mouseX, mouseY, arrowX, arrowY, 16, 24)) {
            rightPanelCollapsed = !rightPanelCollapsed; return;
        }

        // Right panel buy buttons
        if (rightPanelCollapsed || rightPanelSlide > 0.01f) { /* wait until the panel is fully open */ }
        else {
        int renderedRightWidth = getRightPanelMaximumWidth();
        int rpX = gl + guiW - renderedRightWidth;
        int btnY = gt + TOP_BAR_HEIGHT + 12 + 18;
        if (snapshot != null && snapshot.keyRewards != null) btnY += snapshot.keyRewards.size() * 14;
        btnY += 10 + 14;
        if (snapshot != null && snapshot.playerState != null && snapshot.playerState.claimableCount > 0) btnY += 14;
        if (snapshot != null && snapshot.playerState != null && snapshot.playerState.isMaxLevel) {
            btnY += 14;
            if (snapshot.playerState.overflowCount > 0) {
                if (mouseButton == 0 && GuiHelper.isMouseInRect(mouseX, mouseY,
                        rpX + 8, btnY, renderedRightWidth - 16, 20)) {
                    handler.sendClaimOverflow();
                    return;
                }
                btnY += 26;
            }
        }
        btnY += 10 + 14 + 24;

        if (mouseButton == 0) {
            if (snapshot != null && snapshot.playerState != null
                && !"advanced".equals(snapshot.playerState.paidTier)
                && !"legendary".equals(snapshot.playerState.paidTier)) {
                if (GuiHelper.isMouseInRect(mouseX, mouseY, rpX + 8, btnY, renderedRightWidth - 16, 20)) {
                    purchasePreviewTier = "advanced"; purchasePreviewOpen = true; return;
                }
                btnY += 28;
            }
            if (snapshot != null && snapshot.playerState != null
                && !"legendary".equals(snapshot.playerState.paidTier)) {
                if (GuiHelper.isMouseInRect(mouseX, mouseY, rpX + 8, btnY, renderedRightWidth - 16, 20)) {
                    purchasePreviewTier = "legendary"; purchasePreviewOpen = true; return;
                }
            }
        }
        }

        // The slide-out panel and its arrow are above the page, at every layout size.
        if (isPointCoveredByRightPanel(mouseX, mouseY)) return;

        // Claim all
        if (snapshot != null && snapshot.playerState != null && snapshot.playerState.claimableCount > 0) {
            String cs = compactLayout
                    ? "领取(" + snapshot.playerState.claimableCount + ")"
                    : "一键领取 (" + snapshot.playerState.claimableCount + ")";
            int cw = PassFontUtil.getStringWidth(cs);
            int hitW = compactLayout ? cw + 10 : cw + 8;
            int bx = compactLayout ? gl + guiW - hitW - 8 : gl + guiW - cw - 16;
            int hitH = compactLayout ? 18 : 16;
            if (GuiHelper.isMouseInRect(mouseX, mouseY, bx, gt + 14, hitW, hitH)) {
                handler.sendClaimAll();
                return;
            }
        }

        if (currentTab == TAB_HOME && mouseButton == 0) {
            int[][] actions = homeActionBounds(gl + NAV_WIDTH, gt + TOP_BAR_HEIGHT,
                    getContentWidth(), guiH - TOP_BAR_HEIGHT);
            if (GuiHelper.isMouseInRect(mouseX, mouseY,
                    actions[0][0], actions[0][1], actions[0][2], actions[0][3])) {
                changeTab(TAB_REWARDS);
                return;
            }
            if (GuiHelper.isMouseInRect(mouseX, mouseY,
                    actions[1][0], actions[1][1], actions[1][2], actions[1][3])) {
                changeTab(TAB_TASKS);
                return;
            }
        }

        // Task category tabs + hidden toggle
        if (currentTab == TAB_TASKS) {
            int tx = gl + NAV_WIDTH, ty = gt + TOP_BAR_HEIGHT;
            for (int i = 0; i < TASK_CATEGORIES.length; i++) {
                int[] b = taskCategoryBounds(tx, ty, getContentWidth(), i);
                if (GuiHelper.isMouseInRect(mouseX, mouseY, b[0], b[1], b[2], b[3])) {
                    taskCategory = i; taskScrollOffset = 0; taskScrollVelocity = 0; taskRenderOffset = 0; showHiddenTasks = false; return;
                }
            }
            // Hidden toggle
            if (GuiHelper.isMouseInRect(mouseX, mouseY, tx + getContentWidth() - 50, ty + 40, 40, 16)) {
                showHiddenTasks = !showHiddenTasks;
                if (showHiddenTasks && hiddenRevealAnim == null) {
                    hiddenRevealIndex = 0;
                    hiddenRevealAnim = PassAnimation.claimRewardEffect();
                }
                if (showHiddenTasks && snapshot != null && snapshot.hiddenTasks != null
                    && hiddenRevealIndex < snapshot.hiddenTasks.size() - 1) {
                    hiddenRevealIndex++;
                }
                return;
            }

            // Task goto buttons
            List<PassSnapshot.TrackedTask> filtered = buildFilteredTasks();
            int listTop = taskListTop();
            int sy = ty + listTop - (int) taskRenderOffset;
            for (int i = 0; i < filtered.size(); i++) {
                PassSnapshot.TrackedTask t = filtered.get(i);
                int tcy = sy + i * 48;
                if (tcy < ty + listTop - 8 || tcy + 42 > ty + guiH - TOP_BAR_HEIGHT - 22) {
                    continue;
                }
                if ((t.manualClaim && !t.claimed && t.progress >= t.targetValue)
                        || (t.gotoAction != null && t.progress < t.targetValue)) {
                    int gx = tx + 10 + (getContentWidth() - 20) - 70;
                    if (GuiHelper.isMouseInRect(mouseX, mouseY, gx, tcy + 4, 60, 14)) {
                        if (t.manualClaim && t.progress >= t.targetValue) handler.sendClaimTask(t.taskId);
                        else handler.sendGotoAction(t.gotoAction);
                        return;
                    }
                }
            }
        }

        // Left press is deferred until release so a card can be dragged without opening it.
        if (currentTab == TAB_REWARDS && isRewardTrackPoint(mouseX, mouseY)) {
            PassSnapshot.RewardCard card = rewardCardAt(mouseX, mouseY);
            if (mouseButton == 1 && card != null && "claimable".equals(card.status)) {
                handler.sendClaimReward(card.rewardId);
                return;
            }
            if (mouseButton == 0) {
                rewardPointerDown = true;
                rewardDragged = false;
                rewardPointerStartX = mouseX;
                rewardPointerLastX = mouseX;
                rewardPointerLastTime = System.currentTimeMillis();
                pendingRewardCard = card;
                rewardScrollVelocity = 0;
                return;
            }
        }
    }

    private void changeTab(int tab) {
        if (currentTab == tab) return;
        tabFromPosition = tabSwitchAnim == null ? currentTab
                : tabFromPosition + (currentTab - tabFromPosition) * tabSwitchAnim.get("transition");
        currentTab = tab;
        tabSwitchAnim = PassAnimation.tabSwitchEffect();
        rewardPointerDown = false;
        rewardDragged = false;
        pendingRewardCard = null;
        handler.sendChangeTab(TAB_LABELS[tab]);
    }

    private boolean isRewardTrackPoint(int mouseX, int mouseY) {
        if (snapshot == null || snapshot.rewardWindow == null || isPointCoveredByRightPanel(mouseX, mouseY))
            return false;
        int x = getGuiLeft() + NAV_WIDTH + 58;
        int y = getGuiTop() + TOP_BAR_HEIGHT + 28;
        return GuiHelper.isMouseInRect(mouseX, mouseY, x, y,
                Math.max(0, getContentWidth() - 68), Math.max(0, guiH - TOP_BAR_HEIGHT - 50));
    }

    private PassSnapshot.RewardCard rewardCardAt(int mouseX, int mouseY) {
        if (!isRewardTrackPoint(mouseX, mouseY)) return null;
        int rx = getGuiLeft() + NAV_WIDTH, ry = getGuiTop() + TOP_BAR_HEIGHT;
        int rowH = (guiH - TOP_BAR_HEIGHT - 40) / 3;
        int cardH = Math.min(88, rowH - 8);
        int trackX = rx + 58;
        String[] tracks = {"free", "advanced", "legendary"};
        for (int row = 0; row < 3; row++) {
            int cardY = ry + 28 + row * rowH + (rowH - cardH) / 2;
            if (!GuiHelper.isMouseInRect(mouseX, mouseY, trackX, cardY,
                    getContentWidth() - 68, cardH)) continue;
            for (PassSnapshot.RewardCard card : snapshot.rewardWindow) {
                if (!tracks[row].equals(card.track)) continue;
                int cardX = (int) (trackX + (card.level - 1) * 76 - rewardRenderOffset);
                if (GuiHelper.isMouseInRect(mouseX, mouseY, cardX, cardY, 72, cardH)) return card;
            }
        }
        return null;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (!rewardPointerDown || clickedMouseButton != 0 || currentTab != TAB_REWARDS) return;
        int x = toUiCoordinate(mouseX);
        if (Math.abs(x - rewardPointerStartX) >= 4) rewardDragged = true;
        if (!rewardDragged) return;
        long now = System.currentTimeMillis();
        int dx = x - rewardPointerLastX;
        long elapsed = Math.max(1L, now - rewardPointerLastTime);
        float maxScroll = maxRewardScroll();
        float next = rewardScrollOffset - dx;
        if (next < 0f) next = Math.max(-28f, next * 0.35f);
        else if (next > maxScroll) next = Math.min(maxScroll + 28f,
                maxScroll + (next - maxScroll) * 0.35f);
        rewardScrollOffset = next;
        float instantaneous = Math.max(-68f, Math.min(68f, (-dx * 50f) / elapsed));
        rewardScrollVelocity = rewardScrollVelocity * 0.58f + instantaneous * 0.42f;
        rewardSpringBack = false;
        rewardPointerLastX = x;
        rewardPointerLastTime = now;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (!rewardPointerDown || state != 0) return;
        int x = toUiCoordinate(mouseX), y = toUiCoordinate(mouseY);
        PassSnapshot.RewardCard clicked = !rewardDragged ? rewardCardAt(x, y) : null;
        if (clicked != null && clicked == pendingRewardCard) {
            selectedRewardCard = clicked;
            handler.sendRequestRewardPreview(clicked.rewardId);
        }
        if (rewardDragged) {
            long idle = System.currentTimeMillis() - rewardPointerLastTime;
            if (idle > 90L) rewardScrollVelocity *= Math.max(0f, 1f - (idle - 90L) / 140f);
            if (rewardScrollOffset < 0f || rewardScrollOffset > maxRewardScroll()) {
                rewardScrollVelocity = 0f;
                rewardSpringBack = true;
            }
            rewardLastScrollTime = System.currentTimeMillis();
        }
        rewardPointerDown = false;
        rewardDragged = false;
        pendingRewardCard = null;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;
        if (purchasePreviewOpen || selectedRewardCard != null || showSettlement) return;
        updateAdaptiveLayout();
        int gl = getGuiLeft(), gt = getGuiTop();
        int mx = toUiCoordinate(Mouse.getEventX() * width / mc.displayWidth);
        int my = toUiCoordinate(height - Mouse.getEventY() * height / mc.displayHeight - 1);

        float impulse = scroll > 0 ? -24f : 24f;

        if (isPointCoveredByRightPanel(mx, my)) return;

        int cx = gl + NAV_WIDTH, cy = gt + TOP_BAR_HEIGHT;
        int cw = getContentWidth(), ch = guiH - TOP_BAR_HEIGHT;
        if (GuiHelper.isMouseInRect(mx, my, cx, cy, cw, ch)) {
            if (currentTab == TAB_REWARDS) {
                rewardScrollVelocity += impulse;
                rewardSpringBack = false;
                rewardLastScrollTime = System.currentTimeMillis();
            } else if (currentTab == TAB_TASKS) {
                taskScrollVelocity += impulse;
                taskSpringBack = false;
                taskLastScrollTime = System.currentTimeMillis();
            }
            return;
        }
    }

    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void onGuiClosed() { super.onGuiClosed(); handler.sendCloseClient(); }

    // ─── Smooth Scroll Physics ──────────────────────────────────

    private void updateAllScrollPhysics() {
        long now = System.currentTimeMillis();
        updateScroll(now, true);
    }

    private void updateScroll(long now, boolean all) {
        // Reward track scroll
        {
            if (rewardLastRenderTime == 0) { rewardLastRenderTime = now; rewardRenderOffset = rewardScrollOffset; }
            float dt = (now - rewardLastRenderTime) / 50.0f;
            if (dt <= 0) dt = 0.05f;
            dt = Math.min(3f, dt);
            rewardLastRenderTime = now;
            float maxScroll = maxRewardScroll();
            if (rewardPointerDown && rewardDragged) {
                // Follow the pointer through a damped target instead of snapping
                // each mouse event directly onto the rendered track.
                float follow = 1f - (float) Math.pow(0.22f, Math.min(3f, dt));
                rewardRenderOffset += (rewardScrollOffset - rewardRenderOffset) * follow;
            } else if (rewardSpringBack) {
                float target = rewardScrollOffset < 0 ? 0 : (rewardScrollOffset > maxScroll ? maxScroll : rewardScrollOffset);
                float diff = target - rewardScrollOffset;
                if (Math.abs(diff) < 0.3f) { rewardScrollOffset = target; rewardSpringBack = false; }
                else rewardScrollOffset += diff * (1f - (float) Math.pow(0.62f, Math.min(3f, dt)));
            } else if (Math.abs(rewardScrollVelocity) > 0.3f) {
                rewardScrollOffset += rewardScrollVelocity * dt;
                rewardScrollVelocity *= (float) Math.pow(0.90, dt);
                if (rewardScrollOffset < 0 || rewardScrollOffset > maxScroll) { rewardScrollVelocity = 0; rewardSpringBack = true; }
            } else { rewardScrollVelocity = 0; }
            if (!(rewardPointerDown && rewardDragged)) {
                float smoothing = 1f - (float) Math.pow(0.35f, Math.min(3f, dt));
                rewardRenderOffset += (rewardScrollOffset - rewardRenderOffset) * smoothing;
            }
            if (Math.abs(rewardScrollOffset - rewardRenderOffset) < 0.05f) rewardRenderOffset = rewardScrollOffset;
        }
        // Task scroll
        {
            if (taskLastRenderTime == 0) { taskLastRenderTime = now; taskRenderOffset = taskScrollOffset; }
            float dt = (now - taskLastRenderTime) / 50.0f;
            if (dt <= 0) dt = 0.05f;
            dt = Math.min(3f, dt);
            taskLastRenderTime = now;
            int taskCount = buildFilteredTasks().size();
            float maxScroll = Math.max(0, taskCount * 48 - (guiH - TOP_BAR_HEIGHT - taskListTop() - 10));
            if (taskSpringBack) {
                float target = taskScrollOffset < 0 ? 0 : (taskScrollOffset > maxScroll ? maxScroll : taskScrollOffset);
                float diff = target - taskScrollOffset;
                if (Math.abs(diff) < 0.3f) { taskScrollOffset = target; taskSpringBack = false; }
                else taskScrollOffset += diff * 0.3f * dt;
            } else if (Math.abs(taskScrollVelocity) > 0.3f) {
                taskScrollOffset += taskScrollVelocity * dt;
                taskScrollVelocity *= (float) Math.pow(0.88, dt);
                if (taskScrollOffset < 0 || taskScrollOffset > maxScroll) { taskScrollVelocity = 0; taskSpringBack = true; }
            } else { taskScrollVelocity = 0; }
            taskRenderOffset += (taskScrollOffset - taskRenderOffset) * Math.min(1.0f, dt * 0.5f);
            if (Math.abs(taskScrollOffset - taskRenderOffset) < 0.05f) taskRenderOffset = taskScrollOffset;
        }
    }

    // ─── Helpers ───────────────────────────────────────────────

    private List<PassSnapshot.TrackedTask> buildFilteredTasks() {
        List<PassSnapshot.TrackedTask> f = new ArrayList<>();
        String group = taskCategory == 0 ? "daily" : taskCategory == 1 ? "weekly" : "season";
        if (showHiddenTasks && snapshot != null && snapshot.hiddenTasks != null) {
            for (int i = 0; i <= hiddenRevealIndex && i < snapshot.hiddenTasks.size(); i++)
                if (group.equals(snapshot.hiddenTasks.get(i).group)) f.add(snapshot.hiddenTasks.get(i));
        } else if (snapshot != null && snapshot.trackedTasks != null) {
            for (PassSnapshot.TrackedTask t : snapshot.trackedTasks)
                if (group.equals(t.group)) f.add(t);
        }
        return f;
    }

    private float maxRewardScroll() {
        int maxLevel = 1;
        if (snapshot != null && snapshot.rewardWindow != null) {
            for (PassSnapshot.RewardCard card : snapshot.rewardWindow)
                if (card.level > maxLevel) maxLevel = card.level;
        }
        return Math.max(0f, maxLevel * 76f - Math.max(0, getContentWidth() - 68));
    }

    private String formatRemainingTime(long ms) {
        if (ms <= 0) return "已结束";
        long days = ms / 86400000L, hours = (ms % 86400000L) / 3600000L;
        if (days > 0) return days + "天 " + hours + "小时";
        return hours + "小时 " + (ms % 3600000) / 60000 + "分钟";
    }
}
