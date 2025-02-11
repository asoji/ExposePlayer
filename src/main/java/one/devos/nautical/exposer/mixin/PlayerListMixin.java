package one.devos.nautical.exposer.mixin;

import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(PlayerList.class)
public interface PlayerListMixin {

    @Accessor("stats")
    Map<UUID, ServerStatsCounter> getStats();

}
