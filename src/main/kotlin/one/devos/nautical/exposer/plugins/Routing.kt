package one.devos.nautical.exposer.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.stats.StatType
import net.minecraft.stats.Stats
import net.minecraft.world.item.Item
import one.devos.nautical.exposer.mixin.PlayerAdvancementsMixin
import one.devos.nautical.exposer.utils.UUIDSerializer
import one.devos.nautical.exposer.utils.getEnchantmentsWithLevels
import one.devos.nautical.exposer.utils.getPlayerStatsByUuid
import java.util.*

private val ITEM_STATS = listOf(
    Stats.ITEM_USED,
    Stats.ITEM_BROKEN,
    Stats.ITEM_CRAFTED,
    Stats.ITEM_PICKED_UP,
    Stats.ITEM_DROPPED
)

private val BLOCK_STATS = listOf(
    Stats.BLOCK_MINED
)

fun Application.configureRouting(server: MinecraftServer) {
    routing {
        get {
            call.respondText("Howdy! If you somehow got here, that's because Exposer is installed and is getting stats of every player on the server to be used. Nothing malicious, just letting you know! c:")
        }

        get("/server") {
            val dedicatedServer = server as? DedicatedServer

            call.respond(
                ServerStats(
                    server.isDedicatedServer,
                    server.acceptsTransfers(),
                    server.isFlightAllowed,
                    dedicatedServer?.properties?.allowNether ?: true,
                    dedicatedServer?.properties?.bugReportLink ?: "",
                    server.isCommandBlockEnabled,
                    server.worldData.difficulty.name,
                    server.playerList.players.map { player ->
                        PlayerInfoWithPing(player.uuid, player.name.string, player.connection.latency())
                    },
                    server.playerCount,
                    server.maxPlayers,
                    server.motd,
                    server.isPvpAllowed,
                    (server.overworld().dayTime() % 24000).toInt()
                )
            )
        }

        get("/players") {
            call.respond(Players(
                server.playerCount,
                server.playerList.players.map { player ->
                    PlayerInfoWithPing(player.uuid, player.name.string, player.connection.latency())
                }
            ))
        }

        get("/player/{player_name}/status") {
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

            val player = server.playerList.getPlayer(playerUuid)
            if (player == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            call.respond(
                PlayerStatus(
                    PlayerHealth(
                        player.health,
                        player.maxHealth
                    ),
                    PlayerAbsorption(
                        player.absorptionAmount,
                        player.maxAbsorption
                    ),
                    PlayerArmor(
                        player.armorValue,
                        player.armorCoverPercentage,
                        20f, // assuming max armor value is 20 temporarily - Other mods may modify the max value, thus meaning that we need to find a way to obtain their new max value.
                        player.absorptionAmount
                    ),
                    PlayerFood(
                        player.foodData.exhaustionLevel,
                        player.foodData.saturationLevel,
                        player.foodData.foodLevel
                    ),
                    PlayerAir(
                        player.airSupply,
                        player.maxAirSupply
                    )
                )
            )
        }

        get("/player/{player_name}/stats") {
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
            val itemStats = mutableMapOf<Item, PlayerItemsStatistic>()

            val statsCounter = server.playerList.getPlayerStatsByUuid(playerUuid, playerName)

            for (resourceLocation in Stats.CUSTOM) {
                statList.add(
                    PlayerCustomStatistic(
                        Component.translatable(resourceLocation.value.toLanguageKey("stat")).string,
                        statsCounter.getValue(resourceLocation)
                    )
                )
            }

            for (item in BuiltInRegistries.ITEM) {
                val types = mutableMapOf<ItemStatisticType, Int>()

                for (statisticType in ITEM_STATS) {
                    val value = statsCounter.getValue(statisticType.get(item))
                    if (value != 0) {
                        types[ItemStatisticType.from(statisticType)] = value
                    }
                }

                val new = PlayerItemsStatistic(Component.translatable(item.descriptionId).string, types)
                itemStats.compute(item) { _, existing ->
                    if (existing == null) {
                        new
                    } else {
                        PlayerItemsStatistic(
                            new.displayName,
                            existing.value.apply { putAll(new.value) }
                        )
                    }
                }
            }

            for (block in BuiltInRegistries.BLOCK) {
                val types = mutableMapOf<ItemStatisticType, Int>()

                for (statisticType in BLOCK_STATS) {
                    val value = statsCounter.getValue(statisticType.get(block))
                    if (value != 0) {
                        types[ItemStatisticType.from(statisticType)] = value
                    }
                }

                val new = PlayerItemsStatistic(Component.translatable(block.descriptionId).string, types)
                itemStats.compute(block.asItem()) { _, existing ->
                    if (existing == null) {
                        new
                    } else {
                        PlayerItemsStatistic(
                            new.displayName,
                            existing.value.apply { putAll(new.value) }
                        )
                    }
                }
            }

            statList.addAll(itemStats.values)
            call.respond(statList)
        }

        get("/player/{player_name}/advancements") {
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

            val player = server.playerList.getPlayer(playerUuid)
            if (player == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val completedAdvancements = server.advancements.allAdvancements.mapNotNull { advancement ->
                if (advancement.id.path.startsWith("recipes/")) return@mapNotNull null
                val progress = (player.advancements as PlayerAdvancementsMixin).progress[advancement] ?: return@mapNotNull null
                Component.translatable(advancement.id.toLanguageKey()).string to progress.isDone
            }

            call.respond(completedAdvancements.toMap())
        }

        get("/player/{player_name}/inventory") {
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

            val player = server.playerList.getPlayer(playerUuid)
            if (player == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            call.respond(PlayerInventory(
                player.inventory.items.map {
                    PlayerItem(it.displayName.string, it.item.toString(), it.count, it.damageValue, it.maxDamage, it.maxDamage - it.damageValue, it.getEnchantmentsWithLevels().mapNotNull { (enchantment, level) ->
                        Enchantment(
                            server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getKey(enchantment)?.toString() ?: return@mapNotNull null,
                            enchantment.description.string,
                            level
                        )
                    })
                },
                player.inventory.armor.map {
                    PlayerItem(it.displayName.string, it.item.toString(), it.count, it.damageValue, it.maxDamage, it.maxDamage - it.damageValue, it.getEnchantmentsWithLevels().mapNotNull { (enchantment, level) ->
                        Enchantment(
                            server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getKey(enchantment)?.toString() ?: return@mapNotNull null,
                            enchantment.description.string,
                            level
                        )
                    })
                },
                player.inventory.offhand.map {
                    PlayerItem(it.displayName.string, it.item.toString(), it.count, it.damageValue, it.maxDamage, it.maxDamage - it.damageValue, it.getEnchantmentsWithLevels().mapNotNull { (enchantment, level) ->
                        Enchantment(
                            server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getKey(enchantment)?.toString() ?: return@mapNotNull null,
                            enchantment.description.string,
                            level
                        )
                    })
                }
            ))
        }

        get("/player/{player_name}/position") {
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

            val player = server.playerList.getPlayer(playerUuid)
            if (player == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            call.respond(PlayerPosition(
                player.level().dimension().location().toString(),
                player.level().getBiome(player.blockPosition()).unwrapKey().orElseThrow().location().toString(),
                PlayerCoordinates(
                    player.x,
                    player.y,
                    player.z
                ),
                player.direction.serializedName
            ))
        }
    }
}

@Serializable
private data class Players(
    val count: Int,
    val players: List<@Contextual PlayerInfoWithPing>
)

@Serializable
private data class PlayerInfoWithPing(
    @Serializable(with = UUIDSerializer::class) val uuid: UUID,
    val name: String,
    val ping: Int
)

@Serializable
private data class PlayerInfo(
    @Serializable(with = UUIDSerializer::class) val uuid: UUID,
    val name: String
)

@Serializable
private data class PlayerHealth(
    val current: Float,
    val max: Float,
)

@Serializable
private data class PlayerAbsorption(
    val current: Float,
    val max: Float,
)

@Serializable
private data class PlayerFood(
    val exhaustion: Float,
    val saturation: Float,
    val level: Int,
)

@Serializable
private data class PlayerAir(
    val current: Int,
    val max: Int,
)

@Serializable
private data class PlayerArmor(
    val value: Int,
    val percentage: Float,
    val max: Float,
    val absorption: Float,
)

@Serializable
private data class PlayerStatus( // todo: vehicle data
    val health: PlayerHealth,
    val absorption: PlayerAbsorption,
    val armor: PlayerArmor,
    val food: PlayerFood,
    val air: PlayerAir
)

@Serializable
private sealed interface PlayerStatistic<T> {
    val displayName: String
    @Contextual
    val value: T
}

@Serializable
private class PlayerCustomStatistic(
    override val displayName: String,
    override val value: Int
) : PlayerStatistic<Int>

@Serializable
private class PlayerItemsStatistic(
    override val displayName: String,
    override val value: MutableMap<ItemStatisticType, Int>,
) : PlayerStatistic<MutableMap<ItemStatisticType, Int>>

@Serializable
private enum class ItemStatisticType(private val value: StatType<*>) {
    USED(Stats.ITEM_USED),
    BROKEN(Stats.ITEM_BROKEN),
    CRAFTED(Stats.ITEM_CRAFTED),
    PICKED_UP(Stats.ITEM_PICKED_UP),
    DROPPED(Stats.ITEM_DROPPED),
    MINED(Stats.BLOCK_MINED);

    companion object {
        fun from(statisticType: StatType<*>): ItemStatisticType {
            return entries.first { it.value == statisticType }
        }
    }
}

@Serializable
private data class ServerStats(
    val dedicatedServer: Boolean,
    val acceptsTransfers: Boolean,
    val allowFlight: Boolean,
    val allowNether: Boolean,
    val serverBugReportLink: String,
    val commandBlockEnabled: Boolean,
    val difficulty: String,
    val players: List<@Contextual PlayerInfoWithPing>,
    val playerCount: Int,
    val maxPlayerCount: Int,
    val motd: String,
    val pvpEnabled: Boolean,
    val timeOfDay: Int
)

@Serializable
private data class PlayerInventory(
    val items: List<PlayerItem>,
    val armorItems: List<PlayerItem>,
    val offhandItem: List<PlayerItem>
)

@Serializable
private data class PlayerItem(
    val displayName: String,
    val item: String,
    val itemCount: Int,
    val durabilityDamage: Int,
    val maxDurability: Int,
    val remainingDurability: Int,
    val enchantments: List<Enchantment>
)

@Serializable
private data class Enchantment(
    val id: String,
    val displayName: String,
    val level: Int
)

@Serializable
private data class PlayerPosition(
    val dimension: String,
    val biome: String,
    val coordinates: PlayerCoordinates,
    val facing: String
)

@Serializable
private data class PlayerCoordinates(
    val x: Double,
    val y: Double,
    val z: Double
)