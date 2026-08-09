package cn.earthsky.raybattlepass.animation;

public class Easing {

    public static float linear(float t) { return t; }

    public static float easeInQuad(float t) { return t * t; }
    public static float easeOutQuad(float t) { return t * (2 - t); }
    public static float easeInOutQuad(float t) {
        return t < 0.5f ? 2 * t * t : -1 + (4 - 2 * t) * t;
    }

    public static float easeOutCubic(float t) {
        return (--t) * t * t + 1;
    }

    public static float easeOutElastic(float t) {
        if (t == 0 || t == 1) return t;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t - 0.075) * (2 * Math.PI) / 0.3) + 1);
    }

    public static float easeOutBounce(float t) {
        if (t < 1 / 2.75f) {
            return 7.5625f * t * t;
        } else if (t < 2 / 2.75f) {
            t -= 1.5f / 2.75f;
            return 7.5625f * t * t + 0.75f;
        } else if (t < 2.5f / 2.75f) {
            t -= 2.25f / 2.75f;
            return 7.5625f * t * t + 0.9375f;
        } else {
            t -= 2.625f / 2.75f;
            return 7.5625f * t * t + 0.984375f;
        }
    }
}
