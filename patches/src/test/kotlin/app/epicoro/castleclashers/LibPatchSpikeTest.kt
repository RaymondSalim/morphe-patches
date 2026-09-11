package app.epicoro.castleclashers

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.patch.rawResourcePatch
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.Assume

private val libPatchSpikePatch = rawResourcePatch(
    name = "lib patch spike",
    description = "Round-trip write into lib/arm64-v8a/libil2cpp.so",
) {
    execute {
        val so = get("lib/arm64-v8a/libil2cpp.so")
        check(so.exists()) { "lib/arm64-v8a/libil2cpp.so not found in input APK" }
        val data = so.readBytes()
        for (i in 0x100..0x103) data[i] = 0x5A
        so.writeBytes(data)
    }
}

class LibPatchSpikeTest {

    @Test
    fun rawPatchRoundTripsLibil2cppWrite() {
        val apkPath = System.getenv("CC_TEST_APK")
        Assume.assumeTrue("CC_TEST_APK not set", apkPath != null)
        val inputApk = File(apkPath)

        val original = ZipFile(inputApk).use { zf ->
            zf.getInputStream(zf.getEntry("lib/arm64-v8a/libil2cpp.so")).use { it.readBytes() }
        }

        val started = System.currentTimeMillis()
        Patcher(PatcherConfig(inputApk, File("build/cc-spike-temp"))).use { patcher ->
            patcher += setOf(libPatchSpikePatch)
            runBlocking {
                patcher().collect { result ->
                    result.exception?.let { e -> fail("Spike patch failed: $e", e) }
                }
            }
        }
        println("Spike patcher run took ${System.currentTimeMillis() - started} ms")

        val patchedFile = File("build/cc-spike-temp").walkTopDown()
            .filter { it.isFile && it.path.endsWith("lib/arm64-v8a/libil2cpp.so") }
            .firstOrNull() ?: fail("spiked libil2cpp.so not found in patcher temp output")
        val patched = patchedFile.readBytes()

        assertEquals(original.size, patched.size)
        for (i in 0x100..0x103) {
            assertEquals(0x5A.toByte(), patched[i], "byte at 0x${i.toString(16)}")
        }
    }
}
