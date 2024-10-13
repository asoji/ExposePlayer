package one.devos.nautical.exposer.plugins

import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }

//    routing {
//        get("/json/kotlinx-serialization") {
//            call.respond(
//                mapOf("hello" to "world")
//            )
//        }
//    }
}