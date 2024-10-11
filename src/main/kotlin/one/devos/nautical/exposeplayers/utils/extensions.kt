package one.devos.nautical.exposeplayers.utils

import net.minecraft.FileUtil
import net.minecraft.server.players.PlayerList
import net.minecraft.stats.ServerStatsCounter
import net.minecraft.world.level.storage.LevelResource
import one.devos.nautical.exposeplayers.mixin.PlayerListMixin
import java.io.File
import java.util.UUID

val PlayerList.stats: MutableMap<UUID, ServerStatsCounter>
    get() = (this as PlayerListMixin).stats

fun PlayerList.getPlayerStatsByUuid(uuid: UUID, name: String): ServerStatsCounter {
    var statsCounter = stats[uuid]
    if (statsCounter == null) {
        val directory = this.server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile()
        val uuidFile = File(directory, "$uuid.json")
        if (!uuidFile.exists()) {
            val playerNameFile = File(directory, "$name.json")
            val path = playerNameFile.toPath()
            if (FileUtil.isPathNormalized(path) && FileUtil.isPathPortable(path) && path.startsWith(directory.path) && playerNameFile.isFile) {
                playerNameFile.renameTo(uuidFile);
            }
        }

        statsCounter = ServerStatsCounter(this.server, uuidFile)
        stats[uuid] = statsCounter
    }

    return statsCounter!!
}
