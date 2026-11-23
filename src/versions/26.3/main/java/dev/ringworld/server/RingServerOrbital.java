package dev.ringworld.server;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingOrbitalDay;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingSkySettings;
import java.util.Arrays;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server half of the orbital day cycle (experiment branch, 2026-11-22; 26.3).
 *
 * <p>Phase 1 made every client visual follow local time. This makes gameplay follow it too:
 * <ul>
 *   <li><b>Timeline attributes queried at a position</b> (undead burning, bees, creaking,
 *       eyeblossoms, villager schedules, the daylight detector's sun angle, sky light level)
 *       are evaluated at the local time of that position. The attribute system's per-tick
 *       cache is bypassed for those queries; the server clock reports local time only for the
 *       duration of that one evaluation, on that thread ({@link #clockOffset}).</li>
 *   <li><b>Sky darkness</b> is per longitude: spawn-light checks take it from the spawn
 *       position, and {@code getSkyDarken}/{@code isBrightOutside} use the position of
 *       whatever is being ticked ({@link #enter}: entities, players, bed use). Values are
 *       cached per tick in 16-block longitude buckets.</li>
 *   <li><b>Sleep</b> counts only players in local night (plus anyone already asleep), and the
 *       skip advances the world clock until every sleeper reaches local dawn, so every other
 *       longitude moves forward by the same amount.</li>
 * </ul>
 * Not covered (still global): moon phase and anything keyed to the day count.
 */
public final class RingServerOrbital {
    /** Implemented by the attribute value sampler mixin. */
    public interface LocalSampler {
        boolean ringworld$hasTimeLayer();
        Object ringworld$computeLocal(Vec3 position, Object interpolator, long offsetTicks);
    }

    private static volatile ServerLevel active;
    private static volatile RingGeometry geometry;
    private static final ThreadLocal<Vec3[]> CONTEXT = ThreadLocal.withInitial(() -> new Vec3[1]);
    private static final ThreadLocal<long[]> CLOCK_OFFSET = ThreadLocal.withInitial(() -> new long[]{-1L});
    private static final int BUCKET_BLOCKS = 16;
    private static int[] darkenCache = new int[0];
    private static long darkenCacheTick = Long.MIN_VALUE;

    private RingServerOrbital() { }

    /** Called every Overworld tick; picks up /ringworld daycycle changes within a tick. */
    public static void refresh(ServerLevel overworld) {
        RingGeometry ring = RingWorldServer.geometryFor(overworld);
        boolean on = ring != null
                && RingSkySettings.get(overworld).profile().dayCycle() == RingSkyProfile.DayCycle.ORBITAL;
        if (!on) {
            if (active == overworld) { active = null; geometry = null; }
            return;
        }
        if (active != overworld || !ring.equals(geometry)) {
            geometry = ring;
            active = overworld;
            darkenCache = new int[Math.max(1, ring.circumferenceBlocks() / BUCKET_BLOCKS + 1)];
            darkenCacheTick = Long.MIN_VALUE;
        }
    }

    public static void forget(ServerLevel world) {
        if (active == world) { active = null; geometry = null; }
    }

    public static boolean activeFor(Level level) {
        return level != null && level == active;
    }

    public static boolean ownsSystem(Object system) {
        ServerLevel level = active;
        return level != null && level.environmentAttributes() == system;
    }

    // ---- position context (what is being ticked) ----

    public static Vec3 enter(Vec3 position) {
        Vec3[] slot = CONTEXT.get();
        Vec3 previous = slot[0];
        slot[0] = position;
        return previous;
    }

    public static void exit(Vec3 previous) {
        CONTEXT.get()[0] = previous;
    }

    public static Vec3 context() {
        return CONTEXT.get()[0];
    }

    // ---- clock offset for one local evaluation ----

    public static long pushClockOffset(long offset) {
        long[] slot = CLOCK_OFFSET.get();
        long previous = slot[0];
        slot[0] = offset;
        return previous;
    }

    public static void popClockOffset(long previous) {
        CLOCK_OFFSET.get()[0] = previous;
    }

    /** Ticks the server clock should subtract right now on this thread, or -1. */
    public static long clockOffset() {
        return CLOCK_OFFSET.get()[0];
    }

    public static long offsetTicks(double blockX) {
        RingGeometry ring = geometry;
        return ring == null ? 0L : RingOrbitalDay.longitudeOffsetTicks(ring, blockX);
    }

    public static long localTicks(long worldTicks, double blockX) {
        RingGeometry ring = geometry;
        return ring == null ? worldTicks : RingOrbitalDay.localTicks(ring, worldTicks, blockX);
    }

    // ---- local sky darkness ----

    /** Vanilla's {@code 15 - sky_light_level}, at the local time of this longitude. */
    public static int skyDarkenAt(Level level, double blockX) {
        RingGeometry ring = geometry;
        if (ring == null || level != active || !level.getServer().isSameThread()) return level.getSkyDarken();
        long tick = level.getGameTime();
        int[] cache = darkenCache;
        if (tick != darkenCacheTick) {
            Arrays.fill(cache, -1);
            darkenCacheTick = tick;
        }
        int bucket = Math.min(cache.length - 1, (int)(ring.wrapX(blockX) / BUCKET_BLOCKS));
        int value = cache[bucket];
        if (value < 0) {
            Vec3 sample = new Vec3(bucket * BUCKET_BLOCKS + BUCKET_BLOCKS / 2.0, level.getSeaLevel(), 0.0);
            float skyLight = level.environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_LEVEL, sample);
            value = Math.max(0, (int)(15.0F - skyLight));
            cache[bucket] = value;
        }
        return value;
    }

    /** Vanilla's {@code isBrightOutside} rule (darkness below 4) at this longitude. */
    public static boolean brightAt(Level level, double blockX) {
        return !level.dimensionType().hasFixedTime() && skyDarkenAt(level, blockX) < 4;
    }

    /** True only if no (non-spectator) player is in local night: the orbital "sky is bright". */
    public static boolean brightForAllPlayers(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && !brightAt(level, player.getX())) return false;
        }
        return true;
    }

    /** Players who count for sleep: asleep already, or in local night where they stand. */
    public static List<ServerPlayer> sleepCandidates(ServerLevel level, List<ServerPlayer> players) {
        if (!activeFor(level)) return players;
        return players.stream().filter(player -> player.isSleeping() || !brightAt(level, player.getX())).toList();
    }

    /** Ticks to add to the world clock so every sleeper reaches local dawn (local time 0). */
    public static int ticksUntilSleepersWake(ServerLevel level, long worldTicks) {
        long longest = 0L;
        for (ServerPlayer player : level.players()) {
            if (!player.isSleeping()) continue;
            long local = localTicks(worldTicks, player.getX());
            longest = Math.max(longest, Math.floorMod(-local, RingOrbitalDay.DAY_TICKS));
        }
        return (int)longest;
    }
}
