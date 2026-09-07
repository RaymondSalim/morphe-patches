package app.hevy.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_HEVY_APKM = Compatibility(
        name = "Hevy - Gym Log Workout Tracker",
        packageName = "com.hevy",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x1C1C1E, // Dark background of the Hevy launcher icon.
        targets = listOf(
            AppTarget(version = "3.1.12"),
            AppTarget(version = "3.0.11")
        )
    )

    val COMPATIBILITY_HEVY_APK = Compatibility(
        name = "Hevy - Gym Log Workout Tracker",
        packageName = "com.hevy",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x1C1C1E,
        targets = listOf(
            AppTarget(version = "3.1.12"),
            AppTarget(version = "3.0.11")
        )
    )
}
