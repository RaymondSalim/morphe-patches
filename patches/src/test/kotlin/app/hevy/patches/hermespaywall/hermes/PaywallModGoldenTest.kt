package app.hevy.patches.hermespaywall.hermes

import org.junit.Assume
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PaywallModGoldenTest {

    @Test
    fun reproducesTheKnownGoodModdedBundle() {
        val originalPath = System.getenv("HEVY_TEST_ORIGINAL_BUNDLE")
        val moddedPath = System.getenv("HEVY_TEST_MODDED_BUNDLE")
        Assume.assumeTrue("bundle env vars not set", originalPath != null && moddedPath != null)

        val original = File(originalPath!!).readBytes()
        val modded = File(moddedPath!!).readBytes()

        val data = original.copyOf()
        val applied = PaywallMod.apply(data)
        assertEquals(3, applied)
        assertEquals(modded.size, data.size)

        // Every byte except the 20-byte SHA-1 trailer must match the known-good
        // manual mod exactly.
        for (i in 0 until data.size - 20) {
            if (data[i] != modded[i]) {
                throw AssertionError("Byte mismatch at offset 0x${i.toString(16)}")
            }
        }

        // Our trailer is the correct SHA-1 of the patched content. The manual
        // mod left the trailer stale, so it must NOT match the modded file.
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(data.copyOfRange(0, data.size - 20))
        assertContentEquals(digest, data.copyOfRange(data.size - 20, data.size))
    }

    @Test
    fun patchesAreIdempotentSafe() {
        // A second run must fail loudly instead of double-patching.
        val originalPath = System.getenv("HEVY_TEST_ORIGINAL_BUNDLE")
        Assume.assumeTrue("HEVY_TEST_ORIGINAL_BUNDLE not set", originalPath != null)
        val data = File(originalPath!!).readBytes()
        PaywallMod.apply(data)
        try {
            PaywallMod.apply(data)
            throw AssertionError("Second apply should have failed")
        } catch (expected: IllegalStateException) {
            // expected
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
