import java.io.File
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun runCheckedProcess(
    workingDir: File,
    command: List<String>,
    extraEnvironment: Map<String, String> = emptyMap(),
) {
    val process = ProcessBuilder(command)
        .directory(workingDir)
        .inheritIO()
        .apply { environment().putAll(extraEnvironment) }
        .start()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "Command failed ($exitCode): ${command.joinToString(" ")}" }
}

val fairPlayRevision = "aaf5025267ba71d6eb5bb631d0b518b7354102a8"
val spdxLicenseRevision = "5bf6d9610255540bfbee6890765a616042bf1e11"
val fairPlayBridgeDir = rootProject.file("native/fairplay-bridge")
val fairPlayVendorDir = fairPlayBridgeDir.resolve("vendor/shairplay-rust")
val fairPlayMarker = fairPlayVendorDir.resolve(".atriscast-revision")
val fairPlayOutputDir = layout.buildDirectory.dir("generated/fairplay/jniLibs")
val fairPlayAssetsDir = layout.buildDirectory.dir("generated/fairplay/assets")
val fairPlayCargoTargetDir = layout.buildDirectory.dir("fairplay-cargo-target")
val skipFairPlayNative = providers.gradleProperty("skipFairPlayNative").orNull == "true"
val fairPlayAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val fairPlayLibraryName = "libatriscast_fairplay.so"

fun expectedFairPlayLibraries(outputDir: File): List<File> =
    fairPlayAbis.map { abi -> outputDir.resolve("$abi/$fairPlayLibraryName") }

android {
    namespace = "com.atrishub.atriscast"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.atrishub.atriscast"
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "0.1.0-alpha10"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9 no longer accepts Provider<Directory> through the legacy SourceSet API. These generated
    // paths are concrete Files here; preBuild below carries the task dependency that populates them.
    sourceSets.getByName("main").apply {
        jniLibs.srcDir(fairPlayOutputDir.get().asFile)
        assets.srcDir(fairPlayAssetsDir.get().asFile)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val prepareFairPlayRust = tasks.register("prepareFairPlayRust") {
    description = "Fetch the pinned LGPL shairplay-rust source used by the FairPlay JNI bridge."
    inputs.property("skipFairPlayNative", skipFairPlayNative)
    outputs.dir(fairPlayVendorDir)
    outputs.dir(fairPlayAssetsDir)
    onlyIf("FairPlay native bridge is enabled") { !skipFairPlayNative }

    doLast {
        val sourceFile = fairPlayVendorDir.resolve("src/crypto/fairplay.rs")
        val upstreamLicense = fairPlayVendorDir.resolve("LICENSE")
        val assetsLicenseDir = fairPlayAssetsDir.get().asFile.resolve("licenses")

        val vendorReady = fairPlayMarker.exists() &&
            fairPlayMarker.readText().trim() == fairPlayRevision &&
            sourceFile.exists() && upstreamLicense.exists()

        if (!vendorReady) {
            fairPlayVendorDir.deleteRecursively()
            fairPlayVendorDir.parentFile.mkdirs()
            fairPlayVendorDir.mkdirs()

            runCheckedProcess(fairPlayVendorDir, listOf("git", "init", "--quiet"))
            runCheckedProcess(
                fairPlayVendorDir,
                listOf("git", "remote", "add", "origin", "https://github.com/metaneutrons/shairplay-rust.git")
            )
            runCheckedProcess(fairPlayVendorDir, listOf("git", "fetch", "--quiet", "--depth", "1", "origin", fairPlayRevision))
            runCheckedProcess(fairPlayVendorDir, listOf("git", "checkout", "--quiet", "--detach", "FETCH_HEAD"))

            check(sourceFile.exists()) { "Pinned shairplay FairPlay source was not fetched" }
            val original = sourceFile.readText()
            val visibilityNeedle = "pub(crate) fn decrypt(&self"
            val visibilityIndex = original.indexOf(visibilityNeedle)
            check(visibilityIndex >= 0) {
                "Pinned shairplay FairPlay API changed; refusing to patch an unexpected source tree"
            }
            sourceFile.writeText(
                original.replaceRange(
                    visibilityIndex,
                    visibilityIndex + visibilityNeedle.length,
                    "pub fn decrypt(&self",
                )
            )
            fairPlayMarker.writeText(fairPlayRevision)
        }

        assetsLicenseDir.mkdirs()
        upstreamLicense.copyTo(assetsLicenseDir.resolve("LGPL-3.0-or-later.txt"), overwrite = true)

        // Ubuntu build hosts already ship the canonical GPL v3 text. Prefer that local copy so
        // normal CI does not depend on a second external host merely to package a license notice.
        // Other hosts use a revision-pinned SPDX copy as a deterministic fallback.
        val systemGpl = File("/usr/share/common-licenses/GPL-3")
        val gplBytes = if (systemGpl.isFile) {
            systemGpl.readBytes()
        } else {
            URI(
                "https://raw.githubusercontent.com/spdx/license-list-data/" +
                    "$spdxLicenseRevision/text/GPL-3.0-only.txt"
            ).toURL().readBytes()
        }
        assetsLicenseDir.resolve("GPL-3.0.txt").writeBytes(gplBytes)
    }
}

val buildFairPlayBridge = tasks.register("buildFairPlayBridge") {
    description = "Build the LGPL FairPlay decryptor as a replaceable Android shared library."
    dependsOn(prepareFairPlayRust)
    inputs.property("skipFairPlayNative", skipFairPlayNative)
    outputs.dir(fairPlayOutputDir)
    onlyIf("FairPlay native bridge is enabled") { !skipFairPlayNative }
    outputs.upToDateWhen {
        expectedFairPlayLibraries(fairPlayOutputDir.get().asFile).all { library ->
            library.isFile && library.length() > 0L
        }
    }

    doLast {
        val outputDir = fairPlayOutputDir.get().asFile
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        runCheckedProcess(
            fairPlayBridgeDir,
            listOf(
                "cargo", "ndk",
                "-t", "arm64-v8a",
                "-t", "armeabi-v7a",
                "-t", "x86_64",
                "-o", outputDir.absolutePath,
                "build", "--release",
                "--manifest-path", fairPlayBridgeDir.resolve("Cargo.toml").absolutePath,
            ),
            extraEnvironment = mapOf(
                "CARGO_TARGET_DIR" to fairPlayCargoTargetDir.get().asFile.absolutePath,
            ),
        )

        val missingLibraries = expectedFairPlayLibraries(outputDir).filterNot { library ->
            library.isFile && library.length() > 0L
        }
        check(missingLibraries.isEmpty()) {
            "FairPlay native bridge build completed without expected libraries: " +
                missingLibraries.joinToString { it.relativeTo(outputDir).path }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(buildFairPlayBridge)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)

    implementation("androidx.core:core:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.tv:tv-foundation:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.googlecode.plist:dd-plist:1.29")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
