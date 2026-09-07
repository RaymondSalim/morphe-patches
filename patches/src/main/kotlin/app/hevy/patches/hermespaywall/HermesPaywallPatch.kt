package app.hevy.patches.hermespaywall

import app.hevy.patches.hermespaywall.hermes.PaywallMod
import app.hevy.patches.shared.Constants
import app.hevy.patches.shared.preserveAppCode
import app.morphe.patcher.patch.rawResourcePatch

val hermesPaywallPatch = rawResourcePatch(
    name = "Unlock Pro Features",
    description = "Enable pro subscription features",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_HEVY_APKM, Constants.COMPATIBILITY_HEVY_APK)

    dependsOn(preserveAppCode)

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
