package app.epicoro.castleclashers.patches.shared

import app.epicoro.castleclashers.patches.native.Arm64Patcher
import app.epicoro.castleclashers.patches.native.codeHashSites
import app.morphe.patcher.patch.rawResourcePatch

// Internal, unnamed patch: it is not shown in Morphe Manager or CLI patch
// lists and cannot be selected on its own. It is the single application
// point for codeHashSites: the native patches declare it as a dependency
// instead of applying the list themselves, so a future row is patched
// exactly once even when several native patches are selected together
// (applying the same site twice would fail on the second signature match).
val codeHashPatch = rawResourcePatch {
    compatibleWith(Constants.COMPATIBILITY_CASTLE_APKM, Constants.COMPATIBILITY_CASTLE_APK)

    execute {
        // ACTk Genuine CodeHash is unreachable in the current target
        // (evidence in Sites.kt); an empty list leaves the library untouched
        // instead of paying a 160 MB read-modify-write cycle for zero bytes.
        if (codeHashSites.isEmpty()) return@execute

        val so = get("lib/arm64-v8a/libil2cpp.so")
        check(so.exists()) { "lib/arm64-v8a/libil2cpp.so not found in the APK" }

        Arm64Patcher.applySites(so, codeHashSites).forEach(::println)
    }
}
