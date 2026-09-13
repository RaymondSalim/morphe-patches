package app.epicoro.castleclashers.patches.adblock

import app.epicoro.castleclashers.patches.native.Arm64Patcher
import app.epicoro.castleclashers.patches.native.adsSites
import app.epicoro.castleclashers.patches.native.codeHashSites
import app.epicoro.castleclashers.patches.shared.Constants
import app.hevy.patches.shared.preserveAppCode
import app.morphe.patcher.patch.rawResourcePatch

val adBlockPatch = rawResourcePatch(
    name = "Block ads (banner, interstitial & app open)",
    description = "Prevents banner, interstitial and app-open ads from loading and displaying. Rewarded ads still work and still grant rewards.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_CASTLE_APKM, Constants.COMPATIBILITY_CASTLE_APK)

    dependsOn(preserveAppCode)

    execute {
        // The RE-derived sites only exist in the arm64-v8a library; a
        // multi-ABI input would ship unpatched 32-bit code.
        for (abi in listOf("armeabi-v7a", "x86", "x86_64")) {
            val otherAbiLib = get("lib/$abi/libil2cpp.so")
            check(!otherAbiLib.exists()) { "Unsupported ABI $abi present; arm64-v8a only" }
        }

        val so = get("lib/arm64-v8a/libil2cpp.so")
        check(so.exists()) { "lib/arm64-v8a/libil2cpp.so not found in the APK" }

        val logs = Arm64Patcher.applySites(so, adsSites + codeHashSites)
        logs.forEach(::println)
    }
}
