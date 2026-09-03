import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application")
    id("com.github.ben-manes.versions")
    id("com.google.devtools.ksp") version "2.3.7"
    id("com.anthonycr.plugins.mezzanine") version "2.3.0"
    id("com.autonomousapps.dependency-analysis") version "3.18.0"
    id("com.squareup.sort-dependencies") version "0.20.0"
}

android {
    compileSdk = 36

    // Release signing is supplied by the private build environment. Keeping the
    // keystore path and credentials outside source control prevents accidental
    // disclosure while ensuring companion Antares and Solipsism builds share an
    // identity for certificate pinning.
    val releaseKeystorePath = providers.environmentVariable("KEYSTORE").orNull
    val releaseKeystorePassword = providers.environmentVariable("STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
    val hasReleaseSigning = listOf(
        releaseKeystorePath,
        releaseKeystorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionName = "8.0.2"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        aidl = true
    }

    buildTypes {
        named("debug") {
            multiDexEnabled = true
            isMinifyEnabled = false
            isShrinkResources = false
            setProguardFiles(listOf("proguard-project.txt"))
            enableUnitTestCoverage = false
            enableAndroidTestCoverage = false
        }

        named("release") {
            multiDexEnabled = false
            isMinifyEnabled = true
            isShrinkResources = true
            setProguardFiles(listOf("proguard-project.txt"))
            enableUnitTestCoverage = false
            enableAndroidTestCoverage = false

            if (hasReleaseSigning) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(releaseKeystorePath!!)
                    storePassword = releaseKeystorePassword
                    keyAlias = releaseKeyAlias
                    keyPassword = releaseKeyPassword
                }
            }

            ndk {
                abiFilters.add("arm64-v8a")
                abiFilters.add("armeabi-v7a")
            }
        }
    }

    flavorDimensions.add("capabilities")

    productFlavors {
        create("solipsismBrowser") {
            dimension = "capabilities"
            buildConfigField("boolean", "FULL_VERSION", "Boolean.parseBoolean(\"true\")")
            buildConfigField("String", "RELEASE_SITE_URL", "\"https://kenneth-cho-infosec.github.io/Solipsism/\"")
            buildConfigField(
                "String",
                "ANTARES_CERT_SHA256",
                "\"${providers.gradleProperty("antaresCertSha256").orElse("").get()}\""
            )
            // 0.1.1 was published with the original public debug certificate. Keep this
            // compatibility pin so existing installs can update Solipsism before updating
            // Antares. It is intentionally limited to the known certificate, never any
            // certificate supplied by the installed package.
            buildConfigField(
                "String",
                "ANTARES_LEGACY_CERT_SHA256",
                "\"480BED986E48910C5A028C60A207E85535AE00832AECDAEFA5C1BC2D68D80EEF\""
            )
            applicationId = "com.krystelligence.solipsism"
            versionCode = 149
        }
    }
    packaging {
        jniLibs {
            // Compress native libraries in the APK. Android extracts them at install time.
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf(
                ".readme",
                "**/*.prof",
                "**/*.profraw",
                "**/*.so.dbg",
                "**/symbols/**",
            )
        }
    }
    lint {
        abortOnError = true
    }
    namespace = "com.krystelligence.solipsism"
}

