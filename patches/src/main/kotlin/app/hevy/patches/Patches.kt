package app.hevy.patches

import app.hevy.patches.hermespaywall.hermesPaywallPatch
import app.hevy.patches.packagerename.packageRenamePatch

val allPatches = listOf(
    packageRenamePatch,
    hermesPaywallPatch,
)
