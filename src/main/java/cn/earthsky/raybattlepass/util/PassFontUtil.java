package cn.earthsky.raybattlepass.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

public class PassFontUtil {

    public static FontRenderer getFont() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    public static int getStringWidth(String text) {
        return getFont().getStringWidth(text);
    }

    public static void drawString(String text, int x, int y, int color) {
        getFont().drawString(text, x, y, color);
    }

    public static void drawStringWithShadow(String text, int x, int y, int color) {
        getFont().drawStringWithShadow(text, x, y, color);
    }

    public static void drawCenteredString(String text, int centerX, int y, int color) {
        getFont().drawString(text, centerX - getStringWidth(text) / 2, y, color);
    }

    public static String truncateString(String text, int maxWidth) {
        return getFont().trimStringToWidth(text, maxWidth);
    }

    public static void drawScaledString(String text, float x, float y, int color, float scale) {
        FontRenderer fr = getFont();
        fr.drawString(text, (int) (x / scale), (int) (y / scale), color);
    }
}
