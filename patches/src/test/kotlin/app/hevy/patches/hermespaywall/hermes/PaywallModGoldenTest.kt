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

    @Test
    fun supportsHevy3_1_12() {
        val bundlePath = System.getenv("HEVY_TEST_BUNDLE_3_1_12")
        Assume.assumeTrue("HEVY_TEST_BUNDLE_3_1_12 not set", bundlePath != null)

        val data = File(bundlePath!!).readBytes()
        val applied = PaywallMod.apply(data)
        assertEquals(3, applied)

        // Offsets and bytes verified by disassembling the 3.1.12 bundle:
        // site 1: LoadConstFalse -> LoadConstTrue in the is_pro store
        // site 2: LoadParam+GetEnvironment (6-byte) prologue -> LoadConstTrue r0; Ret r0
        // site 3: terminal LoadConstFalse -> LoadConstTrue in the grace getter tail
        val expectedEdits = mapOf(
            0x11AD8DE to (0x79 to 0x78),
            0x11AD944 to (0x6C to 0x78),
            0x11AD946 to (0x00 to 0x5C),
            0x11AD947 to (0x29 to 0x00),
            0x11AD949 to (0x01 to 0x00),
            0x11ADB93 to (0x79 to 0x78),
        )
        val original = File(bundlePath).readBytes()
        for ((offset, change) in expectedEdits) {
            val (before, after) = change
            assertEquals(before.toInt(), original[offset].toInt() and 0xFF, "pre-patch byte at 0x${offset.toString(16)}")
            assertEquals(after, data[offset].toInt() and 0xFF, "post-patch byte at 0x${offset.toString(16)}")
        }
        // Site 2's Ret register operand (0x01) happened to match the original
        // GetEnvironment environment index, so that byte is intentionally
        // unchanged.
        assertEquals(0x01, data[0x11AD948].toInt() and 0xFF, "post-patch byte at 0x11ad948")

        val digest = MessageDigest.getInstance("SHA-1")
            .digest(data.copyOfRange(0, data.size - 20))
        assertContentEquals(digest, data.copyOfRange(data.size - 20, data.size))
    }
}
