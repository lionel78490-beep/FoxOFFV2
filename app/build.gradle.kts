import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)

}

// Clé de signature "upload" Play Store (voir ROADMAP.md Phase 9,
// DECISIONS.md). Fichier keystore/keystore.properties volontairement
// git-ignoré (.gitignore) — jamais commité. Chargement optionnel : sans ce
// fichier (autre machine, CI), le build release reste possible mais non
// signé, plutôt que d'échouer.
val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.projectfox.foxoff"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.projectfox.foxoff"
        minSdk = 30
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file("keystore/${keystoreProperties["storeFile"]}")
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    // Emplacement des schémas Room exportés (utile pour les migrations
    // futures) — voir DECISIONS.md ADR-004.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    implementation("com.google.protobuf:protobuf-javalite:4.35.0")

    implementation(libs.bouncycastle.bcprov)
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.play.services.wearable)
    implementation(libs.garmin.connectiq)
    implementation(libs.androidx.health.connect.client)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // Implémentation réelle d'org.json pour les tests JVM purs : le stub
    // Android renvoyé par défaut (android.jar) lève une RuntimeException sur
    // tout appel (JSONObject.put, etc.), voir SleepDetectionHistory.
    testImplementation(libs.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    // Requis par SleepSessionDaoTest (runTest) — voir data/local/.
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

}

// Tests "lourds" du framework de simulation de nuits (2026-08-15, voir
// ROADMAP.md Phase 5) : rejouent 10 000 à 100 000 nuits synthétiques à
// travers le vrai moteur, plusieurs minutes chacun — exclus du
// `./gradlew test` de routine pour que les runs quotidiens restent
// rapides. Pour les lancer explicitement :
// `./gradlew test -PheavyTests --tests "*OptimizeWideSearchTest*"`.
tasks.withType<Test>().configureEach {
    if (!project.hasProperty("heavyTests")) {
        exclude(
            "**/OptimizeSleepScoringConfigTest.class",
            "**/OptimizeOnProfiledNightsTest.class",
            "**/OptimizeWideSearchTest.class",
            "**/OptimizeFinalOptimizationTest.class",
            "**/DiagnosticAnalysisTest.class",
            "**/ControlledExperimentTest.class",
            "**/ContinuityExperimentTest.class",
            "**/ContextualDetectionTest.class",
            "**/ActigraphyExperimentTest.class",
            "**/ProfiledNightGeneratorTest.class",
            "**/OptimizeWithRealNightsTest.class",
            "**/NextGenDetectionTest.class",
            "**/NextGenIndependentValidationTest.class"
        )
    }
}


