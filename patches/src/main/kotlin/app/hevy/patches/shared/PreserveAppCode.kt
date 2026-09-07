package app.hevy.patches.shared

import app.morphe.patcher.patch.bytecodePatch

// Internal, unnamed bytecode patch: it is not shown in Morphe Manager or CLI
// patch lists, but the bundle needs at least one bytecode patch for the
// patcher to compile dex output. Without it the patcher runs in
// BytecodeMode.NONE and the patched APK ships without classes.dex, which
// fails to install with INSTALL_FAILED_INVALID_APK. This patch's dependency
// switches the patcher to its strip-fast mode, which passes all original dex
// files through unchanged.
val preserveAppCode = bytecodePatch {
    execute {
        // No-op: the presence of a bytecode patch is what preserves dex code.
    }
}
