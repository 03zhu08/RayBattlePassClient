package cn.earthsky.raybattlepass.util;

import cn.earthsky.raybattlepass.RayBattlePass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayDeque;
import java.util.Deque;

@SideOnly(Side.CLIENT)
public class ToastManager {

    private static final Deque<ToastEntry> queue = new ArrayDeque<>();
    private static ToastEntry currentToast;
    private static long toastStartTime;
    private static boolean registered;

    public static void show(String title, String body) {
        show(title, body, 3000);
    }

    public static void show(String title, String body, int durationMs) {
        ToastEntry entry = new ToastEntry(title, body, durationMs);
        if (currentToast == null && queue.isEmpty()) {
            currentToast = entry;
            toastStartTime = System.currentTimeMillis();
        } else {
            queue.add(entry);
        }
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(new ToastTickHandler());
            registered = true;
        }
    }

    public static void render(ScaledResolution sr, float partialTicks) {
        if (currentToast == null) return;

        long elapsed = System.currentTimeMillis() - toastStartTime;
        float alpha = 1.0F;
        int fadeMs = 400;
        if (elapsed < fadeMs) {
            alpha = (float) elapsed / fadeMs;
        } else if (elapsed > currentToast.durationMs - fadeMs) {
            alpha = 1.0F - (float) (elapsed - (currentToast.durationMs - fadeMs)) / fadeMs;
        }
        if (alpha <= 0) {
            advanceQueue();
            return;
        }

        int x = sr.getScaledWidth() / 2 - 120;
        int y = sr.getScaledHeight() - 60;
        int color = (Math.min(255, (int) (alpha * 200)) << 24) | 0x111111;
        GuiHelper.drawRect(x, y, x + 240, y + 32, color);

        String text = currentToast.title;
        if (currentToast.body != null) text += " - " + currentToast.body;
        int textColor = (Math.min(255, (int) (alpha * 255)) << 24) | 0xFFFFFF;
        PassFontUtil.drawCenteredString(
            PassFontUtil.truncateString(text, 230), x + 120, y + 12, textColor);
    }

    private static void advanceQueue() {
        if (queue.isEmpty()) {
            currentToast = null;
            return;
        }
        currentToast = queue.poll();
        toastStartTime = System.currentTimeMillis();
    }

    public static class ToastTickHandler {
        @SubscribeEvent
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (currentToast == null) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen != null) {
                render(new ScaledResolution(mc), 0);
            }
        }
    }

    private static class ToastEntry {
        final String title;
        final String body;
        final int durationMs;

        ToastEntry(String title, String body, int durationMs) {
            this.title = title;
            this.body = body;
            this.durationMs = durationMs;
        }
    }
}
