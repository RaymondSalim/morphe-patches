group = "app.hevy"

patches {
    about {
        name = "Hevy Pro Patches"
        description = "Patches for Hevy - Gym Log Workout Tracker"
        source = "https://github.com/RaymondSalim/morphe-patches"
        author = "RaymondSalim"
        contact = "https://github.com/RaymondSalim"
        website = "https://github.com/RaymondSalim/morphe-patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
    testImplementation(kotlin("test"))
    // The patcher publishes its transitive dependencies as runtime-scope, so
    // tests calling Patcher() need coroutines on the compile classpath.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
