# Castle Clashers Patches Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Castle Busters (EpiCoro/Voodoo, `com.epicoro.castleclashers` 1.17.2) as a second app in the Morphe bundle with three patches: package rename, ads block (rewarded ads untouched), and full-trajectory aim guide (native IL2CPP patches).

**Architecture:** Two raw `rawResourcePatch` patches byte-patch `lib/arm64-v8a/libil2cpp.so` at signature-verified ARM64 sites discovered by Il2CppDumper + Ghidra. A ported `resourcePatch` renames the package. All patches fail loud on signature mismatch. Repo restructured into a two-app bundle.

**Tech Stack:** Kotlin (morphe-patcher 1.8.0, gradle plugin 1.3.3), Il2CppDumper (.NET), Ghidra (ARM64 disassembly), apktool (E2E manifest decode), JUnit via kotlin.test.

**Spec:** `docs/superpowers/specs/2026-09-12-castle-clashers-patches-design.md` (this plan implements it; executors read both).

## Global Constraints

- Target: `com.epicoro.castleclashers`, version **1.17.2** (code 696) only. Patches fail loud on any other version.
- arm64-v8a only. Any native patch throws if another ABI's `libil2cpp.so` exists in the input.
- Rewarded ad paths stay byte-identical. `VoodooPremium` paid flows untouched. `PopulateEnemyPredictionTrajectories` untouched.
- Every patch site: unique byte-signature (exactly one match across the whole `.so`), expected original bytes asserted before writing, fail loud on 0 or >1 matches.
- All three new patches `dependsOn(app.hevy.patches.shared.preserveAppCode)` (imported across packages; per `PreserveAppCode.kt`, a bytecode patch must be in every selectable patch set).
- Env-gated tests skip via `org.junit.Assume` when variables are unset (Hevy precedent).
- Env vars: `CC_TEST_APK` (clean 1.17.2 APK), `CC_TEST_IL2CPP` (extracted `libil2cpp.so` path), `CC_TEST_OUTPUT_DIR`.
- `apk/` is gitignored: never commit APKs, `.so`, metadata, or RE artifacts.
- Never edit generated files: `patches-list.json`, `patches-bundle.json`, `CHANGELOG.md`, and the README block between `PATCHES_START`/`PATCHES_END`.
- Code comments describe current behavior, never the change history. No LLM attribution in commits.
- Commits on `dev`, semantic style: `feat:`, `fix:`, `chore:`, `docs:`, `test:`.
- Gradle registry credentials for the Morphe GitHub Packages repo come from `~/.gradle/gradle.properties` (`gpr.user`/`gpr.key`) locally; never write them into the repo.
- Rebrand the bundle name from "Hevy Pro Patches" to "Morphe Patches" (about block, `rootProject.name`, README title). Hevy patch behavior untouched.

## Task ordering and gates

- Task 1 (spike) gates Tasks 6, 8, 9. Tasks 2-5 and 7 need only the staged split APKs in `apk/com.epicoro.castleclasher/`, not `CC_TEST_APK`.
- `CC_TEST_APK` is user-provided (clean single APK/APKM of 1.17.2). If unavailable when execution starts, run Tasks 2-5 and 7 first, then return to 1.
- The `.so` is byte-identical between the staged splits and a universal APK of the same build, so RE conclusions hold for both input formats.

## Task 1: Spike — patcher round-trips a write into `lib/arm64-v8a/libil2cpp.so`

**Files:**
- Test (throwaway, deleted in Task 9): `patches/src/test/kotlin/app/epicoro/castleclashers/LibPatchSpikeTest.kt`

**Interfaces:**
- Consumes: `CC_TEST_APK` (env), morphe `rawResourcePatch`/`Patcher` API (same pattern as `HermesPaywallPatch.kt`: `get(path)` returns `File`, mutate `readBytes()`, `writeBytes(data)`).
- Produces: GO/NO-GO gate. If this fails, Tasks 6, 8, 9 must not proceed; read the morhe-patcher 1.8.0 source (open source, jar in `~/.gradle/caches/modules-2/files-2.1/app.morphe/morphe-patcher/1.8.0/`) and redesign before continuing.

- [ ] **Step 1: Obtain input APK**

`CC_TEST_APK` must be a **plain APK** (tests read entries with `ZipFile`; an APKM nests multiple APKs and will not work). Preferred: ask the user for a clean APK of Castle Busters 1.17.2 and set `CC_TEST_APK`. If the user only has an APKM, extract its base APK first (`unzip app.apkm` produces `base.apk` plus config splits; use the base with `lib/arm64-v8a/libil2cpp.so` present, or the merged output of the fallback below).

Fallback (only if user APK not yet available): merge the staged splits with APKEditor.

```bash
mkdir -p apk/re
curl -L -o apk/re/APKEditor.jar https://github.com/REAndroid/APKEditor/releases/latest/download/APKEditor.jar
java -jar apk/re/APKEditor.jar m -i apk/com.epicoro.castleclasher -o apk/merged-universal.apk
export CC_TEST_APK=$PWD/apk/merged-universal.apk
```

If the merge fails (the UnityDataAssetPack asset-pack split may resist merging), stop the fallback and wait for the user APK. Do not hand-merge.

- [ ] **Step 2: Write the failing spike test**

```kotlin
package app.epicoro.castleclashers

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.patch.rawResourcePatch
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.Assume

private val libPatchSpikePatch = rawResourcePatch(
    name = "lib patch spike",
    description = "Round-trip write into lib/arm64-v8a/libil2cpp.so",
) {
    execute {
        val so = get("lib/arm64-v8a/libil2cpp.so")
        check(so.exists()) { "lib/arm64-v8a/libil2cpp.so not found in input APK" }
        val data = so.readBytes()
        for (i in 0x100..0x103) data[i] = 0x5A
        so.writeBytes(data)
    }
}

class LibPatchSpikeTest {

    @Test
    fun rawPatchRoundTripsLibil2cppWrite() {
        val apkPath = System.getenv("CC_TEST_APK")
        Assume.assumeTrue("CC_TEST_APK not set", apkPath != null)
        val inputApk = File(apkPath)

        val original = ZipFile(inputApk).use { zf ->
            zf.getInputStream(zf.getEntry("lib/arm64-v8a/libil2cpp.so")).use { it.readBytes() }
        }

        val started = System.currentTimeMillis()
        Patcher(PatcherConfig(inputApk, File("build/cc-spike-temp"))).use { patcher ->
            patcher += setOf(libPatchSpikePatch)
            runBlocking {
                patcher().collect { result ->
                    result.exception?.let { e -> fail("Spike patch failed: $e", e) }
                }
            }
        }
        println("Spike patcher run took ${System.currentTimeMillis() - started} ms")

        val patchedFile = File("build/cc-spike-temp").walkTopDown()
            .filter { it.isFile && it.path.endsWith("lib/arm64-v8a/libil2cpp.so") }
            .firstOrNull() ?: fail("spiked libil2cpp.so not found in patcher temp output")
        val patched = patchedFile.readBytes()

        assertEquals(original.size, patched.size)
        for (i in 0x100..0x103) {
            assertEquals(0x5A.toByte(), patched[i], "byte at 0x${i.toString(16)}")
        }
    }
}
```

- [ ] **Step 3: Run it**

```bash
./gradlew :patches:test --tests 'app.epicoro.castleclashers.LibPatchSpikeTest'
```

