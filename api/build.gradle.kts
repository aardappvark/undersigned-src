plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}
kotlin { jvmToolchain(17) }
application { mainClass.set("com.undersigned.api.ServerKt") }
dependencies {
    implementation(project(":protocol"))
    implementation("org.json:json:20240303")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
// One source of truth for reward seasons: the app's own asset.
tasks.processResources { from("../app/src/main/assets/seasons.json") }
