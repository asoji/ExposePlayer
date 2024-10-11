package one.devos.nautical.exposeplayers.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.stats.StatType
import net.minecraft.stats.Stats
import one.devos.nautical.exposeplayers.utils.UUIDSerializer
import one.devos.nautical.exposeplayers.utils.getPlayerStatsByUuid
import java.util.UUID

private val ITEM_STATS = listOf(
    Stats.ITEM_USED,
    Stats.ITEM_BROKEN,
    Stats.ITEM_CRAFTED,
    Stats.ITEM_PICKED_UP,
    Stats.ITEM_DROPPED
)

private val BLOCK_STATS = listOf(
    Stats.BLOCK_MINED,
)

fun Application.configureRouting(server: MinecraftServer) {
    routing {
        get {
            call.respondText("Hello world!")
        }

        get("/players") {
            call.respond(PlayersEndpointResponse(
                server.playerCount,
                server.playerList.players.map { player ->
                    PlayerInfo(player.uuid, player.name.string)
                }
            ))
        }

        get("/players/stats/{player_name}") {
            val playerName = call.parameters["player_name"]
            if (playerName == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val playerUuid = server.profileCache?.get(playerName)?.get()?.id
            if (playerUuid == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val statList = mutableListOf<PlayerStatistic<*>>()

            val statsCounter = server.playerList.getPlayerStatsByUuid(playerUuid, playerName)

            for (resourceLocation in Stats.CUSTOM) {
                statList.add(PlayerCustomStatistic(
                    Component.translatable(resourceLocation.value.toLanguageKey("stat")).string,
                    statsCounter.getValue(resourceLocation)
                ))
            }

            for (item in BuiltInRegistries.ITEM) {
                val types = mutableMapOf<ItemStatisticType, Int>()

                for (statisticType in ITEM_STATS) {
                    val value = statsCounter.getValue(statisticType.get(item))
                    if (value != 0) {
                        types[ItemStatisticType.from(statisticType)] = value
                    }
                }

                statList.add(PlayerItemsStatistic(Component.translatable(item.descriptionId).string, types))
            }

            for (block in BuiltInRegistries.BLOCK) {
                val types = mutableMapOf<BlockStatisticType, Int>()

                for (statisticType in BLOCK_STATS) {
                    val value = statsCounter.getValue(statisticType.get(block))
                    if (value != 0) {
                        types[BlockStatisticType.from(statisticType)] = value
                    }
                }

                statList.add(PlayerBlocksStatistic(Component.translatable(block.descriptionId).string, types))
            }

            call.respond(statList)
        }
    }
}

@Serializable
private data class PlayerInfo(
    @Serializable(with = UUIDSerializer::class) val uuid: UUID,
    val name: String
)

@Serializable
private data class PlayersEndpointResponse(
    val count: Int,
    val players: List<@Contextual PlayerInfo>
)

@Serializable
private sealed interface PlayerStatistic<T> {
    val displayName: String
    @Contextual val value: T
}

@Serializable
private class PlayerCustomStatistic(
    override val displayName: String,
    override val value: Int
) : PlayerStatistic<Int>

@Serializable
private class PlayerItemsStatistic(
    override val displayName: String,
    override val value: Map<ItemStatisticType, Int>,
) : PlayerStatistic<Map<ItemStatisticType, Int>>

@Serializable
private enum class ItemStatisticType(private val value: StatType<*>) {
    USED(Stats.ITEM_USED),
    BROKEN(Stats.ITEM_BROKEN),
    CRAFTED(Stats.ITEM_CRAFTED),
    PICKED_UP(Stats.ITEM_PICKED_UP),
    DROPPED(Stats.ITEM_DROPPED);

    companion object {
        fun from(statisticType: StatType<*>): ItemStatisticType {
            return entries.first { it.value == statisticType }
        }
    }
}

@Serializable
private class PlayerBlocksStatistic(
    override val displayName: String,
    @Contextual override val value: Map<BlockStatisticType, Int>,
) : PlayerStatistic<Map<BlockStatisticType, Int>>

@Serializable
private enum class BlockStatisticType(private val value: StatType<*>) {
    MINED(Stats.BLOCK_MINED);

    companion object {
        fun from(statisticType: StatType<*>): BlockStatisticType {
            return entries.first { it.value == statisticType }
        }
    }
}