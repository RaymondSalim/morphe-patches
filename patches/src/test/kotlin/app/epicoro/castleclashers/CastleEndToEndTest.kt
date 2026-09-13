package app.epicoro.castleclashers

import app.epicoro.castleclashers.patches.allPatches
import app.epicoro.castleclashers.patches.native.Arm64Patcher
import app.epicoro.castleclashers.patches.native.adsSites
import app.epicoro.castleclashers.patches.native.aimGuideSites
import app.epicoro.castleclashers.patches.native.codeHashSites
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.w3c.dom.Element

class CastleEndToEndTest {

    @Test
    fun patchesCastleBustersEndToEnd() {
        val apkPath = System.getenv("CC_TEST_APK")
        Assume.assumeTrue("CC_TEST_APK not set", apkPath != null)
        val inputApk = File(apkPath)
        val outputDir = File(System.getenv("CC_TEST_OUTPUT_DIR") ?: "build/cc-e2e-output")
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val originalSo = ZipFile(inputApk).use { zf ->
            zf.getInputStream(zf.getEntry("lib/arm64-v8a/libil2cpp.so")).use { it.readBytes() }
        }
        val allSites = adsSites + aimGuideSites + codeHashSites
        assertTrue(allSites.isNotEmpty())

        Patcher(PatcherConfig(inputApk, File("build/cc-e2e-temp"))).use { patcher ->
            patcher += allPatches.toSet()
            runBlocking {
                patcher().collect { result ->
                    result.exception?.let { e -> fail("Patch ${result.patch.name} failed: $e", e) }
                }
            }
            val result = patcher.get()

            // The patch result must carry every dex file of the input. The
            // patcher renumbers multi-dex files (its content-to-name pairing
            // is not preserved), so the byte-level check compares the sha1
            // multiset of the dex contents rather than per-name checksums;
            // it still rejects lost, duplicated or foreign dex content.
            val originalDexSha1s = zipDexSha1s(inputApk)
            assertEquals(
                originalDexSha1s.keys,
                result.dexFiles.map { it.name }.toSet(),
                "dex passthrough names",
            )
            assertEquals(
                originalDexSha1s.values.sorted(),
                result.dexFiles.map { dexFile -> sha1Hex(dexFile.stream.use { it.readBytes() }) }.sorted(),
                "dex passthrough contents",
            )

            val resourcesApk = result.resources.resourcesApk
                ?: fail("Expected compiled resources APK")
            resourcesApk.copyTo(outputDir.resolve("resources.apk"), overwrite = true)
        }

        val patchedSo = File("build/cc-e2e-temp").walkTopDown()
            .filter { it.isFile && it.path.endsWith("lib/arm64-v8a/libil2cpp.so") }
            .firstOrNull() ?: fail("patched libil2cpp.so not found")
        val patched = patchedSo.readBytes()
        assertEquals(originalSo.size, patched.size)

        val allowed = HashSet<Int>()
        for (site in allSites) {
            val originalMatch = Arm64Patcher.findMatches(originalSo, site.signature)
            assertEquals(1, originalMatch.size, "site ${site.name} signature in original")
            val win = originalMatch[0]
            (win + site.patchOffset until win + site.patchOffset + site.expectedBytes.size)
                .forEach { allowed.add(it) }

            // The patch window lies inside the signature, so the full
            // signature never re-occurs in the patched file. Sizes being
            // equal lets the original match anchor the patched location:
            // the replacement bytes must land there and the signature
            // context outside the patch window must survive.
            assertContentEquals(
                site.replacementBytes,
                patched.copyOfRange(win + site.patchOffset, win + site.patchOffset + site.replacementBytes.size),
            )
            assertContentEquals(
                site.signature.copyOfRange(0, site.patchOffset),
                patched.copyOfRange(win, win + site.patchOffset),
                "site ${site.name} signature context before patch window",
            )
            val contextStart = win + site.patchOffset + site.expectedBytes.size
            assertContentEquals(
                site.signature.copyOfRange(contextStart - win, site.signature.size),
                patched.copyOfRange(contextStart, win + site.signature.size),
                "site ${site.name} signature context after patch window",
            )
        }

        val diffs = originalSo.indices.filter { originalSo[it] != patched[it] }
        assertTrue(diffs.isNotEmpty(), "no bytes patched at all")
        val strays = diffs.filter { it !in allowed }
        assertTrue(
            strays.isEmpty(),
            "bytes changed outside declared sites: ${strays.size} examples ${strays.take(5)}",
        )
        for (site in allSites) {
            val m = Arm64Patcher.findMatches(originalSo, site.signature)[0] + site.patchOffset
            assertTrue(
                (m until m + site.expectedBytes.size).any { originalSo[it] != patched[it] },
                "site ${site.name} produced no change",
            )
        }

        val decodedDir = outputDir.resolve("decoded")
        val process = ProcessBuilder(
            "apktool", "d", "-f", "-s",
            "-o", decodedDir.absolutePath,
            outputDir.resolve("resources.apk").absolutePath,
        ).redirectErrorStream(true).start()
        check(process.waitFor() == 0) { "apktool failed:\n${process.inputStream.bufferedReader().readText()}" }

        assertRenamedManifest(decodedDir.resolve("AndroidManifest.xml"))
        assertRenamedAppName(decodedDir.resolve("res/values/strings.xml"), "Castle Bustërs")
    }

    private fun assertRenamedManifest(manifestFile: File) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        val root = document.documentElement as Element
        assertEquals("com.epicoro.castleclashers.mod", root.getAttribute("package"))

        val authorities = collectAttributes(document, "provider", "android:authorities")
        assertTrue(authorities.any { it.startsWith("com.epicoro.castleclashers.mod.") }, "renamed authorities")
        assertFalse(
            authorities.any { it.startsWith("com.epicoro.castleclashers.") && !it.startsWith("com.epicoro.castleclashers.mod.") },
            "un-renamed authorities: $authorities",
        )

        val permissionNames = collectAttributes(document, "permission", "android:name") +
            collectAttributes(document, "uses-permission", "android:name")
        assertTrue(
            permissionNames.any { it == "com.epicoro.castleclashers.mod.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" },
            "custom permission not renamed",
        )
        assertFalse(
            permissionNames.any { it.startsWith("com.epicoro.castleclashers.") && !it.startsWith("com.epicoro.castleclashers.mod.") },
            "un-renamed package permissions: $permissionNames",
        )
    }

    private fun assertRenamedAppName(stringsXmlFile: File, expected: String) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsXmlFile)
        val nodes = document.getElementsByTagName("string")
        val appNames = ArrayList<String>(nodes.length)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as Element
            if (node.getAttribute("name") == "app_name") appNames.add(node.textContent)
        }
        assertEquals(listOf(expected), appNames)
    }

    private fun collectAttributes(document: org.w3c.dom.Document, tag: String, attribute: String): List<String> {
        val nodes = document.getElementsByTagName(tag)
        return (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute(attribute) }
    }

    private fun zipDexSha1s(file: File): Map<String, String> = ZipFile(file).use { zf ->
        zf.entries().toList()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .associate { entry ->
                entry.name to sha1Hex(zf.getInputStream(entry).use { stream -> stream.readBytes() })
            }
    }

    private fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
}
