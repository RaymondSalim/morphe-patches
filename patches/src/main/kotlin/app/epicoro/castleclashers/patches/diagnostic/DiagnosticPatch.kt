package app.epicoro.castleclashers.patches.diagnostic

import app.epicoro.castleclashers.patches.native.Arm64Patcher
import app.epicoro.castleclashers.patches.shared.Constants
import app.hevy.patches.shared.preserveAppCode
import app.morphe.patcher.patch.rawResourcePatch

private val diagnosticSites = listOf(
    app.epicoro.castleclashers.patches.native.NativeSite(
        name = "diag.updateTrajectory.ret",
        description = "DIAGNOSTIC, revert after the experiment: makes " +
            "ProjectileController.UpdateTrajectory return immediately, which removes whatever " +
            "this function draws (unit-upgrade preview guide and/or battle guide). If a guide " +
            "survives this patch, that guide is drawn elsewhere.",
        signature = hex(
            "FF C3 05 D1 EF 3B 0D 6D ED 33 0E 6D EB 2B 0F 6D " +
                "E9 23 10 6D FD 7B 11 A9 FC 6F 12 A9 FA 67 13 A9 " +
                "F8 5F 14 A9 F6 57 15 A9 F4 4F 16 A9 E8 0F 17 A9",
        ),
        patchOffset = 0,
        expectedBytes = hex("FF C3 05 D1"),
        replacementBytes = hex("C0 03 5F D6"),
    ),
    app.epicoro.castleclashers.patches.native.NativeSite(
        name = "diag.setTrajectoryDots.ret",
        description = "DIAGNOSTIC, revert after the experiment: makes " +
            "EnemyAimController.SetTrajectoryDots return immediately, removing the aim dots it " +
            "places (if any are visible during aiming). If dots survive, they come from elsewhere.",
        signature = hex(
            "ED 33 B9 6D EB 2B 01 6D E9 23 02 6D FE 67 03 A9 " +
                "F8 5F 04 A9 F6 57 05 A9 F4 4F 06 A9 E8 40 20 1E",
        ),
        patchOffset = 0,
        expectedBytes = hex("ED 33 B9 6D"),
        replacementBytes = hex("C0 03 5F D6"),
    ),
)

val diagnosticPatch = rawResourcePatch(
    name = "[DIAG] Disable guide renderers (temporary)",
    description = "Temporary diagnostic: makes ProjectileController.UpdateTrajectory and EnemyAimController.SetTrajectoryDots return immediately. Use to identify which function draws the visible aim guide; revert after the experiment.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_CASTLE_APKM, Constants.COMPATIBILITY_CASTLE_APK)

    dependsOn(preserveAppCode)

    execute {
        for (abi in listOf("armeabi-v7a", "x86", "x86_64")) {
            val otherAbiLib = get("lib/$abi/libil2cpp.so")
            check(!otherAbiLib.exists()) { "Unsupported ABI $abi present; arm64-v8a only" }
        }

        val so = get("lib/arm64-v8a/libil2cpp.so")
        check(so.exists()) { "lib/arm64-v8a/libil2cpp.so not found in the APK" }

        val logs = Arm64Patcher.applySites(so, diagnosticSites)
        logs.forEach(::println)
    }
}
