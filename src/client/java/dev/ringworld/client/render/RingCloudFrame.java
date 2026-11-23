package dev.ringworld.client.render;

/**
 * The arguments vanilla passed to its cloud renderer this frame (experiment, 2026-11-22),
 * captured by {@code CloudRendererMixin} so the distant cloud ring uses exactly the same
 * colour, range and time as the live clouds it continues. Render thread only.
 */
public final class RingCloudFrame {
    private static int color;
    private static int rangeChunks;
    private static long gameTime;
    private static float partialTick;
    private static boolean visible;
    private static long capturedNanos;

    private RingCloudFrame() { }

    public static void capture(int argb, boolean cloudsOn, int range, long time, float partial) {
        color = argb;
        visible = cloudsOn;
        rangeChunks = range;
        gameTime = time;
        partialTick = partial;
        capturedNanos = System.nanoTime();
    }

    /** False when clouds are off, or when vanilla stopped calling its cloud renderer
     *  (it may skip the call entirely with clouds off), so a stale capture never shows. */
    public static boolean visible() {
        return visible && System.nanoTime() - capturedNanos < 500_000_000L;
    }
    public static int color() { return color; }
    public static int rangeChunks() { return rangeChunks; }
    public static long gameTime() { return gameTime; }
    public static float partialTick() { return partialTick; }
}
