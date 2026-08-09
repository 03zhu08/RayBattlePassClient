package cn.earthsky.raybattlepass.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.lwjgl.opengl.GL11;

public class GuiHelper {

    public static void drawRect(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left, top, right, bottom, color);
    }

    public static void drawGradientRect(int left, int top, int right, int bottom, int startColor, int endColor) {
        Gui.drawRect(left, top, right, bottom, startColor);
    }

    public static void drawHorizontalLine(int startX, int endX, int y, int color) {
        Gui.drawRect(startX, y, endX, y + 1, color);
    }

    public static void drawVerticalLine(int x, int startY, int endY, int color) {
        Gui.drawRect(x, startY, x + 1, endY, color);
    }

    public static void drawBeveledRect(int left, int top, int right, int bottom, int bgColor, int bevelLight, int bevelDark) {
        drawRect(left, top, right, bottom, bgColor);
        drawRect(left, top, right, top + 1, bevelLight);
        drawRect(left, top, left + 1, bottom, bevelLight);
        drawRect(left, bottom - 1, right, bottom, bevelDark);
        drawRect(right - 1, top, right, bottom, bevelDark);
    }

    public static void enableScissor(int x, int y, int width, int height, float scaleFactor) {
        Minecraft mc = Minecraft.getMinecraft();
        int sf = (int) scaleFactor;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * sf, mc.displayHeight - (y + height) * sf, width * sf, height * sf);
    }

    public static void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static void drawProgressBar(int x, int y, int width, int height, float progress, int bgColor, int fillColor) {
        drawRect(x, y, x + width, y + height, bgColor);
        int fillWidth = (int) (width * Math.min(progress, 1.0F));
        if (fillWidth > 0) {
            drawRect(x, y, x + fillWidth, y + height, fillColor);
        }
    }

    public static boolean isMouseInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
