plugins {
    id("com.android.application")
}

android {
    namespace = "com.lexia.accueil"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lexia.accueil"
        minSdk = 22
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Signature : la clé des secrets GitHub si elle est fournie (KEYSTORE_BASE64…),
    // sinon la clé partagée du dépôt, toujours la même pour que les mises à jour
    // s'installent par-dessus la version précédente sans perdre l'adresse réglée.
    val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")
    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else {
                storeFile = file("accueil-partage.jks")
                storePassword = "lexia-accueil"
                keyAlias = "accueil"
                keyPassword = "lexia-accueil"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
