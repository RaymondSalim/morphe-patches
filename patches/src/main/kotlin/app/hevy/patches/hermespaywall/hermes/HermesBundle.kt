package app.hevy.patches.hermespaywall.hermes

import java.security.MessageDigest

/**
 * Minimal parser for Hermes bytecode files (bytecode version 96).
 *
 * Parses only what is needed to locate functions by the string constants they
 * reference: the file header, the function header table, and the string
 * tables. The bundle is parsed against a mutable byte array so callers can
 * patch bytes in place and recompute the trailing SHA-1.
 */
class HermesBundle private constructor(
    private val data: ByteArray,
) {
    val version: Int

    val functions: List<Function>

    class Function internal constructor(
        val id: Int,
        val offset: Int,
        val size: Int,
    )

    private val stringCount: Int
    private val stringUtf16: BooleanArray
    private val stringOffsets: IntArray
    private val stringLengths: IntArray
    private val overflowOffsets: IntArray
    private val overflowLengths: IntArray
    private val stringStorageBase: Int
    private val stringCache = HashMap<Int, String>()
    private var reverseIndex: Map<String, Int>? = null

    init {
        require(data.size >= 128) { "Hermes bundle too small: ${data.size} bytes" }
        // The 8-byte Hermes magic 0xC61FBC03C103191F appears verbatim in the
        // file, so little-endian u32 reads of it yield the byte-swapped halves.
        require(readU32(0) == 0x03BC1FC6 && readU32(4) == 0x1F1903C1) {
            "Invalid Hermes magic"
        }
        version = readU32(8)
        require(version == 96) { "Unsupported Hermes bytecode version: $version (expected 96)" }

        val functionCount = readU32(32 + 4 * 2)
        val stringKindCount = readU32(32 + 4 * 3)
        val identifierCount = readU32(32 + 4 * 4)
        stringCount = readU32(32 + 4 * 5)
        val overflowStringCount = readU32(32 + 4 * 6)
        val stringStorageSize = readU32(32 + 4 * 7)

        // Function headers: contiguous 16-byte slots directly after the padded header.
        val functionsStart = align(32 + 4 * 19 + 1, 32)
        val functionHeadersEnd = functionsStart + functionCount * SMALL_HEADER_SIZE
        require(functionHeadersEnd <= data.size) { "Function header table exceeds file length" }

        val functionList = ArrayList<Function>(functionCount)
        for (i in 0 until functionCount) {
            val headerOffset = functionsStart + i * SMALL_HEADER_SIZE
            val word0 = readU32(headerOffset)
            val word1 = readU32(headerOffset + 4)
            val word2 = readU32(headerOffset + 8)
            val flags = data[headerOffset + 15].toInt() and 0xFF
            var offset = word0 and 0x1FFFFFF
            var size = word1 and 0x7FFF
            if (flags and FLAG_OVERFLOWED != 0) {
                // The large header lives at (infoOffset << 16) | offset.
                val infoOffset = word2 and 0x1FFFFFF
                val largeOffset = (infoOffset.toLong() shl 16) or offset.toLong()
                require(largeOffset + 12 <= data.size) {
                    "Overflowed function header for function $i out of range"
                }
                offset = readU32(largeOffset.toInt())
                size = readU32(largeOffset.toInt() + 8)
            }
            require(offset >= 0 && size >= 0 && offset + size <= data.size) {
                "Function $i body out of range: offset=$offset size=$size"
            }
            functionList.add(Function(i, offset, size))
        }
        functions = functionList

        // String kind entries, identifier hashes, string tables, string storage.
        var cursor = align(functionHeadersEnd, 4)
        cursor = align(cursor + stringKindCount * 4, 4)
        cursor = align(cursor + identifierCount * 4, 4)
        require(cursor + stringCount * 4 <= data.size) { "String table exceeds file length" }
        stringUtf16 = BooleanArray(stringCount)
        stringOffsets = IntArray(stringCount)
        stringLengths = IntArray(stringCount)
        for (i in 0 until stringCount) {
            val entry = readU32(cursor + i * 4)
            stringUtf16[i] = entry and 1 != 0
            stringOffsets[i] = (entry shr 1) and 0x7FFFFF
            stringLengths[i] = (entry shr 24) and 0xFF
        }
        cursor = align(cursor + stringCount * 4, 4)
        overflowOffsets = IntArray(overflowStringCount)
        overflowLengths = IntArray(overflowStringCount)
        for (i in 0 until overflowStringCount) {
            overflowOffsets[i] = readU32(cursor + i * 8)
            overflowLengths[i] = readU32(cursor + i * 8 + 4)
        }
        cursor = align(cursor + overflowStringCount * 8, 4)
        require(cursor + stringStorageSize <= data.size) { "String storage exceeds file length" }
        stringStorageBase = cursor
    }

    /** Decode the string with the given id. */
    fun string(id: Int): String {
        stringCache[id]?.let { return it }
        require(id in 0 until stringCount) { "String id out of range: $id" }
        var offset = stringOffsets[id]
        var length = stringLengths[id]
        if (length == 0xFF) {
            // The small entry's offset field is an index into the overflow
            // table, whose entry holds the real storage offset and length.
            val overflowIndex = offset
            offset = overflowOffsets[overflowIndex]
            length = overflowLengths[overflowIndex]
        }
        val raw = data.copyOfRange(stringStorageBase + offset, stringStorageBase + offset + length)
        val decoded = if (stringUtf16[id]) {
            String(raw, Charsets.UTF_16LE)
        } else {
            String(raw, Charsets.ISO_8859_1)
        }
        stringCache[id] = decoded
        return decoded
    }

    /** Look up the first string id whose value equals [value]. */
    fun stringId(value: String): Int {
        reverseIndex?.get(value)?.let { return it }
        val index = HashMap<String, Int>()
        for (i in 0 until stringCount) {
            index.putIfAbsent(string(i), i)
        }
        reverseIndex = index
        return index[value] ?: throw IllegalArgumentException("String not in bundle: \"$value\"")
    }

    /** All string values referenced by [function]'s instruction stream. */
    fun referencedStrings(function: Function): Set<String> {
        val referenced = mutableSetOf<String>()
        var ip = 0
        while (ip < function.size) {
            val absolute = function.offset + ip
            val opcodeValue = data[absolute].toInt() and 0xFF
            val opcode = TABLE_INDEX[opcodeValue]
                ?: throw IllegalStateException(
                    "Unknown Hermes opcode 0x${opcodeValue.toString(16)} in function ${function.id}"
                )
            for (operand in opcode.stringOperands) {
                val operandAbsolute = absolute + operand.offset
                // Read as Long so high-bit u16/u32 ids never wrap negative.
                val stringIdValue = when (operand.width) {
                    1 -> (data[operandAbsolute].toLong() and 0xFF)
                    2 -> (data[operandAbsolute].toLong() and 0xFF) or
                        ((data[operandAbsolute + 1].toLong() and 0xFF) shl 8)
                    4 -> (data[operandAbsolute].toLong() and 0xFF) or
                        ((data[operandAbsolute + 1].toLong() and 0xFF) shl 8) or
                        ((data[operandAbsolute + 2].toLong() and 0xFF) shl 16) or
                        ((data[operandAbsolute + 3].toLong() and 0xFF) shl 24)
                    else -> throw IllegalStateException("Unsupported string operand width")
                }
                require(stringIdValue < stringCount) {
                    "String id out of range in function ${function.id}: $stringIdValue"
                }
                referenced.add(string(stringIdValue.toInt()))
            }
            ip += opcode.size
        }
        require(ip == function.size) {
            "Instruction stream overran function ${function.id} body"
        }
        return referenced
    }

    /** Functions whose referenced strings contain every value in [referencingAllOf]. */
    fun findFunctions(referencingAllOf: Set<String>): List<Function> =
        functions.filter { referencedStrings(it).containsAll(referencingAllOf) }

    /**
     * Recompute the SHA-1 trailer (last 20 bytes) over the rest of the bundle.
     * Bytecode edits invalidate the original trailer.
     */
    fun updateSha1Trailer() {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(data.copyOfRange(0, data.size - 20))
        System.arraycopy(digest, 0, data, data.size - 20, 20)
    }

    private fun readU32(offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

    private fun align(value: Int, alignment: Int): Int =
        (value + alignment - 1) / alignment * alignment

    companion object {
        private const val SMALL_HEADER_SIZE = 16
        private const val FLAG_OVERFLOWED = 0x20

        private val TABLE_INDEX = HermesOpcodes.TABLE

        fun parse(data: ByteArray): HermesBundle = HermesBundle(data)
    }
}
