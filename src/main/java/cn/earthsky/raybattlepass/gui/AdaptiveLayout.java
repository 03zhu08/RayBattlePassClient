package cn.earthsky.raybattlepass.gui;

public final class AdaptiveLayout {

    private AdaptiveLayout() {}

    public static Result calculate(int screenWidth, int screenHeight) {
        /*
         * Minecraft's bitmap font becomes thin and blurry when the entire UI is
         * rendered at a fractional scale. Keep native 1:1 GUI pixels for every
         * normal ScaledResolution (Minecraft guarantees roughly 320x240), and
         * let the compact layout reflow the content instead.
         */
        final int minWidth = 300;
        final int minHeight = 220;
        final int maxWidth = 800;
        final int maxHeight = 520;
        final int margin = screenWidth >= 340 && screenHeight >= 260 ? 10 : 4;

        int usableWidth = Math.max(1, screenWidth - margin);
        int usableHeight = Math.max(1, screenHeight - margin);
        float widthScale = Math.max(0.1f, usableWidth / (float) minWidth);
        float heightScale = Math.max(0.1f, usableHeight / (float) minHeight);
        float scale = Math.min(1f, Math.min(widthScale, heightScale));

        int virtualWidth = Math.max(minWidth, (int) (usableWidth / scale));
        int virtualHeight = Math.max(minHeight, (int) (usableHeight / scale));
        int width = Math.min(maxWidth, virtualWidth);
        int height = Math.min(maxHeight, virtualHeight);
        boolean compact = width < 720 || height < 410;

        return new Result(scale, width, height, compact);
    }

    static float nextPanelSlide(float current, boolean collapsed) {
        float target = collapsed ? 1f : 0f;
        float difference = target - current;
        if (Math.abs(difference) <= 0.01f) return target;
        return Math.max(0f, Math.min(1f, current + difference * 0.18f));
    }

    static int visiblePanelWidth(int maximumWidth, float slide) {
        float visibleRatio = 1f - Math.max(0f, Math.min(1f, slide));
        return Math.max(0, Math.round(maximumWidth * visibleRatio));
    }

    public static final class Result {
        public final float scale;
        public final int width;
        public final int height;
        public final boolean compact;

        private Result(float scale, int width, int height, boolean compact) {
            this.scale = scale;
            this.width = width;
            this.height = height;
            this.compact = compact;
        }
    }
}
