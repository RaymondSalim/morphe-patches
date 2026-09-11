package app.epicoro.castleclashers.patches.native

/**
 * One verified byte-patch site inside a native library.
 *
 * [signature] is a byte window that must match exactly once in the whole
 * file; it must be unique enough to survive unrelated code changes and
 * include the patch site. [patchOffset] is where the replacement begins,
 * relative to the signature start. [expectedBytes] are asserted against
 * the file before writing [replacementBytes]; both must have equal length.
 */
data class NativeSite(
    val name: String,
    val description: String,
    val signature: ByteArray,
    val patchOffset: Int,
    val expectedBytes: ByteArray,
    val replacementBytes: ByteArray,
)

/** Parses a space-separated hex string like "1F 20 03 D5" into bytes. */
fun hex(pattern: String): ByteArray =
    pattern.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
