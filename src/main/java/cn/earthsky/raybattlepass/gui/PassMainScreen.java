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
    private static final int TAB_CURRENCY = 3, TAB_PARTNERS = 4, TAB_ARCHIVE = 5;

    private static final String[] TAB_LABELS = {
        "首页", "奖励", "任务", "赛季币", "伙伴", "归档"
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

    // Smooth scroll state - task list
    private float taskScrollOffset;
    private float taskScrollVelocity;
    private float taskRenderOffset;
    private boolean taskSpringBack;
    private long taskLastScrollTime;
    private long taskLastRenderTime;

    private int taskCategory = 0;
    private static final String[] TASK_CATEGORIES = {
        "每日任务", "每周任务", "赛季任务", "限时任务", "公会协作", "已追踪"
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
    private long openStartTimeMs;

    // Adaptive layout dimensions
    private int guiW, guiH;
    private float uiScale = 1f;
    private boolean compactLayout;
    private boolean layoutInitialized;
    private float archiveScrollOffset;

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

        int uiMouseX = toUiCoordinate(mouseX);
        int uiMouseY = toUiCoordinate(mouseY);
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

        switch (currentTab) {
            case TAB_HOME:    drawHomeTab(guiLeft, guiTop); break;
            case TAB_REWARDS: drawRewardsTab(guiLeft, guiTop); break;
            case TAB_TASKS:   drawTasksTab(guiLeft, guiTop); break;
            case TAB_CURRENCY:drawCurrencyTab(guiLeft, guiTop); break;
            case TAB_PARTNERS:drawPartnersTab(guiLeft, guiTop); break;
            case TAB_ARCHIVE: drawArchiveTab(guiLeft, guiTop); break;
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
                GuiHelper.drawRect(bx, y + 14, bx + claimW, y + 32, primaryColor);
                PassFontUtil.drawCenteredString(claim, bx + claimW / 2, y + 19, 0xFF000000);
            } else if (snapshot.currencySummary != null) {
                String currency = String.valueOf(snapshot.currencySummary.currentValue);
                int currencyW = PassFontUtil.getStringWidth(currency);
                PassFontUtil.drawStringWithShadow(
                        currency, x + w - currencyW - 10, y + 20, accentColor);
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
                float op = snapshot.playerState.overflowExpRequired > 0
                    ? (float) snapshot.playerState.overflowExp / snapshot.playerState.overflowExpRequired : 0;
                GuiHelper.drawProgressBar(lx + 50, y + 18, 120, 8, Math.min(op, 1f), 0xFF333333, accentColor);
            } else {
                String expStr = snapshot.playerState.exp + "/" + snapshot.playerState.expToNextLevel;
                PassFontUtil.drawStringWithShadow(expStr, lx, y + 28, 0xFFAAAAAA);
                float p = snapshot.playerState.expToNextLevel > 0
                    ? (float) snapshot.playerState.exp / snapshot.playerState.expToNextLevel : 0;
                GuiHelper.drawProgressBar(lx + 50, y + 18, 120, 8, p, 0xFF333333,
                    levelUpAnim != null ? 0xFFFF6600 : primaryColor);
            }
        }

        if (snapshot.currencySummary != null) {
            String cs = snapshot.currencySummary.displayName + " " + snapshot.currencySummary.currentValue;
            int cw = PassFontUtil.getStringWidth(cs);
            PassFontUtil.drawStringWithShadow(cs, x + w - cw - 100, y + 18, accentColor);
        }

        if (snapshot.playerState != null && snapshot.playerState.claimableCount > 0) {
            String claimStr = "一键领取 (" + snapshot.playerState.claimableCount + ")";
            int cw = PassFontUtil.getStringWidth(claimStr);
            int bx = x + w - cw - 16;
            GuiHelper.drawRect(bx, y + 14, bx + cw + 8, y + 30, primaryColor);
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
        for (int i = 0; i < TAB_LABELS.length; i++) {
            int ty = y + 10 + i * 28;
            boolean sel = i == currentTab;
            boolean hov = !sel && GuiHelper.isMouseInRect(mouseX, mouseY, x, ty, NAV_WIDTH, 18);
            if (sel) {
                GuiHelper.drawRect(x + 2 + ox, ty, x + 5 + ox, ty + 18, primaryColor);
                GuiHelper.drawRect(x, ty, x + NAV_WIDTH, ty + 18, 0x33222222);
            } else if (hov) {
                GuiHelper.drawRect(x, ty, x + NAV_WIDTH, ty + 18, 0x1AFFFFFF);
            }
            int textColor = sel ? primaryColor : hov ? 0xFFCCCCCC : 0xFF888888;
            int textW = PassFontUtil.getStringWidth(TAB_LABELS[i]);
            PassFontUtil.drawStringWithShadow(TAB_LABELS[i], x + (NAV_WIDTH - textW) / 2 + ox, ty + 5, textColor);
        }
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
        rightPanelSlide = AdaptiveLayout.nextPanelSlide(rightPanelSlide, rightPanelCollapsed);

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
        GuiHelper.drawRect(px + 10, btnY, px + 120, btnY + 24, primaryColor);
        PassFontUtil.drawCenteredString("确认购买", px + 65, btnY + 7, 0xFF000000);

        GuiHelper.drawRect(px + pw - 80, btnY, px + pw - 10, btnY + 24, 0xFF333333);
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

        int recY = y + h - 34;
        PassFontUtil.drawStringWithShadow("今日推荐", x + 10, recY, primaryColor);
        PassFontUtil.drawStringWithShadow(
                PassFontUtil.truncateString(
                        "肉鸽玩法1次 / 派遣伙伴远征 / 岛屿入侵",
                        Math.max(40, w - 90)),
                x + 80, recY, 0xFFAAAAAA);

        float cf = openAnim != null ? openAnim.get("contentFade") : 1f;
        if (cf < 1f) GuiHelper.drawRect(x, y, x + w, y + h, ((int) ((1 - cf) * 200) << 24) | 0x000000);
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
        PassFontUtil.drawCenteredString("滚轮浏览  |  左键详情  |  右键领取", x + w / 2, y + h - 14, 0xFF666666);
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

        GuiHelper.drawRect(cx, cy, cx + cw, cy + ch, 0xCC222222);
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
        GuiHelper.drawRect(px + pw - 70, btnY, px + pw - 10, btnY + 20, 0xFF333333);
        PassFontUtil.drawCenteredString("关闭", px + pw - 40, btnY + 5, 0xFFCCCCCC);
        if ("claimable".equals(card.status)) {
            GuiHelper.drawRect(px + 10, btnY, px + 90, btnY + 20, primaryColor);
            PassFontUtil.drawCenteredString("领取奖励", px + 50, btnY + 5, 0xFF000000);
        }
    }

    private String statusText(String s) {
        switch (s) { case "claimed": return "已领取"; case "claimable": return "可领取"; case "locked": return "未解锁"; default: return "未知状态"; }
    }

    // ─── Tasks Tab (P2: limited/guild/hidden) ──────────────────

    private void drawTasksTab(int guiLeft, int guiTop) {
        int x = guiLeft + NAV_WIDTH, y = guiTop + TOP_BAR_HEIGHT;
        int w = getContentWidth();
        int h = guiH - TOP_BAR_HEIGHT;
        GuiHelper.drawRect(x, y, x + w, y + h, PANEL_BG);

        // Category tabs - compact layout, two rows
        for (int i = 0; i < Math.min(TASK_CATEGORIES.length, 3); i++) {
            int cx = x + 10 + i * 82;
            int color = i == taskCategory ? primaryColor : 0xFF888888;
            PassFontUtil.drawStringWithShadow(TASK_CATEGORIES[i], cx, y + 8, color);
        }
        for (int i = 3; i < TASK_CATEGORIES.length; i++) {
            int cx = x + 10 + (i - 3) * 82;
            int color = i == taskCategory ? primaryColor : 0xFF888888;
            PassFontUtil.drawStringWithShadow(TASK_CATEGORIES[i], cx, y + 24, color);
        }
        // Hidden task toggle
        {
            int color = showHiddenTasks ? 0xFFAA44FF : 0xFF555555;
            PassFontUtil.drawStringWithShadow("隐藏", x + w - 50, y + 8, color);
        }
        GuiHelper.drawHorizontalLine(x, x + w, y + 40, 0xFF444444);

        if (snapshot != null && snapshot.taskSummary != null) {
            String sum = "每日 " + snapshot.taskSummary.dailyCompleted + "/" + snapshot.taskSummary.dailyTotal
                + "  每周 " + snapshot.taskSummary.weeklyCompleted + "/" + snapshot.taskSummary.weeklyTotal
                + "  赛季 " + snapshot.taskSummary.seasonCompleted + "/" + snapshot.taskSummary.seasonTotal
                + "  公会 " + snapshot.taskSummary.guildCompleted + "/" + snapshot.taskSummary.guildTotal;
            PassFontUtil.drawStringWithShadow(
                    PassFontUtil.truncateString(sum, Math.max(80, w - 20)),
                    x + 10, y + 46, 0xFFAAAAAA);
        }

        // Collect tasks
        List<PassSnapshot.TrackedTask> filtered = new ArrayList<>();
        if (showHiddenTasks && snapshot != null && snapshot.hiddenTasks != null) {
            // Show hidden tasks (revealed one by one)
            for (int i = 0; i <= hiddenRevealIndex && i < snapshot.hiddenTasks.size(); i++)
                filtered.add(snapshot.hiddenTasks.get(i));
        } else if (snapshot != null && snapshot.trackedTasks != null) {
            String gf = null;
            switch (taskCategory) {
                case 0: gf = "daily"; break; case 1: gf = "weekly"; break;
                case 2: gf = "season"; break; case 3: gf = "limited"; break;
                case 4: gf = "guild"; break;
            }
            for (PassSnapshot.TrackedTask t : snapshot.trackedTasks) {
                if (gf == null || gf.equals(t.group)) filtered.add(t);
            }
        }

        int sy = y + 70 - (int) taskRenderOffset;
        enableUiScissor(x, y + 62, w, Math.max(0, h - 80));
        for (int i = 0; i < filtered.size(); i++) {
            PassSnapshot.TrackedTask t = filtered.get(i);
            int ty = sy + i * 48;
            if (ty < y + 38 || ty > y + h) continue;
            drawTaskCard(x + 10, ty, w - 20, 42, t);
        }
        disableUiScissor();
        PassFontUtil.drawCenteredString("滚轮滚动  |  点击分类切换  |  隐藏任务需触发揭示", x + w / 2, y + h - 14, 0xFF666666);
    }

    private void drawTaskCard(int cx, int cy, int cw, int ch, PassSnapshot.TrackedTask t) {
        int bg = t.progress >= t.targetValue ? 0xCC1A2A1A : t.isHidden ? 0xCC1A0A2A : 0xCC1A1A1A;
        GuiHelper.drawRect(cx, cy, cx + cw, cy + ch, bg);
        GuiHelper.drawRect(cx, cy, cx + cw, cy + 1, t.isLimited ? accentColor : 0xFF444444);

        // Badge: limited / guild / hidden
        int bx = cx + 6;
        if (t.isLimited) {
            String rem = formatLimitedRemaining(t.endTimeMs - System.currentTimeMillis());
            PassFontUtil.drawStringWithShadow("限时 " + rem, bx, cy + 4, accentColor);
            bx += 70;
        } else if (t.isGuild) {
            PassFontUtil.drawStringWithShadow("公会", bx, cy + 4, 0xFF88AA88);
            bx += 34;
        } else if (t.isHidden) {
            PassFontUtil.drawStringWithShadow("隐藏", bx, cy + 4, 0xFFAA44FF);
            bx += 34;
        }

        PassFontUtil.drawStringWithShadow(t.title, bx, cy + 4,
            t.progress >= t.targetValue ? 0xFF55AA55 : 0xFFCCCCCC);

        float pct = t.targetValue > 0 ? (float) t.progress / t.targetValue : 0;
        int fc = t.progress >= t.targetValue ? 0xFF55AA55 : primaryColor;
        GuiHelper.drawProgressBar(cx + 6, cy + 22, cw - 100, 8, Math.min(pct, 1f), 0xFF333333, fc);

        String ps = t.progress + "/" + t.targetValue;
        PassFontUtil.drawString(ps, cx + cw - PassFontUtil.getStringWidth(ps) - 10, cy + 22, 0xFF888888);

        if (t.progress >= t.targetValue) {
            GuiHelper.drawRect(cx + cw - 70, cy + 4, cx + cw - 10, cy + 18, 0xFF335533);
            PassFontUtil.drawCenteredString("已完成", cx + cw - 40, cy + 6, 0xFF55AA55);
        } else if (t.gotoAction != null) {
            GuiHelper.drawRect(cx + cw - 70, cy + 4, cx + cw - 10, cy + 18, 0xFF333333);
            PassFontUtil.drawCenteredString("前往", cx + cw - 40, cy + 6, primaryColor);
        }
    }

    private String formatLimitedRemaining(long ms) {
        if (ms <= 0) return "已结束";
        long h = ms / 3600000L, m = (ms % 3600000L) / 60000;
        if (h > 24) return (h / 24) + "天";
        return h + "小时" + m + "分钟";
    }

    // ─── Currency Tab ──────────────────────────────────────────

    private void drawCurrencyTab(int guiLeft, int guiTop) {
        int x = guiLeft + NAV_WIDTH, y = guiTop + TOP_BAR_HEIGHT;
        int w = getContentWidth();
        int h = guiH - TOP_BAR_HEIGHT;
        GuiHelper.drawRect(x, y, x + w, y + h, PANEL_BG);
        PassFontUtil.drawStringWithShadow("赛季币", x + 10, y + 10, primaryColor);

        if (snapshot != null && snapshot.currencySummary != null) {
            PassSnapshot.CurrencySummary cs = snapshot.currencySummary;
            int cardX = x + 20, cardY = y + 40;
            GuiHelper.drawRect(cardX, cardY, cardX + 200, cardY + 100, 0xFF1A1A1A);
            GuiHelper.drawRect(cardX, cardY, cardX + 200, cardY + 1, accentColor);
            GuiHelper.drawRect(cardX, cardY + 99, cardX + 200, cardY + 100, accentColor);
            PassFontUtil.drawStringWithShadow(cs.displayName, cardX + 10, cardY + 12, 0xFFCCCCCC);
            PassFontUtil.drawStringWithShadow(String.format("%,d", cs.currentValue), cardX + 10, cardY + 46, 0xFFFFFFFF);

            int iy = y + 40;
            int infoWidth = Math.max(80, w - 250);
            PassFontUtil.drawStringWithShadow(PassFontUtil.truncateString(
                "来源: " + PassText.provider(cs.provider), infoWidth), x + 240, iy, 0xFF888888); iy += 20;
            PassFontUtil.drawStringWithShadow(PassFontUtil.truncateString(
                "同步方式: 服务端自动同步", infoWidth), x + 240, iy, 0xFF888888); iy += 20;
            PassFontUtil.drawStringWithShadow(PassFontUtil.truncateString(
                "刷新间隔: " + cs.refreshIntervalTicks + " 游戏刻", infoWidth), x + 240, iy, 0xFF888888);
            PassFontUtil.drawStringWithShadow(
                    PassFontUtil.truncateString(
                            "赛季币由服务端数据接口提供，客户端只负责显示。",
                            Math.max(80, w - 40)),
                    x + 20, y + 160, 0xFF666666);

            // Overflow box info
            if (snapshot.playerState != null && snapshot.playerState.isMaxLevel) {
                PassFontUtil.drawStringWithShadow("满级循环宝箱", x + 20, y + 190, accentColor);
                String ovInfo = "每 " + snapshot.playerState.overflowExpRequired + " 经验获得1个循环宝箱";
                PassFontUtil.drawStringWithShadow(ovInfo, x + 20, y + 208, 0xFFAAAAAA);
                String ovCount = "已获得: " + snapshot.playerState.overflowCount + " 个";
                PassFontUtil.drawStringWithShadow(ovCount, x + 20, y + 224, 0xFFCCCCCC);
                if (snapshot.playerState.overflowCount > 0 && h >= 285) {
                    int buttonColor = 0xFFF2C94C;
                    GuiHelper.drawRect(x + 20, y + 246, x + 130, y + 270, buttonColor);
                    PassFontUtil.drawCenteredString("领取循环宝箱", x + 75, y + 253, 0xFF000000);
                }
            }
        } else {
            PassFontUtil.drawStringWithShadow("赛季币信息不可用，等待服务端下发数据...", x + 20, y + 40, 0xFF888888);
        }
    }

    // ─── Partners Tab ──────────────────────────────────────────

    private void drawPartnersTab(int guiLeft, int guiTop) {
        int x = guiLeft + NAV_WIDTH, y = guiTop + TOP_BAR_HEIGHT;
        int w = getContentWidth();
        int h = guiH - TOP_BAR_HEIGHT;
        GuiHelper.drawRect(x, y, x + w, y + h, PANEL_BG);
        PassFontUtil.drawStringWithShadow("伙伴特辑", x + 10, y + 10, primaryColor);
        PassFontUtil.drawStringWithShadow("S1 故土回声 · 推荐伙伴", x + 10, y + 28, 0xFFAAAAAA);

        if (snapshot != null && snapshot.seasonInfo != null && snapshot.seasonInfo.featuredPartnerIds != null
            && !snapshot.seasonInfo.featuredPartnerIds.isEmpty()) {
            String pid = snapshot.seasonInfo.featuredPartnerIds.get(0);

            int px = x + 20, py = y + 50, pw = 180, ph = Math.min(260, h - 70);
            GuiHelper.drawRect(px, py, px + pw, py + ph, 0xFF0F0F0F);
            GuiHelper.drawRect(px, py, px + pw, py + 1, primaryColor);
            GuiHelper.drawRect(px + pw - 1, py, px + pw, py + ph, primaryColor);
            drawCircle(px + pw / 2, py + ph / 2 - 20, 60, 0x33555555);
            drawCircle(px + pw / 2, py + ph / 2 - 20, 40, 0x22444444);
            drawDiamond(px + pw / 2, py + ph / 2 - 20, 24, primaryColor & 0x66FFFFFF);
            PassFontUtil.drawCenteredString(pid, px + pw / 2, py + ph - 30, 0xFF888888);

            int ix = x + 215, iy = y + 50;
            int infoW = Math.max(180, w - 225);
            GuiHelper.drawRect(ix, iy, ix + infoW, iy + ph, PANEL_BG);
            PassFontUtil.drawStringWithShadow("测试伙伴", ix + 12, iy + 12, 0xFFFFFFFF);
            PassFontUtil.drawStringWithShadow("定位: 医疗 / 支援", ix + 12, iy + 32, accentColor);
            PassFontUtil.drawStringWithShadow("阵营: 地球训练区", ix + 12, iy + 48, 0xFFCCCCCC);
            iy += 68;
            for (String line : new String[]{
                "来自地球训练区的医疗支援专", "家，精通战场急救与远程护盾",
                "技术。在肉鸽玩法和爬塔环境", "中提供持续恢复与减伤辅助。"}) {
                PassFontUtil.drawStringWithShadow(line, ix + 12, iy, 0xFF888888); iy += 14;
            }
            iy += 10;
            for (String s : new String[]{
                "推荐玩法: 肉鸽玩法 / 爬塔 / 岛屿入侵",
                "推荐芯片: 生命回复 / 护盾增强",
                "推荐队友: 星环猎手 / 故土勘探员",
                "获取方式: 通行证10级进阶线",
                "碎片进度: 解锁赛季限定皮肤"}) {
                PassFontUtil.drawStringWithShadow(s, ix + 12, iy, 0xFFAAAAAA); iy += 14;
            }

            if (h >= 410) {
                int sy = y + 330;
                PassFontUtil.drawStringWithShadow("外观与特效", x + 20, sy - 12, primaryColor);
                String[] previews = {"默认外观", "赛季皮肤", "战术待命动作", "医疗光束特效"};
                int previewW = Math.max(70, Math.min(110, (w - 50) / previews.length));
                for (int i = 0; i < previews.length; i++) {
                    int spx = x + 20 + i * (previewW + 10);
                    GuiHelper.drawRect(spx, sy, spx + previewW, sy + 50, 0xFF1A1A1A);
                    GuiHelper.drawRect(spx, sy, spx + previewW, sy + 1, 0xFF444444);
                    PassFontUtil.drawCenteredString(
                            PassFontUtil.truncateString(previews[i], previewW - 6),
                            spx + previewW / 2, sy + 20, 0xFF888888);
                }
            }
        } else {
            PassFontUtil.drawStringWithShadow("暂无本赛季伙伴信息", x + 20, y + 50, 0xFF888888);
            PassFontUtil.drawStringWithShadow("等待服务端下发赛季伙伴数据...", x + 20, y + 70, 0xFF666666);
        }
    }

    // ─── Archive Tab (P2: full past season data) ───────────────

    private void drawArchiveTab(int guiLeft, int guiTop) {
        int x = guiLeft + NAV_WIDTH, y = guiTop + TOP_BAR_HEIGHT;
        int w = getContentWidth();
        int h = guiH - TOP_BAR_HEIGHT;
        GuiHelper.drawRect(x, y, x + w, y + h, PANEL_BG);
        PassFontUtil.drawStringWithShadow("赛季归档", x + 10, y + 10, primaryColor);

        // Current season card
        if (snapshot != null && snapshot.seasonInfo != null) {
            int cy = y + 10;
            GuiHelper.drawRect(x + w - 160, cy, x + w - 10, cy + 18, 0xFF333333);
            PassFontUtil.drawCenteredString("当前赛季", x + w - 85, cy + 4, primaryColor);
        }

        if (snapshot != null && snapshot.archiveSeasons != null && !snapshot.archiveSeasons.isEmpty()) {
            int cardY = y + 40 - (int) archiveScrollOffset;
            enableUiScissor(x, y + 32, w, Math.max(0, h - 82));
            for (PassSnapshot.ArchiveSeason as : snapshot.archiveSeasons) {
                int ch = 110;
                if (cardY + ch < y + 32 || cardY > y + h - 50) {
                    cardY += ch + 8;
                    continue;
                }
                GuiHelper.drawRect(x + 10, cardY, x + w - 10, cardY + ch, 0xFF1A1A1A);
                GuiHelper.drawRect(x + 10, cardY, x + w - 10, cardY + 1, 0xFF444444);

                // Left: emblem
                int ex = x + 20, ey = cardY + 15;
                drawCircle(ex + 35, ey + 35, 30, 0x33555555);
                String emblem = as.displayName == null ? "?" :
                        as.displayName.substring(0, Math.min(3, as.displayName.length()));
                PassFontUtil.drawCenteredString(emblem, ex + 35, ey + 32, 0xFF888888);

                // Center: info
                int ix = ex + 90;
                PassFontUtil.drawStringWithShadow(
                        PassFontUtil.truncateString(as.displayName, Math.max(80, w - 130)),
                        ix, cardY + 12, 0xFFFFFFFF);
                PassFontUtil.drawStringWithShadow(
                        PassFontUtil.truncateString(as.subtitle, Math.max(80, w - 130)),
                        ix, cardY + 28, 0xFFAAAAAA);
                PassFontUtil.drawStringWithShadow("等级: " + as.playerLevel + "/" + as.maxLevel, ix, cardY + 46, 0xFFCCCCCC);
                PassFontUtil.drawStringWithShadow("档位: " + PassText.tier(as.paidTier), ix + 140, cardY + 46, accentColor);
                PassFontUtil.drawStringWithShadow("任务完成: " + as.tasksCompleted + "/" + as.totalTasks, ix, cardY + 62, 0xFFCCCCCC);

                // Trophies
                if (as.earnedTrophies != null && !as.earnedTrophies.isEmpty()) {
                    PassFontUtil.drawStringWithShadow(
                            PassFontUtil.truncateString(
                                    "奖杯: " + String.join(" · ", as.earnedTrophies),
                                    Math.max(80, w - 130)),
                            ix, cardY + 78, primaryColor);
                }

                // Season dates
                String dateRange = formatDate(as.startTimeMs) + " — " + formatDate(as.endTimeMs);
                PassFontUtil.drawStringWithShadow(dateRange, ix, cardY + 94, 0xFF666666);

                cardY += ch + 8;
            }
            disableUiScissor();
            if (getArchiveMaxScroll() > 0) {
                PassFontUtil.drawStringWithShadow("滚轮浏览归档", x + 12, y + h - 16, 0xFF666666);
            }
        } else {
            // Placeholder
            int cX = x + 20, cY = y + 40;
            GuiHelper.drawRect(cX, cY, cX + 300, cY + 80, 0xFF1A1A1A);
            GuiHelper.drawRect(cX, cY, cX + 300, cY + 1, 0xFF444444);
            PassFontUtil.drawStringWithShadow("S1 故土回声 (进行中)", cX + 12, cY + 14, 0xFFCCCCCC);
            PassFontUtil.drawStringWithShadow("赛季结束后将归档至此", cX + 12, cY + 34, 0xFF666666);
            PassFontUtil.drawStringWithShadow("可查看: 奖杯、剧情回顾、赛季数据", cX + 12, cY + 52, 0xFF555555);
        }

        // Current season settlement status
        if (snapshot != null && snapshot.seasonInfo != null && snapshot.seasonInfo.endTimeMs > 0) {
            long rem = snapshot.seasonInfo.endTimeMs - System.currentTimeMillis();
            int sy = y + h - 50;
            if (rem <= 0) {
                GuiHelper.drawRect(x + 10, sy - 4, x + w - 10, sy + 24, 0xFF332200);
                PassFontUtil.drawCenteredString("赛季已结束，可结算赛季奖励", x + w / 2, sy + 6, primaryColor);
            } else if (rem < 7L * 86400000L) {
                PassFontUtil.drawStringWithShadow("赛季即将结束，剩余 " + formatRemainingTime(rem), x + 20, sy + 4, 0xFFAAAAAA);
                PassFontUtil.drawStringWithShadow("赛季结束后可在归档中查看完整赛季记录", x + 20, sy + 20, 0xFF888888);
            }
        }
    }

    private String formatDate(long ms) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd");
        return sdf.format(new java.util.Date(ms));
    }

    private float getArchiveMaxScroll() {
        int count = snapshot != null && snapshot.archiveSeasons != null
                ? snapshot.archiveSeasons.size() : 0;
        int available = Math.max(1, guiH - TOP_BAR_HEIGHT - 96);
        return Math.max(0, count * 118 - available);
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
        PassFontUtil.drawCenteredString("前往赛季归档查看完整记录", sx + sw / 2, iy, accentColor);

        int btnY = sy + sh - 36;
        GuiHelper.drawRect(sx + sw / 2 - 40, btnY, sx + sw / 2 + 40, btnY + 24, primaryColor);
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
                currentTab = i; handler.sendChangeTab(TAB_LABELS[i]); return;
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
        if (snapshot != null && snapshot.playerState != null && snapshot.playerState.isMaxLevel) btnY += 14;
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

        if (compactLayout && getRenderedRightPanelWidth() > 0) {
            int panelWidth = getRenderedRightPanelWidth();
            if (GuiHelper.isMouseInRect(
                    mouseX, mouseY, gl + guiW - panelWidth,
                    gt + TOP_BAR_HEIGHT, panelWidth, guiH - TOP_BAR_HEIGHT)) {
                return;
            }
        }

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

        // Task category tabs + hidden toggle
        if (currentTab == TAB_CURRENCY && snapshot != null && snapshot.playerState != null
                && snapshot.playerState.isMaxLevel && snapshot.playerState.overflowCount > 0) {
            int cx = gl + NAV_WIDTH;
            int cy = gt + TOP_BAR_HEIGHT;
            int contentHeight = guiH - TOP_BAR_HEIGHT;
            if (contentHeight >= 285 && GuiHelper.isMouseInRect(mouseX, mouseY, cx + 20, cy + 246, 110, 24)) {
                handler.sendClaimOverflow();
                return;
            }
        }

        // Task category tabs + hidden toggle
        if (currentTab == TAB_TASKS) {
            int tx = gl + NAV_WIDTH, ty = gt + TOP_BAR_HEIGHT;
            for (int i = 0; i < TASK_CATEGORIES.length; i++) {
                int cx = tx + 10 + (i < 3 ? i : i - 3) * 82;
                int cy = ty + (i < 3 ? 6 : 22);
                if (GuiHelper.isMouseInRect(mouseX, mouseY, cx, cy, 70, 16)) {
                    taskCategory = i; taskScrollOffset = 0; taskScrollVelocity = 0; taskRenderOffset = 0; showHiddenTasks = false; return;
                }
            }
            // Hidden toggle
            if (GuiHelper.isMouseInRect(mouseX, mouseY, tx + (getContentWidth()) - 50, ty + 6, 40, 16)) {
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
            int sy = ty + 70 - (int) taskRenderOffset;
            for (int i = 0; i < filtered.size(); i++) {
                PassSnapshot.TrackedTask t = filtered.get(i);
                int tcy = sy + i * 48;
                if (tcy < ty + 62 || tcy + 42 > ty + guiH - TOP_BAR_HEIGHT - 18) {
                    continue;
                }
                if (t.gotoAction != null && t.progress < t.targetValue) {
                    int gx = tx + 10 + (getContentWidth() - 20) - 70;
                    if (GuiHelper.isMouseInRect(mouseX, mouseY, gx, tcy + 4, 60, 14)) {
                        handler.sendGotoAction(t.gotoAction); return;
                    }
                }
            }
        }

        // Reward tab card clicks
        if (currentTab == TAB_REWARDS && snapshot != null && snapshot.rewardWindow != null) {
            int labelW = 58;
            int cardW = 72;
            int cardSpacing = cardW + 4;
            int rx = gl + NAV_WIDTH;
            int ry = gt + TOP_BAR_HEIGHT;
            int rw = getContentWidth();
            int rh = guiH - TOP_BAR_HEIGHT;
            int trackAreaX = rx + labelW;
            int trackAreaW = rw - labelW - 10;
            int rowH = (rh - 40) / 3;
            int cardH = Math.min(88, rowH - 8);
            String[] tracks = {"free", "advanced", "legendary"};

            int maxLevel = 1;
            for (PassSnapshot.RewardCard card : snapshot.rewardWindow) {
                if (card.level > maxLevel) maxLevel = card.level;
            }
            float pixelOffset = rewardRenderOffset;
            int firstVisibleLevel = Math.max(1, (int)(pixelOffset / cardSpacing) + 1);
            int visibleCount = trackAreaW / cardSpacing + 2;
            int lastVisibleLevel = Math.min(maxLevel, firstVisibleLevel + visibleCount);

            for (int r = 0; r < 3; r++) {
                int rowY = ry + 28 + r * rowH;
                for (int lv = firstVisibleLevel; lv <= lastVisibleLevel; lv++) {
                    int cx = (int)(trackAreaX + (lv - 1) * cardSpacing - pixelOffset);
                    if (cx + cardW < trackAreaX || cx > trackAreaX + trackAreaW) continue;
                    int cy = rowY + (rowH - cardH) / 2;
                    if (GuiHelper.isMouseInRect(mouseX, mouseY, cx, cy, cardW, cardH)) {
                        PassSnapshot.RewardCard card = findCardForTrackLevel(tracks[r], lv);
                        if (card != null) {
                            if (mouseButton == 1 && "claimable".equals(card.status)) handler.sendClaimReward(card.rewardId);
                            else if (mouseButton == 0) { selectedRewardCard = card; handler.sendRequestRewardPreview(card.rewardId); }
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;
        updateAdaptiveLayout();
        int gl = getGuiLeft(), gt = getGuiTop();
        int mx = toUiCoordinate(Mouse.getEventX() * width / mc.displayWidth);
        int my = toUiCoordinate(height - Mouse.getEventY() * height / mc.displayHeight - 1);

        float impulse = scroll > 0 ? -24f : 24f;

        if (compactLayout && getRenderedRightPanelWidth() > 0) {
            int panelWidth = getRenderedRightPanelWidth();
            if (GuiHelper.isMouseInRect(
                    mx, my, gl + guiW - panelWidth,
                    gt + TOP_BAR_HEIGHT, panelWidth, guiH - TOP_BAR_HEIGHT)) {
                return;
            }
        }

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
            } else if (currentTab == TAB_ARCHIVE) {
                archiveScrollOffset += scroll > 0 ? -30f : 30f;
                archiveScrollOffset = Math.max(0, Math.min(getArchiveMaxScroll(), archiveScrollOffset));
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
            rewardLastRenderTime = now;
            int cardSpacing = 76;
            int maxLevel = 1;
            if (snapshot != null && snapshot.rewardWindow != null) {
                for (PassSnapshot.RewardCard card : snapshot.rewardWindow) {
                    if (card.level > maxLevel) maxLevel = card.level;
                }
            }
            float maxScroll = Math.max(0, maxLevel * cardSpacing - getContentWidth() + 60);
            if (rewardSpringBack) {
                float target = rewardScrollOffset < 0 ? 0 : (rewardScrollOffset > maxScroll ? maxScroll : rewardScrollOffset);
                float diff = target - rewardScrollOffset;
                if (Math.abs(diff) < 0.3f) { rewardScrollOffset = target; rewardSpringBack = false; }
                else rewardScrollOffset += diff * 0.3f * dt;
            } else if (Math.abs(rewardScrollVelocity) > 0.3f) {
                rewardScrollOffset += rewardScrollVelocity * dt;
                rewardScrollVelocity *= (float) Math.pow(0.88, dt);
                if (rewardScrollOffset < 0 || rewardScrollOffset > maxScroll) { rewardScrollVelocity = 0; rewardSpringBack = true; }
            } else { rewardScrollVelocity = 0; }
            rewardRenderOffset += (rewardScrollOffset - rewardRenderOffset) * Math.min(1.0f, dt * 0.5f);
            if (Math.abs(rewardScrollOffset - rewardRenderOffset) < 0.05f) rewardRenderOffset = rewardScrollOffset;
        }
        // Task scroll
        {
            if (taskLastRenderTime == 0) { taskLastRenderTime = now; taskRenderOffset = taskScrollOffset; }
            float dt = (now - taskLastRenderTime) / 50.0f;
            if (dt <= 0) dt = 0.05f;
            taskLastRenderTime = now;
            int taskCount = buildFilteredTasks().size();
            float maxScroll = Math.max(0, taskCount * 48 - (guiH - TOP_BAR_HEIGHT - 80));
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
        if (showHiddenTasks && snapshot != null && snapshot.hiddenTasks != null) {
            for (int i = 0; i <= hiddenRevealIndex && i < snapshot.hiddenTasks.size(); i++)
                f.add(snapshot.hiddenTasks.get(i));
        } else if (snapshot != null && snapshot.trackedTasks != null) {
            String gf = null;
            switch (taskCategory) {
                case 0: gf = "daily"; break; case 1: gf = "weekly"; break;
                case 2: gf = "season"; break; case 3: gf = "limited"; break;
                case 4: gf = "guild"; break;
            }
            for (PassSnapshot.TrackedTask t : snapshot.trackedTasks)
                if (gf == null || gf.equals(t.group)) f.add(t);
        }
        return f;
    }

    private String formatRemainingTime(long ms) {
        if (ms <= 0) return "已结束";
        long days = ms / 86400000L, hours = (ms % 86400000L) / 3600000L;
        if (days > 0) return days + "天 " + hours + "小时";
        return hours + "小时 " + (ms % 3600000) / 60000 + "分钟";
    }
}
