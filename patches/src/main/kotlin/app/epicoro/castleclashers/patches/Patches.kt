package app.epicoro.castleclashers.patches

import app.epicoro.castleclashers.patches.aimguide.aimGuidePatch
import app.epicoro.castleclashers.patches.adblock.adBlockPatch
import app.epicoro.castleclashers.patches.packagerename.packageRenamePatch

val allPatches = listOf(
    packageRenamePatch,
    adBlockPatch,
    aimGuidePatch,
)
