package dev.ringworld.world;

import com.mojang.serialization.Codec;

/**
 * How many random-spread structures a new RingWorld gets, relative to vanilla
 * (experiment branch, 2026-11-22). Replaces the on/off "more structures" switch, which added
 * a second candidate grid at 0.7x vanilla spacing with no thinning: roughly 2.5x vanilla on
 * the default band, and a different amount on every band width because rim-adjacent extras
 * are discarded. The levels here are targets for the TOTAL count; see
 * {@link RingStructureDensity#isAdditionalCandidate}.
 *
 * <p>Saved worlds and config files from before this change only have the boolean; "on" reads
 * as {@link #DOUBLE}. DOUBLE keeps the old fingerprint salt so those worlds keep their layout
 * fingerprint (and therefore their cached Atlas).
 */
public enum RingStructureAbundance {
    VANILLA(0, "Vanilla", 0.0, 0L),
    ONE_AND_HALF(1, "1.5x", 0.5, 0x2F8A_51C7L),
    DOUBLE(2, "2x", 1.0, 0x6D31_904BL);

    public static final Codec<RingStructureAbundance> CODEC = Codec.INT.xmap(
            RingStructureAbundance::fromId, RingStructureAbundance::id);

    private final int id;
    private final String label;
    private final double extraFraction;
    private final long fingerprintSalt;

    RingStructureAbundance(int id, String label, double extraFraction, long fingerprintSalt) {
        this.id = id;
        this.label = label;
        this.extraFraction = extraFraction;
        this.fingerprintSalt = fingerprintSalt;
    }

    public int id() { return id; }
    public String label() { return label; }
    /** Extra structures as a fraction of vanilla's count: 0.5 for 1.5x, 1.0 for 2x. */
    public double extraFraction() { return extraFraction; }
    long fingerprintSalt() { return fingerprintSalt; }

    public RingStructureAbundance next() {
        RingStructureAbundance[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static RingStructureAbundance fromId(int id) {
        for (RingStructureAbundance value : values()) if (value.id == id) return value;
        throw new IllegalArgumentException("unknown RingWorld structure abundance " + id);
    }

    /** Config-file value: a level name (VANILLA, ONE_AND_HALF, DOUBLE) or a legacy boolean. */
    public static RingStructureAbundance parse(String value) {
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("true")) return DOUBLE;
        if (trimmed.equalsIgnoreCase("false")) return VANILLA;
        return valueOf(trimmed.toUpperCase(java.util.Locale.ROOT));
    }
}
