package app.epicoro.castleclashers.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_CASTLE_APKM = Compatibility(
        name = "Castle Busters",
        packageName = "com.epicoro.castleclashers",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x15516C, // Dark teal background of the Castle Busters launcher icon.
        targets = listOf(AppTarget(version = "1.17.2")),
    )

    val COMPATIBILITY_CASTLE_APK = Compatibility(
        name = "Castle Busters",
        packageName = "com.epicoro.castleclashers",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x15516C,
        targets = listOf(AppTarget(version = "1.17.2")),
    )
}