dependencies {
    val robolectric = "4.16.1"
    val mezzanineVersion = "2.3.0"
    val daggerVersion = "2.60.1"
    val kotlin = "2.3.21"
    val datastore = "1.2.1"
    val coil = "3.4.0"
    val cameraX = "1.6.1"
    val media3 = "1.11.0"

    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.annotation:annotation:1.10.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    implementation("androidx.core:core:1.18.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.datastore:datastore:$datastore")
    implementation("androidx.datastore:datastore-core:$datastore")
    implementation("androidx.datastore:datastore-preferences:$datastore")
    implementation("androidx.datastore:datastore-preferences-core:$datastore")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.lifecycle:lifecycle-common:2.11.0")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.palette:palette:1.0.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.webkit:webkit:1.16.0")
    implementation("com.anthonycr.mezzanine:core:$mezzanineVersion")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.dagger:dagger:$daggerVersion")
    implementation("com.google.guava:guava:33.6.0-android")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.guolindev.permissionx:permissionx:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okio:okio:3.18.1")
    implementation("io.coil-kt.coil3:coil:$coil")
    implementation("io.coil-kt.coil3:coil-core:$coil")
    implementation("io.coil-kt.coil3:coil-network-okhttp:$coil")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("io.reactivex.rxjava3:rxjava:3.1.12")
    implementation("io.reactivex.rxjava3:rxkotlin:3.0.1")
    implementation("javax.inject:javax.inject:1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jsoup:jsoup:1.23.1")
    implementation("org.jspecify:jspecify:1.0.1")
    implementation("org.reactivestreams:reactive-streams:1.0.4")

    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    compileOnly("javax.annotation:jsr250-api:1.0")

    testImplementation("com.nhaarman:mockito-kotlin:1.6.0") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.robolectric:annotations:$robolectric")
    testImplementation("org.robolectric:robolectric:$robolectric")
    testImplementation("org.robolectric:shadows-framework:$robolectric")

    ksp("com.anthonycr.mezzanine:processor:$mezzanineVersion")
    ksp("com.google.dagger:dagger-compiler:$daggerVersion")
}

mezzanine {
    files = files(
        "src/main/html/list.html",
        "src/main/html/bookmarks.html",
        "src/main/js/InvertPage.js",
        "src/main/js/TextReflow.js",
        "src/main/js/ThemeColor.js"
    )
}

val mezzanineGeneratedDir = layout.buildDirectory
    .dir("mezzanineGenerated/com/anthonycr/mezzanine")
    .map { it.asFile }
val mezzanineReaderAliases = mapOf(
    "511272597" to "1433655294",
    "1621098914" to "1591862187",
    "669645893" to "11812658",
    "591841962" to "1678998121",
    "1744299999" to "279827214",
    "90424053" to "2114551266"
)

abstract class GenerateTranslationRegistryTask : DefaultTask() {
    @get:InputFile
    abstract val sourceStrings: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val names = Regex("<string\\s+name=\"([a-z][a-z0-9_]*)\"")
            .findAll(sourceStrings.get().asFile.readText())
            .map { it.groupValues[1] }
            .distinct()
            .sorted()
            .toList()
        val output = outputDirectory.get().asFile.resolve(
            "com/krystelligence/solipsism/i18n/GeneratedStringResources.kt"
        )
        output.parentFile.mkdirs()
        output.writeText(buildString {
            appendLine("package com.krystelligence.solipsism.i18n")
            appendLine()
            appendLine("import com.krystelligence.solipsism.R")
            appendLine()
            appendLine("internal object GeneratedStringResources {")
            appendLine("    val ids: Map<String, Int> = mapOf(")
            names.forEachIndexed { index, name ->
                val comma = if (index == names.lastIndex) "" else ","
                appendLine("        \"$name\" to R.string.$name$comma")
            }
            appendLine("    )")
            appendLine("}")
        })
    }
}

val generateTranslationRegistry = tasks.register<GenerateTranslationRegistryTask>("generateTranslationRegistry") {
    sourceStrings.set(layout.projectDirectory.file("src/main/res/values/strings.xml"))
    outputDirectory.set(layout.buildDirectory.dir("generated/translationRegistry"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(generateTranslationRegistry) {
            it.outputDirectory
        }
    }
}

tasks.named("preBuild") {
    dependsOn(generateTranslationRegistry)
}

tasks.named("generateMezzanine") {
    doLast {
        val generatedDir = mezzanineGeneratedDir.get()
        mezzanineReaderAliases.forEach { (generatedId, processorId) ->
            val generatedFile = generatedDir.resolve("_MezzanineReader_$generatedId.kt")
            val processorFile = generatedDir.resolve("_MezzanineReader_$processorId.kt")
            if (generatedFile.isFile && !processorFile.exists()) {
                processorFile.writeText(
                    """
                    package com.anthonycr.mezzanine

                    public object _MezzanineReader_$processorId {
                      public fun readFromMezzanine(): kotlin.String =
                        _MezzanineReader_$generatedId.readFromMezzanine()
                    }
                    """.trimIndent()
                )
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}
