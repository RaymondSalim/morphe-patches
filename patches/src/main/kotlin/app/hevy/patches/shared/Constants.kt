package app.hevy.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_HEVY = Compatibility(
        name = "Hevy - Gym Log Workout Tracker",
        packageName = "com.hevy",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF6C63, // Hevy app icon color (orange/red)
        targets = listOf(
            AppTarget(
                version = "3.0.11"
            )
            // Add more versions as tested
        )
    )
}