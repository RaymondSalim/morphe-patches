package app.hevy.patches.hermespaywall

import app.hevy.patches.hermespaywall.hermes.PaywallMod
import app.hevy.patches.shared.Constants
import app.morphe.patcher.patch.rawResourcePatch

val hermesPaywallPatch = rawResourcePatch(
    name = "Hermes paywall bypass",
    description = "Forces Hevy Pro features to be unlocked by patching the React Native Hermes bytecode bundle.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_HEVY_APKM, Constants.COMPATIBILITY_HEVY_APK)

    execute {
        val bundleFile = get("assets/index.android.bundle")
        if (!bundleFile.exists()) {
            throw IllegalStateException("assets/index.android.bundle not found in the APK")
        }

        val data = bundleFile.readBytes()
        val applied = PaywallMod.apply(data)
        bundleFile.writeBytes(data)

        println("Hermes paywall bypass: applied $applied edits to assets/index.android.bundle")
    }
}
