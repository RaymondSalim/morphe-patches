package app.epicoro.castleclashers.native

import app.epicoro.castleclashers.patches.native.Arm64Patcher
import app.epicoro.castleclashers.patches.native.NativeSite
import app.epicoro.castleclashers.patches.native.hex
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Arm64PatcherTest {

    // Default site: the patch window (expected bytes) sits inside the
    // signature window, which is how RE-derived sites are authored.
    private fun site(
        signature: ByteArray = hex("AA BB CC DD 10 20 30 40 99 88 77 66"),
        patchOffset: Int = 4,
        expected: ByteArray = hex("10 20 30 40"),
        replacement: ByteArray = hex("1F 20 03 D5"),
    ) = NativeSite("test site", "synthetic", signature, patchOffset, expected, replacement)

    private fun buffer(): ByteArray = ByteArray(64) { it.toByte() }

    // Plants the signature, which already contains the expected bytes at
    // patchOffset, exactly like a real patched binary would sit.
    private fun build(offset: Int, s: NativeSite): ByteArray {
        val data = buffer()
        s.signature.copyInto(data, offset)
        return data
    }

    @Test
    fun patchesTheSingleMatch() {
        val s = site()
        val file = File.createTempFile("arm64", ".bin")
        try {
            build(16, s).also { file.writeBytes(it) }
            val logs = Arm64Patcher.applySites(file, listOf(s))
            assertEquals(1, logs.size)
            val data = file.readBytes()
            val start = 16 + s.patchOffset
            assertTrue(s.replacementBytes.contentEquals(data.copyOfRange(start, start + 4)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun throwsOnZeroMatches() {
        val file = File.createTempFile("arm64", ".bin")
        try {
            file.writeBytes(buffer())
            assertFailsWith<IllegalStateException> { Arm64Patcher.applySites(file, listOf(site())) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun throwsOnMultipleMatches() {
        val s = site()
        val file = File.createTempFile("arm64", ".bin")
        try {
            build(8, s).also { data -> s.signature.copyInto(data, 32); file.writeBytes(data) }
            assertFailsWith<IllegalStateException> { Arm64Patcher.applySites(file, listOf(s)) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun throwsWhenExpectedBytesDoNotMatch() {
        // Window outside the signature: corrupting it must not disturb the
        // signature match, isolating the expected-bytes assertion.
        val s = NativeSite(
            "test site", "synthetic",
            signature = hex("AA BB CC DD EE FF 11 22"),
            patchOffset = 8,
            expectedBytes = hex("10 20 30 40"),
            replacementBytes = hex("1F 20 03 D5"),
        )
        val data = build(16, s)
        hex("10 20 30 40").copyInto(data, 16 + s.patchOffset)
        data[16 + s.patchOffset + 1] = 0x7F // corrupt one expected byte
        val file = File.createTempFile("arm64", ".bin")
        try {
            file.writeBytes(data)
            assertFailsWith<IllegalStateException> { Arm64Patcher.applySites(file, listOf(s)) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun throwsWhenSiteExtendsPastBuffer() {
        val s = NativeSite(
            "test site", "synthetic",
            signature = hex("AA BB CC DD EE FF 11 22"),
            patchOffset = 8,
            expectedBytes = hex("10 20 30 40"),
            replacementBytes = hex("1F 20 03 D5"),
        )
        val data = buffer()
        s.signature.copyInto(data, 56) // signature occupies 56..64; window would be 64..68
        val file = File.createTempFile("arm64", ".bin")
        try {
            file.writeBytes(data)
            assertFailsWith<IllegalStateException> { Arm64Patcher.applySites(file, listOf(s)) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun findMatchesFindsOverlappingCandidatesCorrectly() {
        val data = ByteArray(16)
        hex("AA AA AA").copyInto(data, 0)
        hex("AA AA AA").copyInto(data, 1)
        assertEquals(2, Arm64Patcher.findMatches(data, hex("AA AA AA")).size)
        assertEquals(0, Arm64Patcher.findMatches(data, hex("BB BB")).size)
    }
}