Expected: PASS. Log line shows the run duration (160 MB file; tens of seconds is fine, minutes means check memory settings). FAIL with "not found in input APK" means `lib/` is not addressable → read patcher source, redesign, and update the spec before any further native work.

- [ ] **Step 4: Commit the spike test** (throwaway label, deleted in Task 9)

```bash
git add patches/src/test/kotlin/app/epicoro/castleclashers/LibPatchSpikeTest.kt
git commit -m "test: Add Castle Clashers lib-path patcher spike"
```

## Task 2: RE tooling setup and input extraction

**Files:**
- Create (gitignored): `apk/re/inputs/libil2cpp.so`, `apk/re/inputs/global-metadata.dat`, `apk/re/dump/`, `apk/re/scripts/disasm_range.py`, `apk/re/scripts/scan_sig.py`
- Create (committed): `tools/ghidra/disasm_range.py` (durable, reused on future versions)

**Interfaces:**
- Consumes: staged APKs under `apk/com.epicoro.castleclasher/`.
- Produces: `apk/re/dump/dump.cs` (class/method declarations with RVA/Offset comments), a Ghidra project with `libil2cpp.so` imported (no auto-analysis), and the two scan scripts used by Tasks 5 and 7.

- [ ] **Step 1: Install tooling**

```bash
brew install dotnet
brew install --cask ghidra
brew install openjdk@21
jenv add /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

Verify:

```bash
dotnet --version
ls /Applications | grep -i ghidra
```

Ghidra 11.x requires JDK 21; if the cask refuses, check `brew info ghidra` for the pinned JDK and install that instead.

- [ ] **Step 2: Download Il2CppDumper**

Fetch the release zip from https://github.com/Perfare/Il2CppDumper/releases (pin the latest release tag, note the version in the step log). Use the generic zip (not the `-win` one) and run it via dotnet:

```bash
unzip Il2CppDumper.zip -d apk/re/Il2CppDumper
```

If the release only ships OS-specific assets, build from source:

```bash
git clone https://github.com/Perfare/Il2CppDumper.git apk/re/Il2CppDumper-src
dotnet publish apk/re/Il2CppDumper-src/Il2CppDumper -c Release -o apk/re/Il2CppDumper
```

- [ ] **Step 3: Extract inputs**

```bash
mkdir -p apk/re/inputs
unzip -j -o apk/com.epicoro.castleclasher/base.apk \
  'assets/bin/Data/Managed/Metadata/global-metadata.dat' -d apk/re/inputs/
unzip -j -o apk/com.epicoro.castleclasher/split_config.arm64_v8a.apk \
  'lib/arm64-v8a/libil2cpp.so' -d apk/re/inputs/
ls -la apk/re/inputs/
```

Expected: `global-metadata.dat` 26 MB, `libil2cpp.so` 160 MB.

- [ ] **Step 4: Dump metadata**

```bash
mkdir -p apk/re/dump
dotnet apk/re/Il2CppDumper/Il2CppDumper.dll \
  apk/re/inputs/libil2cpp.so apk/re/inputs/global-metadata.dat apk/re/dump
```

Expected: `apk/re/dump/dump.cs` and `script.json` exist. Sanity check:

```bash
grep -c 'Voodoo.Sauce.Internal.Ads' apk/re/dump/dump.cs
grep -n 'class TrajectoryController' apk/re/dump/dump.cs | head
grep -n 'PopulatePlayersPredictionTrajectories' apk/re/dump/dump.cs | head
```

Expected: ads namespace hits > 0, both game symbols present.

- [ ] **Step 5: Create the Ghidra disassembly script (committed copy + working copy)**

Create `tools/ghidra/disasm_range.py`:

```python
# Ghidra headless script (Jython).
# Usage: analyzeHeadless ... -postScript disasm_range.py <startAddrHex> <lengthHex>
# Disassembles [start, start+length) and prints one instruction per line.
from ghidra.program.model.address import AddressSet
from ghidra.app.cmd.disassemble import DisassembleCommand

args = getScriptArgs()
start = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(int(args[0], 16))
length = int(args[1], 16)
end = start.add(length)
DisassembleCommand(start, AddressSet(start, end), True).applyTo(currentProgram, monitor)

for ins in currentProgram.getListing().getInstructions(start, True):
    print("%s  %s" % (ins.getAddress(), ins))
    if ins.getAddress() >= end:
        break
```

Copy it into the RE workspace:

```bash
mkdir -p apk/re/scripts
cp tools/ghidra/disasm_range.py apk/re/scripts/
```

- [ ] **Step 6: Create the uniqueness scan script**

Create `apk/re/scripts/scan_sig.py`:

```python
# Usage: python3 scan_sig.py <file> <hex signature>
# Prints every match offset and fails unless the signature matches exactly once.
import sys
data = open(sys.argv[1], 'rb').read()
sig = bytes.fromhex(sys.argv[2].replace(' ', '').replace('\n', ''))
n = 0
i = data.find(sig)
while i != -1:
    n += 1
    print('match at file offset', hex(i))
    i = data.find(sig, i + 1)
print('total', n)
assert n == 1, 'signature must match exactly once, got %d' % n
```

- [ ] **Step 7: Import libil2cpp.so into Ghidra (no auto-analysis)**

```bash
GHIDRA=/opt/homebrew/opt/ghidra/libexec   # brew formula install; the ghidra cask no longer exists
mkdir -p apk/re/ghidra-project            # analyzeHeadless refuses to create a missing project dir
# Ghidra needs JDK 21; jenv's shell hook forces JAVA_HOME to its java 19, so bypass it:
env -u JAVA_HOME PATH="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin:$PATH" \
  "$GHIDRA/support/analyzeHeadless" apk/re/ghidra-project castle \
  -import apk/re/inputs/libil2cpp.so -noanalysis
