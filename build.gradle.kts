import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
	kotlin("jvm") version "2.0.21"
	`maven-publish`
	java

	alias(libs.plugins.grgit)
	alias(libs.plugins.fabric.loom)
	alias(libs.plugins.ktor)
	alias(libs.plugins.kotlinx.serialization)
	alias(libs.plugins.shadow) apply false
}

val shade: Configuration by configurations.creating { }
val archivesBaseName = "${project.property("archives_base_name").toString()}+mc${libs.versions.minecraft.get()}"
version = getModVersion()
group = project.property("maven_group")!!

/*
*
* Taken from Deftu's Gradle-Toolkit with permission, and have explicit permission from Deftu himself to be excluded from Deftu's Gradle-Toolkit's LGPLv3 license
*
* src: https://github.com/Deftu/Gradle-Toolkit/blob/main/src/main/kotlin/dev/deftu/gradle/tools/shadow.gradle.kts
*
* lines affected: 15, 30-82
*
*/

val fatJar = tasks.register<ShadowJar>("fatJar") {
	group = "exposer"
	description = "Builds a fat JAR with all dependencies shaded in"
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	configurations = listOf(shade)
	archiveVersion.set(project.version.toString())
	archiveClassifier.set("all")

	val javaPlugin = project.extensions.getByType(JavaPluginExtension::class.java)
	val jarTask = project.tasks.getByName("jar") as Jar

	manifest.inheritFrom(jarTask.manifest)
	val libsProvider = project.provider { listOf(jarTask.manifest.attributes["Class-Path"]) }
	val files = project.objects.fileCollection().from(shade)
	doFirst {
		if (!files.isEmpty) {
			val libs = libsProvider.get().toMutableList()
			libs.addAll(files.map { it.name })
			manifest.attributes(mapOf("Class-Path" to libs.filterNotNull().joinToString(" ")))
		}
	}

	from(javaPlugin.sourceSets.getByName("main").output)
	exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
}

project.artifacts.add("shade", fatJar)

pluginManager.withPlugin("java") {
	tasks["assemble"].dependsOn(fatJar)
}

tasks {
	val shadowJar = findByName("shadowJar")
	if (shadowJar != null) {
		named("shadowJar") {
			doFirst {
				throw GradleException("Incorrect task! You're looking for fatJar.")
			}
		}
	}
}

loom {
	tasks {
		fatJar {
			archiveClassifier.set("dev")
		}

		remapJar {
			inputFile.set(fatJar.get().archiveFile)
			archiveClassifier.set("")
		}
	}
}

repositories {
	mavenCentral()
	maven("https://api.modrinth.com/maven")
	maven("https://maven.terraformersmc.com/")
	maven("https://maven.parchmentmc.org")
	maven("https://mvn.devos.one/snapshots")
	maven("https://maven.quiltmc.org/repository/release/")
}

//All dependencies and their versions are in ./gradle/libs.versions.toml
dependencies {

	minecraft(libs.minecraft)

	mappings(loom.layered {
		officialMojangMappings()
		parchment("org.parchmentmc.data:parchment-1.21:2024.07.28@zip")
//		mappings("${libs.quilt.mappings.get()}:intermediary-v2") // if you wanna deal with it be my guest, im not - asoji
	})

	//Fabric
	modImplementation(libs.fabric.loader)
	modImplementation(libs.fabric.api)
	modImplementation(libs.fabric.language.kotlin)

	//Mods
	modImplementation(libs.bundles.dependencies)
	modLocalRuntime(libs.bundles.dev.mods)

	include(modImplementation("gay.asoji:fmw:1.0.0+build.8")!!) // just to avoid the basic long metadata calls

	implementation(libs.bundles.ktor)
	shade(libs.bundles.ktor)
}

// Write the version to the fabric.mod.json
tasks.processResources {
	inputs.property("version", project.version)

	filesMatching("fabric.mod.json") {
		expand(mutableMapOf("version" to project.version))
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release.set(21)
}

java {
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
	from("LICENSE") {
		rename { "${it}_${project.base.archivesName.get()}"}
	}
}

// This will attempt to publish the mod to the devOS Maven, otherwise it will build the mod locally
// This is auto run by GitHub Actions
task("buildOrPublish") {
	group = "build"
	var mavenUser = System.getenv().get("MAVEN_USER")
	if (!mavenUser.isNullOrEmpty()) {
		dependsOn(tasks.getByName("publish"))
		println("prepared for publish")
	} else {
		dependsOn(tasks.getByName("build"))
		println("prepared for build")
	}
}

// TODO: Uncomment for a non template mod!
publishing {
//	publications {
//		create<MavenPublication>("mavenJava") {
//			groupId = project.property("maven_group").toString()
//			artifactId = project.property("archives_base_name").toString()
//			version = getModVersion()
//
//			from(components.get("java"))
//		}
//	}
//
//	repositories {
//		maven {
//			url = uri("https://mvn.devos.one/${System.getenv()["PUBLISH_SUFFIX"]}/")
//			credentials {
//				username = System.getenv()["MAVEN_USER"]
//				password = System.getenv()["MAVEN_PASS"]
//			}
//		}
//	}
}

application {
	mainClass.set("one.devos.nautical.exposeplayers.ExposePlayersKt")

	val isDevelopment: Boolean = project.ext.has("development")
	applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

fun getModVersion(): String {
	val modVersion = project.property("mod_version")
	val buildId = System.getenv("GITHUB_RUN_NUMBER")

	// CI builds only
	if (buildId != null) {
		return "${modVersion}+build.${buildId}"
	}

	// If a git repo can't be found, grgit won't work, this non-null check exists so you don't run grgit stuff without a git repo
	if (grgitService.service.get().grgit.head() != null) {
		var id = grgitService.service.get().grgit.head().abbreviatedId ?: "NO-COMMIT-HASH"

		// Flag the build if the build tree is not clean
		// (aka you have uncommitted changes)
		if (!grgitService.service.get().grgit.status().isClean()) {
			id += "-dirty"
		}
		// ex: 1.0.0+rev.91949fa or 1.0.0+rev.91949fa-dirty
		return "${modVersion}+rev.${id}"
	}

	// No tracking information could be found about the build
	return "${modVersion}+unknown"

}