import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val mapsApiKey = providers.gradleProperty("MAPS_API_KEY")
    .orElse(providers.environmentVariable("MAPS_API_KEY"))
    .getOrElse("YOUR_API_KEY")
val generatedAssetsDirectory = layout.buildDirectory.dir("generated/scouterAssets")
val generateScouterSchemaAsset by tasks.registering(Copy::class) {
    from(rootProject.file("scouter.schema.json"))
    into(generatedAssetsDirectory)
}

android {
    namespace = "app.scouter"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.scouter"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("boolean", "GOOGLE_MAPS_CONFIGURED", (mapsApiKey != "YOUR_API_KEY").toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    sourceSets["main"].assets.srcDir(generatedAssetsDirectory)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.animation:animation:1.7.8")
    implementation("androidx.compose.foundation:foundation:1.7.8")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.android.gms:play-services-maps:20.0.0")
    implementation("io.ktor:ktor-server-cio:3.5.1")
    implementation("io.ktor:ktor-server-body-limit:3.5.1")
    implementation("io.ktor:ktor-server-status-pages:3.5.1")
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.slf4j:slf4j-android:1.7.36")

    debugImplementation("androidx.compose.ui:ui-tooling:1.7.8")
}

tasks.named("preBuild").configure {
    dependsOn(generateScouterSchemaAsset)
}
