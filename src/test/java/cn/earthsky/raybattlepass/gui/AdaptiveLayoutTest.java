package cn.earthsky.raybattlepass.gui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdaptiveLayoutTest {

    @Test
    public void keepsLargeAndCommonWindowsAtReadableScale() {
        assertLayoutFits(1920, 1080);
        assertLayoutFits(1280, 720);
        assertLayoutFits(854, 480);

        assertTrue(AdaptiveLayout.calculate(854, 480).scale == 1f);
        assertFalse(AdaptiveLayout.calculate(854, 480).compact);
    }

    @Test
    public void switchesToCompactModeForShortWindows() {
        AdaptiveLayout.Result layout = AdaptiveLayout.calculate(640, 360);

        assertTrue(layout.compact);
        assertTrue(layout.scale == 1f);
        assertLayoutFits(640, 360);
    }

    @Test
    public void keepsNativePixelTextForMinecraftMinimumAndPortraitWindows() {
        assertLayoutFits(320, 240);
        assertLayoutFits(480, 854);
        assertLayoutFits(1024, 300);

        assertTrue(AdaptiveLayout.calculate(320, 240).compact);
        assertTrue(AdaptiveLayout.calculate(320, 240).scale == 1f);
        assertTrue(AdaptiveLayout.calculate(480, 854).scale == 1f);
        assertTrue(AdaptiveLayout.calculate(1024, 300).scale == 1f);
    }

    @Test
    public void onlyScalesBelowMinecraftMinimumGuiSize() {
        AdaptiveLayout.Result layout = AdaptiveLayout.calculate(240, 160);

        assertTrue(layout.compact);
        assertTrue(layout.scale < 1f);
        assertLayoutFits(240, 160);
    }

    @Test
    public void compactPanelUsesSmoothSlideInsteadOfJumping() {
        float opening = AdaptiveLayout.nextPanelSlide(1f, false);
        float closing = AdaptiveLayout.nextPanelSlide(0f, true);

        assertTrue(opening > 0f && opening < 1f);
        assertTrue(closing > 0f && closing < 1f);
        assertTrue(AdaptiveLayout.visiblePanelWidth(200, opening) > 0);
        assertTrue(AdaptiveLayout.visiblePanelWidth(200, opening) < 200);
    }

    @Test
    public void wideAndCompactPanelsShareTheSameAnimationProgress() {
        float slide = AdaptiveLayout.nextPanelSlide(1f, false);
        int wideVisible = AdaptiveLayout.visiblePanelWidth(200, slide);
        int compactVisible = AdaptiveLayout.visiblePanelWidth(140, slide);

        assertTrue(wideVisible > 0 && wideVisible < 200);
        assertTrue(compactVisible > 0 && compactVisible < 140);
        assertTrue(Math.abs(wideVisible / 200f - compactVisible / 140f) < 0.02f);
    }

    @Test
    public void compactPanelAnimationConvergesToItsTarget() {
        float slide = 1f;
        for (int i = 0; i < 60; i++) {
            slide = AdaptiveLayout.nextPanelSlide(slide, false);
        }
        assertTrue(slide == 0f);
        assertTrue(AdaptiveLayout.visiblePanelWidth(200, slide) == 200);

        for (int i = 0; i < 60; i++) {
            slide = AdaptiveLayout.nextPanelSlide(slide, true);
        }
        assertTrue(slide == 1f);
        assertTrue(AdaptiveLayout.visiblePanelWidth(200, slide) == 0);
    }

    private void assertLayoutFits(int screenWidth, int screenHeight) {
        AdaptiveLayout.Result layout = AdaptiveLayout.calculate(screenWidth, screenHeight);
        assertTrue(layout.width >= 300);
        assertTrue(layout.height >= 220);
        assertTrue(layout.width * layout.scale <= screenWidth - 3);
        assertTrue(layout.height * layout.scale <= screenHeight - 3);
    }
}
