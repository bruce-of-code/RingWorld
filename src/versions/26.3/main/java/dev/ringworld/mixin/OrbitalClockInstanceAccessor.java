package dev.ringworld.mixin;

import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerClockManager.ServerClockInstance.class)
public interface OrbitalClockInstanceAccessor {
    @Accessor("timeMarkers")
    Map<ResourceKey<ClockTimeMarker>, ClockTimeMarker> ringworld$timeMarkers();
}
