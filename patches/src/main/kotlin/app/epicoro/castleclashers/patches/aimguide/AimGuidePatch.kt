package app.epicoro.castleclashers.patches.aimguide

import app.epicoro.castleclashers.patches.native.Arm64Patcher
import app.epicoro.castleclashers.patches.native.aimGuideSites
import app.epicoro.castleclashers.patches.shared.Constants
import app.epicoro.castleclashers.patches.shared.codeHashPatch
import app.hevy.patches.shared.preserveAppCode
import app.morphe.patcher.patch.rawResourcePatch

val aimGuidePatch = rawResourcePatch(
    name = "Extend aim guide",
    description = "Shows the full projectile trajectory while aiming instead of the short preview. Obstacles no longer cut the guide short.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_CASTLE_APKM, Constants.COMPATIBILITY_CASTLE_APK)

    dependsOn(preserveAppCode, codeHashPatch)

    execute {
        for (abi in listOf("armeabi-v7a", "x86", "x86_64")) {
            val otherAbiLib = get("lib/$abi/libil2cpp.so")
            check(!otherAbiLib.exists()) { "Unsupported ABI $abi present; arm64-v8a only" }
        }

        val so = get("lib/arm64-v8a/libil2cpp.so")
        check(so.exists()) { "lib/arm64-v8a/libil2cpp.so not found in the APK" }

        val logs = Arm64Patcher.applySites(so, aimGuideSites)
        logs.forEach(::println)
    }
}
