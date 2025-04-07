dependencies {
    compileOnly(libs.bundles.boost)
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:boostforreddit:stub"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

android {
    defaultConfig {
        minSdk = 26
    }
}
