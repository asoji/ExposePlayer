package one.devos.nautical.template

import gay.asoji.fmw.FMW
import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * This is your mod's main entrypoint class, this runs on both Client and Server.
 * What you put in here will be loaded on mod initialization time
 */
object TemplateMod : ModInitializer {
    /**
     * This `MOD_ID` value is what your `id` in the fabric.mod.json should be.
     * Anything that references your mod should call this value/string.
     */
    val MOD_ID: String = "template"

    /**
     * This logger is used to write text to the console and the log file.
     * It is considered best practice to use your mod id as the logger's name.
     * That way, it's clear which mod wrote info, warnings, and errors.
     */
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    /**
     * This `MOD_NAME` gets what your mod's user-friendly name is from the `fabric.mod.json`
     */
    val MOD_NAME: String = FMW.getName(MOD_ID)

    /**
     * This code runs as soon as Minecraft is in a mod-load-ready state.
     * However, some things (like resources) may still be uninitialized.
     * Proceed with mild caution.
     */
    override fun onInitialize() {
        LOGGER.info("[${MOD_NAME}] Hello Fabric world from $MOD_NAME/$MOD_ID")
    }
}