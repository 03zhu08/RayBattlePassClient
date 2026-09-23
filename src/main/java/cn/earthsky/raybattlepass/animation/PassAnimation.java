package cn.earthsky.raybattlepass.animation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PassAnimation {

    private final List<AnimatedValue> values = new ArrayList<>();
    private final List<TimedEffect> effects = new ArrayList<>();
    private long startTime;

    public PassAnimation() {
        this.startTime = System.currentTimeMillis();
    }

    public void reset() {
        startTime = System.currentTimeMillis();
        for (AnimatedValue v : values) v.reset();
        for (TimedEffect e : effects) e.triggered = false;
    }

    public AnimatedValue addValue(float from, float to, int durationMs, String easing, int delayMs) {
        AnimatedValue v = new AnimatedValue(from, to, durationMs, easing, delayMs, startTime);
        values.add(v);
        return v;
    }

    public AnimatedValue addValue(float from, float to, int durationMs) {
        return addValue(from, to, durationMs, "easeOutQuad", 0);
    }

    public TimedEffect addEffect(int triggerAtMs, Runnable action) {
        TimedEffect e = new TimedEffect(triggerAtMs, action, startTime);
        effects.add(e);
        return e;
    }

    public void update() {
        long now = System.currentTimeMillis();
        long elapsed = now - startTime;
        for (AnimatedValue v : values) v.update(elapsed);
        for (TimedEffect e : effects) {
            if (!e.triggered && elapsed >= e.triggerAtMs) {
                e.triggered = true;
                e.action.run();
            }
        }
    }

    public boolean isComplete() {
        long elapsed = System.currentTimeMillis() - startTime;
        for (AnimatedValue v : values) {
            if (elapsed < v.delayMs + v.durationMs) return false;
        }
        for (TimedEffect e : effects) {
            if (!e.triggered) return false;
        }
        return true;
    }

    public float get(String name) {
        for (AnimatedValue v : values) {
            if (v.name != null && v.name.equals(name)) return v.current;
        }
        return 0;
    }

    public static class AnimatedValue {
        public String name;
        public float from, to, current;
        public int durationMs, delayMs;
        public String easing;
        public long baseTime;

        AnimatedValue(float from, float to, int durationMs, String easing, int delayMs, long baseTime) {
            this.from = from;
            this.to = to;
            this.current = from;
            this.durationMs = durationMs;
            this.easing = easing;
            this.delayMs = delayMs;
            this.baseTime = baseTime;
        }

        void reset() { this.current = from; this.baseTime = System.currentTimeMillis(); }

        void update(long elapsedMs) {
            if (elapsedMs < delayMs) { current = from; return; }
            float t = Math.min(1f, (float) (elapsedMs - delayMs) / durationMs);
            current = from + (to - from) * applyEasing(t);
        }

        private float applyEasing(float t) {
            switch (easing) {
                case "linear": return Easing.linear(t);
                case "easeInQuad": return Easing.easeInQuad(t);
                case "easeOutQuad": return Easing.easeOutQuad(t);
                case "easeInOutQuad": return Easing.easeInOutQuad(t);
                case "easeOutCubic": return Easing.easeOutCubic(t);
                case "easeOutElastic": return Easing.easeOutElastic(t);
                case "easeOutBounce": return Easing.easeOutBounce(t);
                default: return Easing.easeOutQuad(t);
            }
        }
    }

    public static class TimedEffect {
        int triggerAtMs;
        Runnable action;
        boolean triggered;
        long baseTime;

        TimedEffect(int triggerAtMs, Runnable action, long baseTime) {
            this.triggerAtMs = triggerAtMs;
            this.action = action;
            this.baseTime = baseTime;
            this.triggered = false;
        }
    }

    // ─── Presets ───────────────────────────────────────────────

    public static PassAnimation openSequence() {
        PassAnimation a = new PassAnimation();
        a.addValue(0, 1, 400, "easeOutCubic", 0).name = "fadeIn";
        a.addValue(-10, 0, 350, "easeOutQuad", 50).name = "navSlide";
        a.addValue(0, 1, 500, "easeOutElastic", 150).name = "radarScale";
        a.addValue(0, 1, 300, "easeOutQuad", 200).name = "contentFade";
        return a;
    }

    public static PassAnimation levelUpEffect() {
        PassAnimation a = new PassAnimation();
        a.addValue(1, 1.3f, 200, "easeOutQuad", 0).name = "scaleUp";
        a.addValue(1.3f, 1, 300, "easeOutBounce", 200).name = "scaleDown";
        a.addValue(0, 1, 150, "linear", 0).name = "flashAlpha";
        a.addValue(1, 0, 500, "easeOutQuad", 150).name = "flashFade";
        return a;
    }

    public static PassAnimation claimRewardEffect() {
        PassAnimation a = new PassAnimation();
        a.addValue(1, 1.15f, 150, "easeOutQuad", 0).name = "cardScaleUp";
        a.addValue(1.15f, 0.95f, 100, "easeOutQuad", 150).name = "cardShrink";
        a.addValue(0.95f, 1, 200, "easeOutBounce", 250).name = "cardBounce";
        a.addValue(0, 1, 200, "easeOutQuad", 0).name = "sparkleAlpha";
        a.addValue(1, 0, 400, "easeOutCubic", 200).name = "sparkleFade";
        return a;
    }

    public static PassAnimation purchaseEffect() {
        PassAnimation a = new PassAnimation();
        a.addValue(0, 1, 300, "easeOutCubic", 0).name = "dimOverlay";
        a.addValue(0.8f, 1, 500, "easeOutElastic", 100).name = "emblemScale";
        a.addValue(0, 1, 200, "easeOutQuad", 400).name = "trackLightUp";
        return a;
    }

    public static PassAnimation tabSwitchEffect() {
        PassAnimation a = new PassAnimation();
        a.addValue(0, 1, 260, "easeInOutQuad", 0).name = "transition";
        return a;
    }
}
