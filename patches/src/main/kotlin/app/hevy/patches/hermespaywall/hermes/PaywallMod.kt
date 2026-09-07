package app.hevy.patches.hermespaywall.hermes

/**
 * Applies the Hevy Pro unlock edits to a Hermes bytecode bundle:
 *
 * 1. The pro-state reset generator (references HEVY_PRO_* storage keys) stores
 *    `subscription.is_pro = false`; flip its LoadConstFalse to LoadConstTrue.
 * 2. The isPro status getter (references getProStatusOverride / force-pro /
 *    force-free) gets its prologue rewritten to `return true`.
 * 3. The offline grace-period getter (references lastSuccessfulFetch /
 *    lastFetchAt / expiresAt) has its terminal LoadConstFalse flipped to
 *    LoadConstTrue.
 *
 * Each site is located by string references and validated against expected
 * bytes before editing. If anything does not match, this object throws so the
 * patch fails loudly instead of producing a broken app.
 */
object PaywallMod {

    private val PRO_STATE_RESET_STRINGS = setOf(
        "HEVY_PRO_DISK_STORAGE_KEY",
        "HEVY_PRO_LAST_SUCCESSFUL_FETCH",
    )
    private val PRO_STATUS_GETTER_STRINGS = setOf(
        "getProStatusOverride",
        "force-pro",
        "force-free",
    )
    private val GRACE_PERIOD_GETTER_STRINGS = setOf(
        "lastSuccessfulFetch",
        "lastFetchAt",
        "expiresAt",
    )
    private const val IS_PRO_STRING = "is_pro"

    /**
     * Applies all 3 edits in place and recomputes the bundle SHA-1 trailer.
     *
     * All three sites are located from a single scan of the pristine bundle,
     * BEFORE any byte is edited: the edits rewrite function prologues into
     * `LoadConstTrue; Ret` with dead trailing bytes, and re-scanning a
     * mutated bundle would parse those dead bytes as instructions and throw.
     */
    fun apply(data: ByteArray): Int {
        val bundle = HermesBundle.parse(data)
        val resetSite = locateProStateReset(bundle, data)
        val proStatusGetter = locateUniqueFunction(bundle, PRO_STATUS_GETTER_STRINGS, "isPro status getter")
        val graceGetter = locateUniqueFunction(bundle, GRACE_PERIOD_GETTER_STRINGS, "grace period getter")

        var applied = 0
        applied += patchProStateReset(bundle, data, resetSite)
        applied += patchProStatusGetter(data, proStatusGetter)
        applied += patchGracePeriodGetter(data, graceGetter)
        check(applied == 3) { "Expected to apply 3 paywall edits, applied $applied" }
        bundle.updateSha1Trailer()
        return applied
    }

    private fun locateUniqueFunction(
        bundle: HermesBundle,
        referencingAllOf: Set<String>,
        name: String,
    ): HermesBundle.Function {
        val matches = bundle.findFunctions(referencingAllOf)
        require(matches.size == 1) { "Expected exactly one $name, found ${matches.size}" }
        return matches[0]
    }

    /**
     * Site 1: inside the functions referencing the Hevy Pro storage keys, find
     * the single `LoadConstFalse` immediately followed by `PutNewOwnById` with
     * the `is_pro` string.
     */
    private fun locateProStateReset(bundle: HermesBundle, data: ByteArray): Int {
        val candidates = bundle.findFunctions(PRO_STATE_RESET_STRINGS)
        require(candidates.isNotEmpty()) { "Pro-state reset function not found" }

        val hits = mutableListOf<Int>()
        for (candidate in candidates) {
            var ip = 0
            while (ip < candidate.size) {
                val absolute = candidate.offset + ip
                if ((data[absolute].toInt() and 0xFF) == HermesOpcodes.LOAD_CONST_FALSE) {
                    val nextOpcode = data[absolute + 2].toInt() and 0xFF
                    if (nextOpcode == HermesOpcodes.PUT_NEW_OWN_BY_ID) {
                        // PutNewOwnById: opcode(1) + reg(1) + reg(1) + u16 string id at +3.
                        val stringId = readU16(data, absolute + 2 + 3)
                        if (bundle.string(stringId) == IS_PRO_STRING) {
                            hits.add(absolute)
                        }
                    }
                }
                ip += HermesOpcodes.TABLE[data[absolute].toInt() and 0xFF].size
            }
        }
        require(hits.size == 1) {
            "Expected exactly one is_pro store site, found ${hits.size} in " +
                candidates.joinToString(prefix = "[", postfix = "]") {
                    "id=0x${it.id.toString(16)} offset=0x${it.offset.toString(16)} size=${it.size}"
                }
        }
        return hits[0]
    }

