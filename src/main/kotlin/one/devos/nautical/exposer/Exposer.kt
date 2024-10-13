package one.devos.nautical.exposer

import gay.asoji.fmw.FMW
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.routing.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import one.devos.nautical.exposer.plugins.configureRouting
import one.devos.nautical.exposer.plugins.configureSerialization
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Exposer : ModInitializer {
    val MOD_ID: String = "exposer"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    val MOD_NAME: String = FMW.getName(MOD_ID)

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    override fun onInitialize() {
        LOGGER.info("[${MOD_NAME}] Starting up ExposePlayers")

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            this.server?.stop()
            this.server = embeddedServer(Netty, port = 64589, host = "0.0.0.0", module = {
                install(IgnoreTrailingSlash)
                install(Compression)

                configureRouting(server)
                configureSerialization()
            }).start(wait = false)
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            this.server?.stop()
            this.server = null
        }
    }
}