import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.chaquo)
}

val abis = listOf("arm64-v8a", "x86_64")
val abiFlavorNames = mapOf("arm64-v8a" to "arm64", "x86_64" to "x64")
val baseVersionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull() ?: 26

android {
    val appId = "${project.group}.exilonium"

    namespace = appId
    compileSdk = 37

    val cmakeVersion = "4.1.2"
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = appId

        minSdk = 24
        targetSdk = 37

        versionCode = baseVersionCode
        versionName = project.version.toString()

        multiDexEnabled = true

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    // One APK per ABI, and deliberately no universal one. Chaquopy duplicates a whole Python
    // runtime per ABI, so a universal build lands near 28 MB against IzzyOnDroid's 30 MB per-file
    // limit while the per-ABI builds sit near 21 MB. Chaquopy insists on ndk.abiFilters, which AGP
    // refuses to combine with splits.abi, so the flavors carry the filter instead.
    //
    // A universal APK would reach no device this pair doesn't already cover - an APK without a
    // matching ABI is refused outright - so it only ever added a trap: it has to rank below the
    // per-ABI builds for stores to prefer those, which means a device that installed an ABI build
    // cannot install the universal one over it. Android reports that downgrade as a malformed
    // package, so the file that looked like the safe choice was the one that failed.
    //
    // Each ABI gets its own decamillion range above baseVersionCode, per Google's ABI-split recipe,
    // so anything ranking by version code keeps handing a device its own build. baseVersionCode is
    // still the release anchor: it names the changelog file and matches the version tag.
    flavorDimensions += "abi"

    productFlavors {
        abis.forEachIndexed { index, abi ->
            create(abiFlavorNames.getValue(abi)) {
                dimension = "abi"
                versionCode = baseVersionCode + (index + 1) * 10_000_000

                ndk {
                    //noinspection ChromeOsAbiSupport
                    abiFilters += abi
                }
            }
        }
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val hasReleaseKeystore = keystorePropertiesFile.exists() || System.getenv("ANDROID_KEYSTORE") != null

    signingConfigs {
        create("ci") {
            storeFile = System.getenv("ANDROID_NIGHTLY_KEYSTORE")?.let { file(it) }
            storePassword = System.getenv("ANDROID_NIGHTLY_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_NIGHTLY_KEYSTORE_ALIAS")
            keyPassword = System.getenv("ANDROID_NIGHTLY_KEYSTORE_PASSWORD")
        }

        if (hasReleaseKeystore) {
            create("release") {
                if (keystorePropertiesFile.exists()) {
                    val properties = Properties()
                    FileInputStream(keystorePropertiesFile).use { properties.load(it) }
                    storeFile = properties.getProperty("storeFile")?.let { rootProject.file(it) }
                    storePassword = properties.getProperty("storePassword")
                    keyAlias = properties.getProperty("keyAlias")
                    keyPassword = properties.getProperty("keyPassword")
                } else if (System.getenv("ANDROID_KEYSTORE") != null) {
                    storeFile = System.getenv("ANDROID_KEYSTORE")?.let { file(it) }
                    storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("ANDROID_KEYSTORE_ALIAS")
                    keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            manifestPlaceholders["appName"] = "eXMusic Debug"
        }

        release {
            versionNameSuffix = "-RELEASE"
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appName"] = "eXMusic"
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.findByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("nightly") {
            initWith(getByName("release"))
            matchingFallbacks += "release"

            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-NIGHTLY"
            manifestPlaceholders["appName"] = "eXMusic Nightly"
            signingConfig = signingConfigs.findByName("ci")
        }
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources.excludes.add("META-INF/**/*")
        jniLibs.useLegacyPackaging = true
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    externalNativeBuild {
        cmake {
            version = cmakeVersion
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

afterEvaluate {
    val jniLibs = file("${layout.projectDirectory}/src/main/jniLibs").also { it.mkdirs() }
    android.buildTypes.forEach { type ->
        val typeCapitalized = type.name.let {
            it.first().uppercase() + it.substring(1)
        }

        tasks.named("assemble${typeCapitalized}").configure {
            doFirst {
                val cxxDir =
                    file("${layout.buildDirectory.get()}/intermediates/cxx/${if (typeCapitalized == "Debug") "Debug" else "RelWithDebInfo"}")

                cxxDir.walkTopDown().forEach cxx@{ f ->
                    if (f.name != "qjs") return@cxx

                    f.copyTo(
                        target = jniLibs
                            .resolve(f.parentFile.name)
                            .also { it.mkdirs() }
                            .resolve("libqjs.so"), // disguise because fuck you
                        overwrite = true
                    )
                }
            }
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())

    compilerOptions {
        // Don't set languageVersion ahead of the compiler's stable version: it makes kotlinc emit
        // metadata R8 can't parse yet, which floods every build with metadata warnings
        freeCompilerArgs.addAll(
            "-Xconsistent-data-class-copy-visibility"
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

composeCompiler {
    if (project.findProperty("enableComposeCompilerReports") == "true") {
        val dest = layout.buildDirectory.dir("compose_metrics")
        metricsDestination = dest
        reportsDestination = dest
    }
}

chaquopy {
    defaultConfig {
        version = "3.14"
        pip {
            install("yt-dlp>=2026.08.19")
            install("yt-dlp-ejs>=0.8.0")
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)

    implementation(projects.compose.persist)
    implementation(projects.compose.preferences)
    implementation(projects.compose.routing)
    implementation(projects.compose.reordering)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.activity)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.shimmer)
    implementation(libs.compose.lottie)
    implementation(libs.compose.material3)

    implementation(libs.coil.compose)
    implementation(libs.coil.ktor)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.palette)
    implementation(libs.monet)
    runtimeOnly(projects.core.materialCompat)

    implementation(libs.exoplayer)
    implementation(libs.exoplayer.workmanager)
    implementation(libs.media3.session)
    implementation(libs.media)

    implementation(libs.workmanager)
    implementation(libs.workmanager.ktx)

    implementation(libs.credentials)

    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.immutable)
    implementation(libs.kotlin.datetime)

    implementation(libs.room)
    ksp(libs.room.compiler)

    implementation(libs.slf4j)
    implementation(libs.logback)

    implementation(projects.providers.github)
    implementation(projects.providers.innertube)
    implementation(projects.providers.kugou)
    implementation(projects.providers.lrclib)
    implementation(projects.providers.piped)
    implementation(projects.providers.sponsorblock)
    implementation(projects.providers.translate)
    implementation(projects.core.data)
    implementation(projects.core.ui)
}