```

Expected: import completes (this can take several minutes; no analysis pass). Address mapping check: for a known method, pick the `Offset` (file offset) from `dump.cs`, read 16 bytes at that file offset, then read the same bytes at the Ghidra address equal to that offset; if they differ, use the method's `VA` value instead and re-check. Record the mapping decision (used by all disassembly steps).

**Confirmed mapping for this machine (verified during Task 2 execution):** Ghidra rebases the ELF to image base `0x00100000` at import, so **Ghidra address = dump.cs `VA` + `0x100000`** (equivalently `Offset + 0x104000`, since VA = Offset + 0x4000 on the text segment). Reads at the raw `Offset` or raw `VA` return zeros. Cross-checked with `PopulatePlayersPredictionTrajectories` (VA `0x43A4138`): the 16 file bytes at offset `0x43A0138` equal the Ghidra bytes at `0x44A4138`, and disassembling `0x44A4138` yields the method's prologue (it reads `[x19,#0x28]`, matching `maxArtilleryAngle` at field offset 0x28).

- [ ] **Step 8: Commit the tool script**

```bash
git add tools/ghidra/disasm_range.py
git commit -m "chore: Add Ghidra range disassembly script"
```

## Task 3: Castle Clashers app skeleton, PackageRename patch, bundle rebrand

**Files:**
- Create: `patches/src/main/kotlin/app/epicoro/castleclashers/patches/shared/Constants.kt`
- Create: `patches/src/main/kotlin/app/epicoro/castleclashers/patches/packagerename/PackageRenamePatch.kt`
- Create: `patches/src/main/kotlin/app/epicoro/castleclashers/patches/Patches.kt`
- Modify: `settings.gradle.kts` (rootProject.name)
- Modify: `patches/build.gradle.kts` (about block)
- Modify: `README.md` (title + about text; generated patch-list block untouched)

**Interfaces:**
- Consumes: `app.hevy.patches.shared.preserveAppCode` (existing).
- Produces (later tasks rely on these names):
  - `Constants.COMPATIBILITY_CASTLE_APKM`, `Constants.COMPATIBILITY_CASTLE_APK` (each `targets = listOf(AppTarget(version = "1.17.2"))`)
  - `app.epicoro.castleclashers.patches.allPatches` (initially `listOf(packageRenamePatch)`)
  - `packageRenamePatch` in `app.epicoro.castleclashers.patches.packagerename`

- [ ] **Step 1: Sample the launcher icon color**

```bash
apktool d -s -f -o apk/re/decode-base apk/com.epicoro.castleclasher/base.apk
grep -o 'android:icon="[^"]*"' apk/re/decode-base/AndroidManifest.xml
python3 -m pip install --user pillow
```

Open the icon file the manifest points at (resolve the `@mipmap/...` reference to a PNG under `apk/re/decode-base/res/`; if it is an adaptive XML, follow its background/foreground drawables to a PNG) and run:

```python
from PIL import Image
from collections import Counter
im = Image.open('ICON_PATH').convert('RGBA')
w, h = im.size
px = im.crop((w//4, h//4, 3*w//4, 3*h//4)).getdata()
print(Counter(c for c in px if c[3] > 200).most_common(3))
```

Use the most common opaque color (the icon background) as the hex value for `appIconColor` in Step 3.

- [ ] **Step 2: Verify rename assumptions**

```bash
grep -c 'name="app_name"' apk/re/decode-base/res/values/strings.xml
```

Expected: exactly 1 (the patch asserts this). List every `android:authorities` under `apk/re/decode-base/AndroidManifest.xml` and confirm all start with `com.epicoro.castleclashers`.

- [ ] **Step 3: Write `Constants.kt`** (insert the sampled hex from Step 1)

```kotlin
package app.epicoro.castleclashers.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_CASTLE_APKM = Compatibility(
        name = "Castle Busters",
        packageName = "com.epicoro.castleclashers",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x000000, // sampled launcher-icon background, set in Step 1
        targets = listOf(AppTarget(version = "1.17.2")),
    )

    val COMPATIBILITY_CASTLE_APK = Compatibility(
        name = "Castle Busters",
        packageName = "com.epicoro.castleclashers",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x000000, // same sampled value as the APKM entry
        targets = listOf(AppTarget(version = "1.17.2")),
    )
}
```

- [ ] **Step 4: Write `PackageRenamePatch.kt`**

```kotlin
package app.epicoro.castleclashers.patches.packagerename

import app.epicoro.castleclashers.patches.shared.Constants
import app.hevy.patches.shared.preserveAppCode
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element

private const val ORIGINAL_PACKAGE = "com.epicoro.castleclashers"
private const val APP_NAME_RESOURCE = "app_name"

val packageRenamePatch = resourcePatch(
    name = "Rename package & app name",
    description = "Renames the app package so the patched game can be installed alongside the original Castle Busters, and lets you change the app's launcher name.",
    default = true,
) {
    val packageNameOption = stringOption(
        key = "package-name",
        default = "com.epicoro.castleclashers.mod",
        values = null,
        title = "Package name",
        description = "The new package name of the patched app. It must differ from the original package name.",
        required = true,
        validator = { value ->
            value != null && Regex("[a-zA-Z][\\w]*(?:\\.[\\w]+)+").matches(value)
        },
    )

    val appNameOption = stringOption(
        key = "app-name",
        default = "Castle Bustërs",
        values = null,
        title = "App name",
        description = "The launcher name of the patched game.",
        required = true,
        validator = { value -> !value.isNullOrBlank() },
    )

    compatibleWith(Constants.COMPATIBILITY_CASTLE_APKM, Constants.COMPATIBILITY_CASTLE_APK)

    dependsOn(preserveAppCode)

    execute {
        val packageName = checkNotNull(packageNameOption.value) {
            "The package name option must be set"
        }
        check(packageName != ORIGINAL_PACKAGE) {
            "Package name must differ from $ORIGINAL_PACKAGE"
        }
        val appName = checkNotNull(appNameOption.value) {
            "The app name option must be set"
        }

        // Only manifest attributes that must stay unique per install are
        // renamed: the package itself, content provider authorities, and
        // package-scoped permissions. Third-party permissions and class
        // names stay untouched; resource-side references are remapped by
        // the patcher's PackageRenamingProcessor.
        document("AndroidManifest.xml").use { manifest ->
            manifest.documentElement.setAttribute("package", packageName)

            val providers = manifest.getElementsByTagName("provider")
            for (i in 0 until providers.length) {
                val provider = providers.item(i) as Element
                val authorities = provider.getAttribute("android:authorities")
                if (authorities.startsWith(ORIGINAL_PACKAGE)) {
                    provider.setAttribute(
                        "android:authorities",
                        authorities.replaceFirst(ORIGINAL_PACKAGE, packageName),
                    )
                }
            }

            val permissions = manifest.getElementsByTagName("permission")
            for (i in 0 until permissions.length) {
                val permission = permissions.item(i) as Element
                val name = permission.getAttribute("android:name")
                if (name.startsWith(ORIGINAL_PACKAGE)) {
                    permission.setAttribute("android:name", name.replaceFirst(ORIGINAL_PACKAGE, packageName))
                }
            }

            val usesPermissions = manifest.getElementsByTagName("uses-permission")
            for (i in 0 until usesPermissions.length) {
                val usesPermission = usesPermissions.item(i) as Element
                val name = usesPermission.getAttribute("android:name")
                if (name.startsWith(ORIGINAL_PACKAGE)) {
                    usesPermission.setAttribute("android:name", name.replaceFirst(ORIGINAL_PACKAGE, packageName))
                }
            }
        }

        // The launcher label resolves to the app_name string.
        document("res/values/strings.xml").use { strings ->
            val stringNodes = strings.getElementsByTagName("string")
            var appNodeCount = 0
            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i) as Element
                if (node.getAttribute("name") == APP_NAME_RESOURCE) {
                    node.textContent = appName
                    appNodeCount++
                }
            }
            check(appNodeCount == 1) {
                "Expected exactly one $APP_NAME_RESOURCE string, found $appNodeCount"
            }
        }

        println("Package rename: renamed $ORIGINAL_PACKAGE to $packageName, app name to $appName")
    }
}
```

Note: this manifest has package-prefixed `uses-permission` entries (the game's custom permission is both declared and referenced), so the uses-permission loop matters.

- [ ] **Step 5: Write `Patches.kt`**

```kotlin
package app.epicoro.castleclashers.patches

import app.epicoro.castleclashers.patches.packagerename.packageRenamePatch

val allPatches = listOf(
    packageRenamePatch,
)
```

- [ ] **Step 6: Rebrand**

`settings.gradle.kts`: change `rootProject.name = "hevy-pro-patches"` to `rootProject.name = "morphe-patches"`.

`patches/build.gradle.kts`: in the `patches { about { ... } }` block set `name = "Morphe Patches"` and `description = "Patches for Hevy (Pro unlock) and Castle Busters (ads block, aim guide)"`; leave `source`, `author`, `contact`, `website`, `license` as-is.

`README.md`: replace the H1 and intro with a bundle-neutral title "Morphe Patches" describing both apps. Keep the `PATCHES_START`/`PATCHES_END` generated block untouched. Leave the Hevy how-to section as-is; a Castle Busters how-to is added in Task 10.

- [ ] **Step 7: Build and confirm artifact naming is unaffected**

```bash
./gradlew :patches:buildAndroid
ls patches/build/libs/
```

Expected: build green; artifact name still `patches-<version>.mpp` (module name, not rootProject name, drives it). If the artifact name changed, update `.github/workflows/release.yml` `subject-path:` glob to `patches/build/libs/*patches-*.mpp` and note it in the commit.

- [ ] **Step 8: Commit**

```bash
git add patches/src/main/kotlin/app/epicoro settings.gradle.kts patches/build.gradle.kts README.md
git commit -m "feat: Add Castle Clashers package rename patch and rebrand bundle to Morphe Patches"
```

## Task 4: Native patch plumbing — `NativeSite`, `Arm64Patcher`, synthetic tests

**Files:**
- Create: `patches/src/main/kotlin/app/epicoro/castleclashers/patches/native/NativeSite.kt`
- Create: `patches/src/main/kotlin/app/epicoro/castleclashers/patches/native/Arm64Patcher.kt`
- Test: `patches/src/test/kotlin/app/epicoro/castleclashers/native/Arm64PatcherTest.kt`

**Interfaces:**
- Consumes: nothing (pure functions).
- Produces (Tasks 5-9 rely on these exact names):
  - `data class NativeSite(name: String, description: String, signature: ByteArray, patchOffset: Int, expectedBytes: ByteArray, replacementBytes: ByteArray)`
  - `fun hex(pattern: String): ByteArray` (top-level in `NativeSite.kt`)
  - `object Arm64Patcher { fun applySites(file: File, sites: List<NativeSite>): List<String>; fun findMatches(data: ByteArray, needle: ByteArray): List<Int> }`

- [ ] **Step 1: Write the failing tests**

```kotlin
package app.epicoro.castleclashers.native

import app.epicoro.castleclashers.patches.native.Arm64Patcher
import app.epicoro.castleclashers.patches.native.NativeSite
import app.epicoro.castleclashers.patches.native.hex
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Arm64PatcherTest {

    // Default site: the patch window (expected bytes) sits inside the
    // signature window, which is how RE-derived sites are authored.
    private fun site(
        signature: ByteArray = hex("AA BB CC DD 10 20 30 40 99 88 77 66"),
        patchOffset: Int = 4,
        expected: ByteArray = hex("10 20 30 40"),
        replacement: ByteArray = hex("1F 20 03 D5"),
    ) = NativeSite("test site", "synthetic", signature, patchOffset, expected, replacement)

    private fun buffer(): ByteArray = ByteArray(64) { it.toByte() }

    // Plants the signature, which already contains the expected bytes at
    // patchOffset, exactly like a real patched binary would sit.
    private fun build(offset: Int, s: NativeSite): ByteArray {
        val data = buffer()
        s.signature.copyInto(data, offset)
        return data
    }

    @Test
    fun patchesTheSingleMatch() {
        val s = site()
        val file = File.createTempFile("arm64", ".bin")
        try {
            build(16, s).also { file.writeBytes(it) }
            val logs = Arm64Patcher.applySites(file, listOf(s))
            assertEquals(1, logs.size)
            val data = file.readBytes()
            val start = 16 + s.patchOffset
            assertTrue(s.replacementBytes.contentEquals(data.copyOfRange(start, start + 4)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun throwsOnZeroMatches() {
        val file = File.createTempFile("arm64", ".bin")
        try {
            file.writeBytes(buffer())
            assertFailsWith<IllegalStateException> { Arm64Patcher.applySites(file, listOf(site())) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun throwsOnMultipleMatches() {
        val s = site()
        val file = File.createTempFile("arm64", ".bin")
        try {
            build(8, s).also { data -> s.signature.copyInto(data, 32); file.writeBytes(data) }
            assertFailsWith<IllegalStateException> { Arm64Patcher.applySites(file, listOf(s)) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun throwsWhenExpectedBytesDoNotMatch() {
        // Window outside the signature: corrupting it must not disturb the
        // signature match, isolating the expected-bytes assertion.
        val s = NativeSite(
            "test site", "synthetic",
            signature = hex("AA BB CC DD EE FF 11 22"),
            patchOffset = 8,
            expectedBytes = hex("10 20 30 40"),
            replacementBytes = hex("1F 20 03 D5"),
        )
        val data = build(16, s)
        hex("10 20 30 40").copyInto(data, 16 + s.patchOffset)
        data[16 + s.patchOffset + 1] = 0x7F // corrupt one expected byte
        val file = File.createTempFile("arm64", ".bin")
        try {
            file.writeBytes(data)
            assertFailsWith<IllegalStateException> { Arm64Patcher.applySites(file, listOf(s)) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun throwsWhenSiteExtendsPastBuffer() {
        val s = NativeSite(
            "test site", "synthetic",
            signature = hex("AA BB CC DD EE FF 11 22"),
            patchOffset = 8,
            expectedBytes = hex("10 20 30 40"),
            replacementBytes = hex("1F 20 03 D5"),
        )
        val data = buffer()
        s.signature.copyInto(data, 56) // signature occupies 56..64; window would be 64..68
        val file = File.createTempFile("arm64", ".bin")
        try {
            file.writeBytes(data)
            assertFailsWith<IllegalStateException> { Arm64Patcher.applySites(file, listOf(s)) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun findMatchesFindsOverlappingCandidatesCorrectly() {
        val data = ByteArray(16)
        hex("AA AA AA").copyInto(data, 0)
        hex("AA AA AA").copyInto(data, 1)
        assertEquals(2, Arm64Patcher.findMatches(data, hex("AA AA AA")).size)
        assertEquals(0, Arm64Patcher.findMatches(data, hex("BB BB")).size)
    }
}
```

- [ ] **Step 2: Run, verify compile failure**

```bash
./gradlew :patches:test --tests 'app.epicoro.castleclashers.native.Arm64PatcherTest'
```

Expected: FAIL, unresolved references (`NativeSite`/`Arm64Patcher` do not exist yet).

- [ ] **Step 3: Implement `NativeSite.kt`**

```kotlin
package app.epicoro.castleclashers.patches.native

/**
 * One verified byte-patch site inside a native library.
 *
 * [signature] is a byte window that must match exactly once in the whole
 * file; it must be unique enough to survive unrelated code changes and
 * include the patch site. [patchOffset] is where the replacement begins,
 * relative to the signature start. [expectedBytes] are asserted against
 * the file before writing [replacementBytes]; both must have equal length.
 */
data class NativeSite(
    val name: String,
    val description: String,
    val signature: ByteArray,
    val patchOffset: Int,
    val expectedBytes: ByteArray,
    val replacementBytes: ByteArray,
)

/** Parses a space-separated hex string like "1F 20 03 D5" into bytes. */
fun hex(pattern: String): ByteArray =
    pattern.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
```

- [ ] **Step 4: Implement `Arm64Patcher.kt`**

```kotlin
package app.epicoro.castleclashers.patches.native

import java.io.File

object Arm64Patcher {

    /**
     * Applies every site to [file] in one read-modify-write pass. Each site
     * must match its signature exactly once, and the expected original
     * bytes must be present at the patch offset, or the whole call throws
     * and the file is left unpatched (throwing happens before any write).
     */
    fun applySites(file: File, sites: List<NativeSite>): List<String> {
        val data = file.readBytes()
        val logs = sites.map { applySite(data, it) }
        file.writeBytes(data)
        return logs
    }

    private fun applySite(data: ByteArray, site: NativeSite): String {
        check(site.expectedBytes.size == site.replacementBytes.size) {
            "Site ${site.name}: expected/replacement length mismatch"
        }
        val matches = findMatches(data, site.signature)
        check(matches.size == 1) {
            "Site ${site.name}: expected exactly 1 signature match, found ${matches.size}"
        }
        val start = matches[0] + site.patchOffset
        val end = start + site.expectedBytes.size
        check(end <= data.size) {
            "Site ${site.name}: patch window $start..$end exceeds file size ${data.size}"
        }
        val actual = data.copyOfRange(start, end)
        check(actual.contentEquals(site.expectedBytes)) {
            "Site ${site.name}: expected ${site.expectedBytes.toHex()} at $start, found ${actual.toHex()}"
        }
        site.replacementBytes.copyInto(data, start)
        return "${site.name}: patched ${site.expectedBytes.size} bytes at file offset $start"
    }

    fun findMatches(data: ByteArray, needle: ByteArray): List<Int> {
        val result = ArrayList<Int>()
        var i = 0
        while (i <= data.size - needle.size) {
            if (matchesAt(data, i, needle)) result.add(i)
            i += 1
        }
        return result
    }

    private fun matchesAt(data: ByteArray, offset: Int, needle: ByteArray): Boolean {
        for (j in needle.indices) {
            if (data[offset + j] != needle[j]) return false
        }
        return true
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
}
```

- [ ] **Step 5: Run the tests, verify pass**

```bash
./gradlew :patches:test --tests 'app.epicoro.castleclashers.native.Arm64PatcherTest'
```

Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add patches/src/main/kotlin/app/epicoro/castleclashers/patches/native patches/src/test/kotlin/app/epicoro/castleclashers/native
git commit -m "feat: Add signature-based native .so patch plumbing"
```

## Task 5: RE — ads block sites (and CodeHash enforcement determination)

**Files:**
- Create: `patches/src/main/kotlin/app/epicoro/castleclashers/patches/native/Sites.kt`
- Test: `patches/src/test/kotlin/app/epicoro/castleclashers/native/NativeSitesTest.kt`

**Interfaces:**
- Consumes: Task 2 artifacts (`apk/re/dump/dump.cs`, Ghidra project, scripts), Task 4 types (`NativeSite`, `hex`, `Arm64Patcher.findMatches`), `CC_TEST_IL2CPP`.
- Produces: `adsSites: List<NativeSite>`, `codeHashSites: List<NativeSite>`, and the RE decision record for CodeHash enforcement (comment in `Sites.kt`). Task 6 consumes `adsSites + codeHashSites`.

ARM64 reference (little-endian byte order):

| Instruction | Bytes |
|---|---|
| NOP | `1F 20 03 D5` |
| RET | `C0 03 5F D6` |
| MOV W0, #0 | `00 00 80 52` |
| MOV W0, #1 | `20 00 80 52` |
| MOV X0, #0 | `00 00 80 D2` |

- [ ] **Step 1: Write the failing site test (TDD anchor)**

```kotlin
package app.epicoro.castleclashers.native

import app.epicoro.castleclashers.patches.native.Arm64Patcher
import app.epicoro.castleclashers.patches.native.adsSites
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume

class NativeSitesTest {

    @Test
    fun everyAdsSiteMatchesExactlyOnceInRealBinary() {
        val soPath = System.getenv("CC_TEST_IL2CPP")
        Assume.assumeTrue("CC_TEST_IL2CPP not set", soPath != null)
        val data = File(soPath).readBytes()
        assertTrue(adsSites.isNotEmpty(), "No ads sites defined yet")
        for (site in adsSites) {
            val matches = Arm64Patcher.findMatches(data, site.signature)
            assertEquals(1, matches.size, "Site ${site.name} signature match count")
            val start = matches[0] + site.patchOffset
            val actual = data.copyOfRange(start, start + site.expectedBytes.size)
            assertTrue(
                actual.contentEquals(site.expectedBytes),
                "Site ${site.name} expected bytes mismatch at $start",
            )
        }
    }
}
```

Run it (expects FAIL, `adsSites` does not exist yet):

```bash
./gradlew :patches:test --tests 'app.epicoro.castleclashers.native.NativeSitesTest'
export CC_TEST_IL2CPP=$PWD/apk/re/inputs/libil2cpp.so
```

- [ ] **Step 2: Locate the ads classes and methods in dump.cs**

```bash
grep -n 'Voodoo.Sauce.Internal.Ads' apk/re/dump/dump.cs
```

Read the class declarations and method lists for interstitial and banner types. For each candidate method record: class name, method name, parameter types, return type, `RVA`/`Offset` comment. Candidate selection rules:

- Include: the show entry points and the load/availability gates for **interstitial** and **banner** formats (names like `Show`, `Load`, `IsLoaded`, `HasAd`, `IsReady`, `CanShow` — actual names come from dump.cs).
- Exclude: anything containing `Rewarded` (must remain byte-identical), and `VoodooPremium` purchase/entitlement classes.

Also enumerate the C# callers: search dump.cs for the classes that hold these objects (Voodoo.Sauce glue and `\Assets\SDKAdapters\*` file-path strings list the adapter classes). The caller structure determines the contract.

- [ ] **Step 3: Disassemble each candidate**

For each candidate, from its `Offset`/`VA`: convert to the Ghidra address first — **Ghidra address = `VA` + `0x100000`** (mapping confirmed in Task 2 Step 7). Ghidra 12 removed Jython, so `analyzeHeadless -postScript <script>.py` cannot run Python scripts; the validated runtime is PyGhidra, with a venv at `apk/re/pyghidra-venv` (created offline in Task 2; if missing, recreate with `python3 -m venv apk/re/pyghidra-venv && apk/re/pyghidra-venv/bin/pip install --no-index -f /opt/homebrew/opt/ghidra/libexec/Ghidra/Features/PyGhidra/pypkg/dist pyghidra`). The invocation below reuses the already-imported program in project `castle` (no re-import; the first call pays roughly a minute of JVM startup and program-open time). `$PWD` and `env -u JAVA_HOME` matter: Ghidra's project locator requires absolute paths, and jenv's shell hook pins `JAVA_HOME` to java 19 while Ghidra needs 21.

```bash
env -u JAVA_HOME PATH="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin:$PATH" \
apk/re/pyghidra-venv/bin/python -c "import pyghidra; pyghidra.start(install_dir='/opt/homebrew/opt/ghidra/libexec'); pyghidra.run_script('$PWD/apk/re/inputs/libil2cpp.so', 'tools/ghidra/disasm_range.py', project_location='$PWD/apk/re/ghidra-project', project_name='castle', script_args=['<GHIDRA_ADDR>', '<LEN_HEX>'], nested_project_location=False, analyze=False)"
```

(`nested_project_location=False` is required because analyzeHeadless created the project with the non-nested layout; the pyghidra CLI cannot pass this flag and would silently create a second, nested project and re-import the 160 MB binary.)

Start at the method entry (or slightly before) with `LEN` 0x200-0x600; extend as needed to understand the function.

- [ ] **Step 4: Choose sites and record the contract**

For each chosen site write down, before crafting bytes:

1. The function and what it returns/does when it "has no ad" (trace the caller in C# from dump.cs: which path does the caller take on the "no ad" value, does it fire a failure callback, does the level flow advance).
2. Patch semantics: prefer gates over blind entry returns. A gate that reports "not ready/not available" makes the caller take its natural failure path. A show-entry early return is acceptable only if the caller provably continues without hanging.
3. Whether the function is shared with rewarded flows — if shared, a different site or a runtime-value guard is required; do not patch shared entries.

Craft the patch bytes per the ARM64 table (void gate: early `RET`; bool gate: `MOV W0, #0` + `RET`, or `#1` where "true" means unavailable — the disassembly decides; layer/type-specific constants may replace simple returns).

Define each `signature`: 16-32 bytes of surrounding instruction bytes from the same function that include distinctive immediates/constants and fully contain the patch window (patchOffset + expected length inside the window). Verify uniqueness with Task 2's scan script:

```bash
python3 apk/re/scripts/scan_sig.py apk/re/inputs/libil2cpp.so '<SIG HEX>'
```

Expected: `total 1`. If >1, extend the window with more surrounding bytes and rescan.

- [ ] **Step 5: Determine CodeHash enforcement**

1. `grep -n 'Genuine.CodeHash\|CodeHashGenerator\|BuildHashes' apk/re/dump/dump.cs` — enumerate the ACTk Genuine classes and their methods.
2. Search dump.cs for game-side wrappers (class names containing `Integrity`, `CodeHash`, `Tamper`) and VoodooTune config keys containing `integrity`/`codehash` (metadata strings: `strings apk/re/inputs/global-metadata.dat | grep -i ...` or the dump's string literals file).
3. Find callers: search dump.cs for classes that construct/start these detectors; if inconclusive, locate the `CodeHashGenerator` methods' addresses and scan for their callers with the PyGhidra `open_project` API (exhaustive B/BL branch-instruction scan over the loaded program, the `apk/re/scripts/scan_bl.py` approach) or, failing that, full auto-analysis (slow on the 160 MB binary — run it if needed, do not skip the determination).
4. Write the conclusion into `Sites.kt` as a comment: active-and-enforced (with the detection-event routing evidence) → `codeHashSites` gets a row neutralizing the compare/report path (same procedure: contract first, then bytes, then uniqueness scan); not started or report-only (event goes to a log) → empty list plus the evidence comment.

- [ ] **Step 6: Write `Sites.kt`**

Structure (rows filled from Steps 4-5; every row carries the contract in its `description`):

```kotlin
package app.epicoro.castleclashers.patches.native

// Reverse-engineered patch sites for Castle Busters 1.17.2 (libil2cpp.so arm64).
// Each site description states: target function, why this site, and the
// behavioral contract after patching (what the caller does with the result).
// Rows were verified against the 1.17.2 binary; the unit tests re-verify
// signature uniqueness and expected bytes on every run.

// Site rows produced by Task 5 Step 4/5, e.g.:
//   NativeSite(
//     name = "ads.interstitial.<method>",
//     description = "<class>.<method>(<params>): <contract>. Evidence: <caller path from dump.cs>.",
//     signature = hex("..."),
//     patchOffset = N,
//     expectedBytes = hex("..."),
//     replacementBytes = hex("..."),
//   )

val adsSites = listOf<NativeSite>()
val codeHashSites = listOf<NativeSite>() // or filled per Step 5
```

- [ ] **Step 7: Run the site test, verify pass**

```bash
./gradlew :patches:test --tests 'app.epicoro.castleclashers.native.NativeSitesTest'
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add patches/src/main/kotlin/app/epicoro/castleclashers/patches/native/Sites.kt patches/src/test/kotlin/app/epicoro/castleclashers/native/NativeSitesTest.kt
git commit -m "feat: Add RE-derived ads block sites for Castle Clashers"
```

## Task 6: `AdsBlockPatch` wiring

**Files:**
- Create: `patches/src/main/kotlin/app/epicoro/castleclashers/patches/adblock/AdsBlockPatch.kt`
- Modify: `patches/src/main/kotlin/app/epicoro/castleclashers/patches/Patches.kt`

**Interfaces:**
- Consumes: `adsSites`, `codeHashSites` (Task 5), `Arm64Patcher` (Task 4), `Constants.COMPATIBILITY_CASTLE_*` (Task 3), `preserveAppCode`.
- Produces: `adBlockPatch` added to `allPatches`.

- [ ] **Step 1: Write `AdsBlockPatch.kt`**

```kotlin
package app.epicoro.castleclashers.patches.adblock

import app.epicoro.castleclashers.patches.native.Arm64Patcher
import app.epicoro.castleclashers.patches.native.adsSites
import app.epicoro.castleclashers.patches.native.codeHashSites
import app.epicoro.castleclashers.patches.shared.Constants
import app.hevy.patches.shared.preserveAppCode
import app.morphe.patcher.patch.rawResourcePatch

val adBlockPatch = rawResourcePatch(
    name = "Block ads (banner & interstitial)",
    description = "Prevents banner and interstitial ads from loading and displaying. Rewarded ads still work and still grant rewards.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_CASTLE_APKM, Constants.COMPATIBILITY_CASTLE_APK)

    dependsOn(preserveAppCode)

    execute {
        // The RE-derived sites only exist in the arm64-v8a library; a
        // multi-ABI input would ship unpatched 32-bit code.
        for (abi in listOf("armeabi-v7a", "x86", "x86_64")) {
            val otherAbiLib = get("lib/$abi/libil2cpp.so")
            check(!otherAbiLib.exists()) { "Unsupported ABI $abi present; arm64-v8a only" }
        }

        val so = get("lib/arm64-v8a/libil2cpp.so")
        check(so.exists()) { "lib/arm64-v8a/libil2cpp.so not found in the APK" }

        val logs = Arm64Patcher.applySites(so, adsSites + codeHashSites)
        logs.forEach(::println)
    }
}
```

- [ ] **Step 2: Add to `Patches.kt`**

```kotlin
package app.epicoro.castleclashers.patches

import app.epicoro.castleclashers.patches.adblock.adBlockPatch
import app.epicoro.castleclashers.patches.packagerename.packageRenamePatch

val allPatches = listOf(
    packageRenamePatch,
    adBlockPatch,
)
```

- [ ] **Step 3: Build green**

```bash
./gradlew :patches:buildAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add patches/src/main/kotlin/app/epicoro/castleclashers/patches
git commit -m "feat: Add Castle Clashers ads block patch"
```

## Task 7: RE — aim guide sites

**Files:**
- Modify: `patches/src/main/kotlin/app/epicoro/castleclashers/patches/native/Sites.kt` (add `aimGuideSites`)
- Modify: `patches/src/test/kotlin/app/epicoro/castleclashers/native/NativeSitesTest.kt` (add aim test)

**Interfaces:**
- Consumes: Task 2 artifacts, Task 4 types, Task 5 procedure (same workflow).
- Produces: `aimGuideSites: List<NativeSite>`. Task 8 consumes it.

- [ ] **Step 1: Add the failing aim-site test to `NativeSitesTest`**

```kotlin
    @Test
    fun everyAimGuideSiteMatchesExactlyOnceInRealBinary() {
        val soPath = System.getenv("CC_TEST_IL2CPP")
        Assume.assumeTrue("CC_TEST_IL2CPP not set", soPath != null)
        val data = File(soPath).readBytes()
        assertTrue(aimGuideSites.isNotEmpty(), "No aim guide sites defined yet")
        for (site in aimGuideSites) {
            val matches = Arm64Patcher.findMatches(data, site.signature)
            assertEquals(1, matches.size, "Site ${site.name} signature match count")
            val start = matches[0] + site.patchOffset
            val actual = data.copyOfRange(start, start + site.expectedBytes.size)
            assertTrue(
                actual.contentEquals(site.expectedBytes),
                "Site ${site.name} expected bytes mismatch at $start",
            )
        }
    }
```

(Add `aimGuideSites` to the imports.) Run, expect FAIL (`aimGuideSites` undefined):

```bash
./gradlew :patches:test --tests 'app.epicoro.castleclashers.native.NativeSitesTest'
```

- [ ] **Step 2: Locate the trajectory code in dump.cs**

```bash
grep -n 'class TrajectoryController\|class ShotController' apk/re/dump/dump.cs
grep -n 'PopulatePlayersPredictionTrajectories\|PopulateEnemyPredictionTrajectories' apk/re/dump/dump.cs
```

Record for `TrajectoryController`: all methods with RVAs; the `_backgroundTrajectoryRenderer` field type and the methods that feed it. Record for `ShotController`: `PopulatePlayersPredictionTrajectories` and any helpers it calls that appear in dump.cs (trajectory simulation, raycast, distance helpers).

- [ ] **Step 3: Disassemble and identify the termination conditions**

Disassemble `PopulatePlayersPredictionTrajectories` and the `TrajectoryController` methods it drives (Task 5 Step 3 commands). Map the loop shape and write down every termination condition you find:

- Step/point-count bound (compare against constant or field),
- Distance cap (constant or `AimConfig`-derived field read),
- Collision test (raycast/layer-mask `AND`/branch), and which collision layers terminate the drawn path.

Determine which condition produces the observed "very short" guide. If multiple bind together, note their precedence.

- [ ] **Step 4: Choose sites per the spec contract**

Contract: the drawn path continues past castles, walls, and units; **only ground contact ends it**; player path only. Site selection:

- Obstacle termination: patch the collision predicate so only the ground layer terminates (edit the layer-mask compare or NOP the obstacle-hit branch; never NOP shared branches also used by the enemy path — if prediction code is shared, choose a site downstream of the player/enemy split or gate by the caller).
- Caps: if a step/distance bound binds before ground contact, patch the bound (raise the constant or NOP the bound branch). Where the bound comes from `AimConfig` (VoodooTune), patch the consumer-side use, not a config value.
- Every site: contract written first (what terminates the loop after the patch), then bytes, then uniqueness scan (Task 5 Step 4-5 procedure).

- [ ] **Step 5: Add rows to `Sites.kt` and run the test**

Append `aimGuideSites` with rows in the Task 5 Step 6 format, then:

```bash
./gradlew :patches:test --tests 'app.epicoro.castleclashers.native.NativeSitesTest'
```

Expected: both ads and aim site tests PASS.

- [ ] **Step 6: Commit**

```bash
git add patches/src/main/kotlin/app/epicoro/castleclashers/patches/native/Sites.kt patches/src/test/kotlin/app/epicoro/castleclashers/native/NativeSitesTest.kt
git commit -m "feat: Add RE-derived aim guide sites for Castle Clashers"
```

## Task 8: `AimGuidePatch` wiring

**Files:**
- Create: `patches/src/main/kotlin/app/epicoro/castleclashers/patches/aimguide/AimGuidePatch.kt`
- Modify: `patches/src/main/kotlin/app/epicoro/castleclashers/patches/Patches.kt`

**Interfaces:**
- Consumes: `aimGuideSites`, `codeHashSites` (Tasks 5, 7), `Arm64Patcher`, `Constants`, `preserveAppCode`.
- Produces: `aimGuidePatch` added to `allPatches`.

- [ ] **Step 1: Write `AimGuidePatch.kt`**

```kotlin
package app.epicoro.castleclashers.patches.aimguide

import app.epicoro.castleclashers.patches.native.Arm64Patcher
import app.epicoro.castleclashers.patches.native.aimGuideSites
import app.epicoro.castleclashers.patches.native.codeHashSites
import app.epicoro.castleclashers.patches.shared.Constants
import app.hevy.patches.shared.preserveAppCode
import app.morphe.patcher.patch.rawResourcePatch

val aimGuidePatch = rawResourcePatch(
    name = "Extend aim guide",
    description = "Shows the full projectile trajectory while aiming. Obstacles no longer cut the guide short; it runs until ground contact.",
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

        val logs = Arm64Patcher.applySites(so, aimGuideSites + codeHashSites)
        logs.forEach(::println)
    }
}
```

- [ ] **Step 2: Add to `Patches.kt`**

```kotlin
package app.epicoro.castleclashers.patches

import app.epicoro.castleclashers.patches.aimguide.aimGuidePatch
import app.epicoro.castleclashers.patches.adblock.adBlockPatch
import app.epicoro.castleclashers.patches.packagerename.packageRenamePatch

val allPatches = listOf(
    packageRenamePatch,
    adBlockPatch,
    aimGuidePatch,
)
```

- [ ] **Step 3: Build green**

```bash
./gradlew :patches:buildAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add patches/src/main/kotlin/app/epicoro/castleclashers/patches
git commit -m "feat: Add Castle Clashers aim guide patch"
```

## Task 9: Headless E2E for Castle Clashers

**Files:**
- Test: `patches/src/test/kotlin/app/epicoro/castleclashers/CastleEndToEndTest.kt`
- Delete: `patches/src/test/kotlin/app/epicoro/castleclashers/LibPatchSpikeTest.kt`

**Interfaces:**
- Consumes: `allPatches` (Task 8), all site lists (Tasks 5, 7), `CC_TEST_APK`, `apktool` on PATH, `CC_TEST_OUTPUT_DIR`.
- Produces: the E2E gate from the spec (verification level 3). Also proves sequential same-file patch interplay (AdsBlock + AimGuide both edit the `.so`).

- [ ] **Step 1: Write the E2E test**

```kotlin
package app.epicoro.castleclashers

import app.epicoro.castleclashers.patches.allPatches
import app.epicoro.castleclashers.patches.native.Arm64Patcher
import app.epicoro.castleclashers.patches.native.adsSites
import app.epicoro.castleclashers.patches.native.aimGuideSites
import app.epicoro.castleclashers.patches.native.codeHashSites
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.w3c.dom.Element

class CastleEndToEndTest {

    @Test
    fun patchesCastleBustersEndToEnd() {
        val apkPath = System.getenv("CC_TEST_APK")
        Assume.assumeTrue("CC_TEST_APK not set", apkPath != null)
        val inputApk = File(apkPath)
        val outputDir = File(System.getenv("CC_TEST_OUTPUT_DIR") ?: "build/cc-e2e-output")
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val originalSo = ZipFile(inputApk).use { zf ->
            zf.getInputStream(zf.getEntry("lib/arm64-v8a/libil2cpp.so")).use { it.readBytes() }
        }
        val allSites = adsSites + aimGuideSites + codeHashSites
        assertTrue(allSites.isNotEmpty())

        Patcher(PatcherConfig(inputApk, File("build/cc-e2e-temp"))).use { patcher ->
            patcher += allPatches.toSet()
            runBlocking {
                patcher().collect { result ->
                    result.exception?.let { e -> fail("Patch ${result.patch.name} failed: $e", e) }
                }
            }
            val result = patcher.get()

            val originalDex = zipEntryNames(inputApk).filter { it.matches(Regex("classes\\d*\\.dex")) }
            assertEquals(originalDex.size, result.dexFiles.size, "dex passthrough count")

            val resourcesApk = result.resources.resourcesApk
                ?: fail("Expected compiled resources APK")
            resourcesApk.copyTo(outputDir.resolve("resources.apk"), overwrite = true)
        }

        val patchedSo = File("build/cc-e2e-temp").walkTopDown()
            .filter { it.isFile && it.path.endsWith("lib/arm64-v8a/libil2cpp.so") }
            .firstOrNull() ?: fail("patched libil2cpp.so not found")
        val patched = patchedSo.readBytes()
        assertEquals(originalSo.size, patched.size)

        val allowed = HashSet<Int>()
        for (site in allSites) {
            val originalMatch = Arm64Patcher.findMatches(originalSo, site.signature)
            assertEquals(1, originalMatch.size, "site ${site.name} signature in original")
            val win = originalMatch[0]
            (win + site.patchOffset until win + site.patchOffset + site.expectedBytes.size)
                .forEach { allowed.add(it) }

            // NOTE: an earlier draft of this block asserted that the site
            // signature re-matches in the patched .so. That is structurally
            // impossible for in-signature patch windows: the first patch
            // destroys the signature it sits in. The shipped test
            // (CastleEndToEndTest.kt) uses an anchored-window + diff-mask
            // form instead — replacement bytes at the original match offset,
            // signature context outside the patch window intact, and no
            // byte changed outside the declared windows — and that shipped
            // form is authoritative.
        }

        val diffs = originalSo.indices.filter { originalSo[it] != patched[it] }
        assertTrue(diffs.isNotEmpty(), "no bytes patched at all")
        val strays = diffs.filter { it !in allowed }
        assertTrue(
            strays.isEmpty(),
            "bytes changed outside declared sites: ${strays.size} examples ${strays.take(5)}",
        )
        for (site in allSites) {
            val m = Arm64Patcher.findMatches(originalSo, site.signature)[0] + site.patchOffset
            assertTrue(
                (m until m + site.expectedBytes.size).any { originalSo[it] != patched[it] },
                "site ${site.name} produced no change",
            )
        }

        val decodedDir = outputDir.resolve("decoded")
        val process = ProcessBuilder(
            "apktool", "d", "-f", "-s",
            "-o", decodedDir.absolutePath,
            outputDir.resolve("resources.apk").absolutePath,
        ).redirectErrorStream(true).start()
        check(process.waitFor() == 0) { "apktool failed:\n${process.inputStream.bufferedReader().readText()}" }

        assertRenamedManifest(decodedDir.resolve("AndroidManifest.xml"))
        assertRenamedAppName(decodedDir.resolve("res/values/strings.xml"), "Castle Bustërs")
    }

    private fun assertRenamedManifest(manifestFile: File) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        val root = document.documentElement as Element
        assertEquals("com.epicoro.castleclashers.mod", root.getAttribute("package"))

        val authorities = collectAttributes(document, "provider", "android:authorities")
        assertTrue(authorities.any { it.startsWith("com.epicoro.castleclashers.mod.") }, "renamed authorities")
        assertFalse(
            authorities.any { it.startsWith("com.epicoro.castleclashers.") && !it.startsWith("com.epicoro.castleclashers.mod.") },
            "un-renamed authorities: $authorities",
        )

        val permissionNames = collectAttributes(document, "permission", "android:name") +
            collectAttributes(document, "uses-permission", "android:name")
        assertTrue(
            permissionNames.any { it == "com.epicoro.castleclashers.mod.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" },
            "custom permission not renamed",
        )
        assertFalse(
            permissionNames.any { it.startsWith("com.epicoro.castleclashers.") && !it.startsWith("com.epicoro.castleclashers.mod.") },
            "un-renamed package permissions: $permissionNames",
        )
    }

    private fun assertRenamedAppName(stringsXmlFile: File, expected: String) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsXmlFile)
        val nodes = document.getElementsByTagName("string")
        val appNames = ArrayList<String>(nodes.length)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as Element
            if (node.getAttribute("name") == "app_name") appNames.add(node.textContent)
        }
        assertEquals(listOf(expected), appNames)
    }

    private fun collectAttributes(document: org.w3c.dom.Document, tag: String, attribute: String): List<String> {
        val nodes = document.getElementsByTagName(tag)
        return (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute(attribute) }
    }

    private fun zipEntryNames(file: File): List<String> =
        ZipFile(file).use { zf -> zf.entries().toList().map { it.name } }
}
```

- [ ] **Step 2: Run it**

```bash
export CC_TEST_APK=/path/to/clean-castle-busters-1.17.2.apk
./gradlew :patches:test --tests 'app.epicoro.castleclashers.CastleEndToEndTest'
```

Expected: PASS. Every failure mode maps to a design fix, not a weaker test: stray diffs outside sites mean a patch writes unintended bytes; missing diff inside a window means a replacement equals the original; dex count mismatch means `preserveAppCode` was not in the selected set. If the sequential same-file patch interplay fails here (one patch's edits lost by the other), the spec's fallback applies: merge both site lists into a single native patch before changing anything else.

- [ ] **Step 3: Delete the spike test**

```bash
git rm patches/src/test/kotlin/app/epicoro/castleclashers/LibPatchSpikeTest.kt
```

- [ ] **Step 4: Commit**

```bash
git add patches/src/test/kotlin/app/epicoro/castleclashers
git commit -m "test: Add Castle Clashers headless end-to-end verification"
```

## Task 10: README usage section and device-verification handoff

**Files:**
- Modify: `README.md` (manual sections only)

**Interfaces:**
- Consumes: final patch set, bundle build.
- Produces: user-facing instructions; the device test checklist below is handed to the user verbatim.

- [ ] **Step 1: Add the Castle Busters usage section to README.md** (outside the generated patch-list block)

Mirror the Hevy how-to structure: install Morphe Manager, add-source link (`https://morphe.software/add-source?github=RaymondSalim/morphe-patches`), select the Castle Busters app, get a clean Castle Busters **1.17.2** APK or APKM (APKMirror listing if it exists; do not invent a URL, search and verify, otherwise write "from any trusted APK mirror"), patch, install alongside the original. State: rewarded ads still work by design; the aim guide extends to ground contact; both patches can be toggled independently in Morphe Manager; version-locked to 1.17.2.

- [ ] **Step 2: Final build**

```bash
./gradlew :patches:buildAndroid clean
```

Expected: green, artifact at `patches/build/libs/patches-*.mpp` (or the updated glob from Task 3 Step 7).

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: Add Castle Busters usage instructions"
```

- [ ] **Step 4: Hand the device checklist to the user**

Install the patched APK (all three patches) on the user's device, then verify:

1. App installs and boots to gameplay, no crash.
2. No banner or interstitial ads appear across several level transitions.
3. An opt-in rewarded ad still plays and its in-game reward is granted.
4. Aim guide draws the full trajectory to ground contact, passing through/past enemy bases and walls.
5. Startup shows no tamper/lockout/anti-cheat state (CodeHash check).
6. Premium/IAP store UI loads (do not complete a purchase).
7. A few full sessions for stability; report any crash logs.

Report results back; failures feed the RE phase for site corrections (never weaken tests to pass).