    private fun patchProStateReset(bundle: HermesBundle, data: ByteArray, position: Int): Int {
        check((data[position].toInt() and 0xFF) == HermesOpcodes.LOAD_CONST_FALSE) {
            "Unexpected byte at is_pro store site 0x${position.toString(16)}"
        }
        data[position] = HermesOpcodes.LOAD_CONST_TRUE.toByte()
        return 1
    }

    /**
     * Site 2: rewrite the isPro getter prologue to `LoadConstTrue r0;
     * Ret r0` so the getter returns true unconditionally. The first two
     * instructions vary across app versions (e.g. `LoadThisNS r0;
     * GetEnvironment` on 3.0.11, `LoadParam r0, 0; GetEnvironment` on
     * 3.1.12); the stable invariant is `GetEnvironment` as the second
     * instruction. The whole prologue span is overwritten, with any span
     * beyond the 5-byte sequence zero-filled (unreachable after the Ret).
     */
    private fun patchProStatusGetter(data: ByteArray, getter: HermesBundle.Function): Int {
        check(getter.size >= 5) { "isPro getter too small: ${getter.size} bytes" }
        val base = getter.offset

        val firstSize = instructionSizeAt(data, base)
        val secondSize = instructionSizeAt(data, base + firstSize)
        val prologueSpan = firstSize + secondSize
        check((data[base + firstSize].toInt() and 0xFF) == HermesOpcodes.GET_ENVIRONMENT) {
            "Unexpected isPro getter second instruction 0x" +
                (data[base + firstSize].toInt() and 0xFF).toString(16) +
                " at 0x${(base + firstSize).toString(16)}"
        }
        check(prologueSpan in 5..12) { "Unexpected isPro getter prologue span $prologueSpan at 0x${base.toString(16)}" }

        val rewrite = byteArrayOf(
            HermesOpcodes.LOAD_CONST_TRUE.toByte(),
            0,
            HermesOpcodes.RET.toByte(),
            0,
            1,
        )
        for (offset in 0 until prologueSpan) {
            data[base + offset] = if (offset < rewrite.size) rewrite[offset] else 0
        }
        return 1
    }

    private fun instructionSizeAt(data: ByteArray, offset: Int): Int {
        val opcode = data[offset].toInt() and 0xFF
        check(opcode < HermesOpcodes.TABLE.size) {
            "Unknown opcode 0x${opcode.toString(16)} at 0x${offset.toString(16)}"
        }
        return HermesOpcodes.TABLE[opcode].size
    }

    /**
     * Site 3: flip the terminal LoadConstFalse in the grace-period getter's
     * trailing return sequence `JmpTrue; LoadConstFalse rX; Ret rX;
     * LoadConstTrue rX; Ret rX` (11 bytes) to LoadConstTrue.
     */
    private fun patchGracePeriodGetter(data: ByteArray, getter: HermesBundle.Function): Int {
        val tail = getter.offset + getter.size - 11
        check(tail >= getter.offset) { "Grace period getter too small: ${getter.size} bytes" }
        check(
            (data[tail].toInt() and 0xFF) == HermesOpcodes.JMP_TRUE &&
                (data[tail + 3].toInt() and 0xFF) == HermesOpcodes.LOAD_CONST_FALSE &&
                (data[tail + 5].toInt() and 0xFF) == HermesOpcodes.RET &&
                (data[tail + 7].toInt() and 0xFF) == HermesOpcodes.LOAD_CONST_TRUE &&
                (data[tail + 9].toInt() and 0xFF) == HermesOpcodes.RET
        ) { "Unexpected grace period getter tail at 0x${tail.toString(16)}" }
        data[tail + 3] = HermesOpcodes.LOAD_CONST_TRUE.toByte()
        return 1
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
}
