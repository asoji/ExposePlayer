package one.devos.nautical.exposeplayers.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.stats.Stats
import one.devos.nautical.exposeplayers.utils.UUIDSerializer
import one.devos.nautical.exposeplayers.utils.getPlayerStatsByUuid
import java.util.UUID

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

            val statsCounter = server.playerList.getPlayerStatsByUuid(playerUuid, playerName)
//            call.respond(BuiltInRegistries.STAT_TYPE.map { statisticType -> {
//                statisticType.flatMap
//            })

            
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
private data class PlayerStatistic(
    val displayName: String,
    val value: Any
)