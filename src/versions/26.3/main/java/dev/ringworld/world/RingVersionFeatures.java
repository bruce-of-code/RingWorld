package dev.ringworld.world;

/** Version-owned feature defaults. */
public final class RingVersionFeatures {
    private RingVersionFeatures() { }

    /** Day cycle given to newly created ring worlds; the orbital day is implemented here (RingServerOrbital, RingLocalClock). */
    public static final RingSkyProfile.DayCycle NEW_WORLD_DAY_CYCLE = RingSkyProfile.DayCycle.ORBITAL;
}
