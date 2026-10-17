import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.burooj.speedbreaker"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dev.burooj.speedbreaker"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.3"
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    // community: F-Droid's build, no network. play: Google Play, no network.
    // github: the APK on GitHub Releases, community plus an in-app updater, and the only
    // build with INTERNET and REQUEST_INSTALL_PACKAGES. All three share one package and key.
    flavorDimensions += "distribution"
    productFlavors {
        create("community") { dimension = "distribution" }
        create("play") { dimension = "distribution" }
        create("github") { dimension = "distribution" }
    }

    sourceSets {
        // The GitHub build keeps the community support link; the store builds share the
        // offline stand-in for the updater, so neither carries any network code.
        getByName("github") { kotlin.srcDir("src/community/java") }
        getByName("community") { kotlin.srcDir("src/offline/java") }
        getByName("play") { kotlin.srcDir("src/offline/java") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // The external provenance receipt records HEAD; merge commits with identical
            // source trees must not change APK bytes inside META-INF.
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs(
                    "-Xmx2g",
                    "-Droborazzi.test.record=true"
                )
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    annotationProcessor("androidx.room:room-compiler:2.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Evidence for the resolved release runtime, including transitives and file hashes.
listOf("community", "play", "github").forEach { channel ->
    val variant = channel.replaceFirstChar { it.uppercase() }
    tasks.register("inventory${variant}Release") {
        val runtime = configurations.named("${channel}ReleaseRuntimeClasspath")
        val output = layout.buildDirectory.file("store-inventory/$channel/dependencies.json")
        inputs.files(runtime)
        outputs.file(output)
        doLast {
            val config = runtime.get()
            fun component(id: org.gradle.api.artifacts.component.ComponentIdentifier): Map<String, String> =
                if (id is org.gradle.api.artifacts.component.ModuleComponentIdentifier)
                    mapOf("group" to id.group, "name" to id.module, "version" to id.version)
                else mapOf("displayName" to id.displayName)
            fun sha256(file: java.io.File): String {
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { stream ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }
            val artifacts = config.incoming.artifacts.artifacts.map { artifact ->
                mapOf(
                    "component" to component(artifact.id.componentIdentifier),
                    "filename" to artifact.file.name,
                    "extension" to artifact.file.extension,
                    "sha256" to sha256(artifact.file),
                )
            }.sortedWith(compareBy({ it["component"].toString() }, { it["filename"].toString() }))
            val nodes = config.incoming.resolutionResult.allComponents.map { resolved ->
                mapOf(
                    "component" to component(resolved.id),
                    "dependencies" to resolved.dependencies.map { dependency ->
                        if (dependency is org.gradle.api.artifacts.result.ResolvedDependencyResult)
                            mapOf("requested" to dependency.requested.displayName,
                                  "selected" to component(dependency.selected.id))
                        else mapOf("requested" to dependency.requested.displayName,
                                    "unresolved" to dependency.toString())
                    }.sortedBy { it["requested"].toString() },
                )
            }.sortedBy { it["component"].toString() }
            val result = mapOf("schemaVersion" to 1, "channel" to channel,
                "configuration" to config.name, "artifacts" to artifacts, "graph" to nodes)
            output.get().asFile.apply {
                parentFile.mkdirs()
                writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(result)) + "\n")
            }
        }
    }
}
