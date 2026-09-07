package app.hevy.patches

import app.hevy.patches.hermespaywall.hermes.PaywallMod
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.Assume

class PatcherEndToEndTest {

    @Test
    fun patchesTheOriginalApkIntoTheKnownGoodMod() {
        val apkPath = System.getenv("HEVY_TEST_ORIGINAL_APK")
        val originalBundlePath = System.getenv("HEVY_TEST_ORIGINAL_BUNDLE")
        Assume.assumeTrue("e2e env vars not set", apkPath != null && originalBundlePath != null)

        val outputDir = File(System.getenv("HEVY_TEST_OUTPUT_DIR") ?: "build/e2e-output")
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        Patcher(
            PatcherConfig(
                apkFile = File(apkPath),
                temporaryFilesPath = File("build/e2e-temp"),
            )
        ).use { patcher ->
            patcher += allPatches.toSet()

            // Patcher.invoke is a suspend function returning a Flow of patch
            // results; collect it on a blocking thread for the JUnit harness.
            runBlocking {
                patcher().collect { result ->
                    result.exception?.let { exception ->
                        fail("Patch ${result.patch.name} failed: $exception", exception)
                    }
                }
            }

            val patcherResult = patcher.get()

            // 1. The patched Hermes bundle must be byte-identical to the
            //    known-good mod (PaywallMod output, trailer recomputed).
            val expectedBundle = File(originalBundlePath).readBytes().also { PaywallMod.apply(it) }
            val patchedBundle = findFile(
                listOfNotNull(patcherResult.resources.otherResources, File("build/e2e-temp/apk")),
                "index.android.bundle",
            )
            assertContentEquals(expectedBundle, patchedBundle.readBytes())

            // 2. Stage the compiled resources for the manifest assertion.
            val resourcesApk = patcherResult.resources.resourcesApk
                ?: fail("Expected compiled resources APK")
            val staged = outputDir.resolve("resources.apk")
            resourcesApk.copyTo(staged, overwrite = true)
        }

        // 3. Decode the compiled resources and assert the renamed manifest.
        val decodedDir = outputDir.resolve("decoded")
        val process = ProcessBuilder(
            "apktool", "d", "-f", "-s",
            "-o", decodedDir.absolutePath,
            outputDir.resolve("resources.apk").absolutePath,
        ).redirectErrorStream(true).start()
        val apktoolOutput = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "apktool failed:\n$apktoolOutput" }

        ManifestAssertions.assertRenamedManifest(decodedDir.resolve("AndroidManifest.xml"))
    }

    private fun findFile(roots: List<File>, name: String): File {
        val matches = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.name == name }.toList()
        }
        return matches.firstOrNull()
            ?: fail("Could not find $name in patched output; searched: $roots")
    }
}
