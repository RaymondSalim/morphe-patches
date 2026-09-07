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

    /** Applies all 3 edits in place and recomputes the bundle SHA-1 trailer. */
    fun apply(data: ByteArray): Int {
        val bundle = HermesBundle.parse(data)
        var applied = 0
        applied += patchProStateReset(bundle, data)
        applied += patchProStatusGetter(bundle, data)
        applied += patchGracePeriodGetter(bundle, data)
        check(applied == 3) { "Expected to apply 3 paywall edits, applied $applied" }
        bundle.updateSha1Trailer()
        return applied
    }

    /**
     * Site 1: inside the functions referencing the Hevy Pro storage keys, find
     * the single `LoadConstFalse` immediately followed by `PutNewOwnById` with
     * the `is_pro` string, and flip it to LoadConstTrue.
     */
    private fun patchProStateReset(bundle: HermesBundle, data: ByteArray): Int {
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
            "Expected exactly one is_pro store site, found ${hits.size} in $candidates"
        }
        val position = hits[0]
        check((data[position].toInt() and 0xFF) == HermesOpcodes.LOAD_CONST_FALSE) {
            "Unexpected byte at is_pro store site 0x${position.toString(16)}"
        }
        data[position] = HermesOpcodes.LOAD_CONST_TRUE.toByte()
        return 1
    }

    /**
     * Site 2: rewrite the isPro getter prologue `LoadThisNS r0;
     * GetEnvironment rX, n` to `LoadConstTrue r0; Ret r0`.
     */
    private fun patchProStatusGetter(bundle: HermesBundle, data: ByteArray): Int {
        val matches = bundle.findFunctions(PRO_STATUS_GETTER_STRINGS)
        require(matches.size == 1) {
            "Expected exactly one isPro status getter, found ${matches.size}"
        }
        val getter = matches[0]
        check(getter.size >= 5) { "isPro getter too small: ${getter.size} bytes" }
        val base = getter.offset
        check((data[base].toInt() and 0xFF) == HermesOpcodes.LOAD_THIS_NS && data[base + 1].toInt() == 0) {
            "Unexpected isPro getter prologue at 0x${base.toString(16)}"
        }
        check((data[base + 2].toInt() and 0xFF) == HermesOpcodes.GET_ENVIRONMENT) {
            "Unexpected isPro getter second instruction at 0x${base.toString(16)}"
        }
        data[base] = HermesOpcodes.LOAD_CONST_TRUE.toByte()
        data[base + 1] = 0
        data[base + 2] = HermesOpcodes.RET.toByte()
        data[base + 3] = 0
        data[base + 4] = 1
        return 1
    }

    /**
     * Site 3: flip the terminal LoadConstFalse in the grace-period getter's
     * trailing return sequence `JmpTrue; LoadConstFalse rX; Ret rX;
     * LoadConstTrue rX; Ret rX` (11 bytes) to LoadConstTrue.
     */
    private fun patchGracePeriodGetter(bundle: HermesBundle, data: ByteArray): Int {
        val matches = bundle.findFunctions(GRACE_PERIOD_GETTER_STRINGS)
        require(matches.size == 1) {
            "Expected exactly one grace period getter, found ${matches.size}"
        }
        val getter = matches[0]
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
