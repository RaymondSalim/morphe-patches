# Hevy Pro Patches — Bundle Compliance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `hevy-pro-patch` into a compliant, working Morphe patch bundle for Hevy 3.0.11 that reproduces the known-good manual mod (package rename + Hermes paywall bypass), verified byte-for-byte against it, and publishes via the template's dev-branch semantic-release pipeline to `RaymondSalim/morphe-patches`.

**Architecture:** Two patches registered in `Patches.kt`. `packageRenamePatch` is a `resourcePatch` editing only `AndroidManifest.xml` (the patcher's `PackageRenamingProcessor` handles resource-side renaming). `hermesPaywallPatch` is a `rawResourcePatch` backed by a dependency-free Hermes v96 parser (`hermes/` package) that locates 3 patch sites by string signatures + instruction patterns and applies the known-good 5-byte edit.

**Tech Stack:** Kotlin (JVM, Morphe patches convention plugin `app.morphe.patches` 1.3.3), Gradle 9.6.1, Java 21, kotlin.test, Morphe patcher API (fork of ReVanced patcher).

**Spec:** `docs/superpowers/specs/2026-09-08-hevy-pro-patches-design.md` (same repo). The spec contains the ground-truth byte-level analysis; executors should read both.

## Global Constraints

- Repo: `/Users/rsalim/personal/hevy-diff/hevy-pro-patch`, remote `git@github.com:RaymondSalim/morphe-patches`. All work lands on a new `dev` branch.
- Public source URL everywhere: `https://github.com/RaymondSalim/morphe-patches` (fix README, build.gradle.kts, issue templates to this; never `rsalim2/hevy-pro-patches`).
- Target app: Hevy 3.0.11, version code 2015570, package `com.hevy`.
- Semantic commits only (`chore:`, `feat:`, `fix:`). `feat`/`fix` pushes to `dev`/`main` trigger releases; never hand-edit `patches-list.json`, `patches-bundle.json`, `CHANGELOG.md`, or the README patch list section.
- Never commit secrets (GitHub tokens) or build outputs.
- Build: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` (JDK 21, matches CI). No Android SDK needed.
- Registry auth for Gradle: `GITHUB_ACTOR`/`GITHUB_TOKEN` env vars, or `gpr.user`/`gpr.key` in `~/.gradle/gradle.properties` (outside the repo). NEVER in project `gradle.properties`.
- Test fixtures (outside the repo, passed via env vars): `HEVY_TEST_ORIGINAL_BUNDLE=/Users/rsalim/personal/hevy-diff/work/original_apktool/assets/index.android.bundle`, `HEVY_TEST_MODDED_BUNDLE=/Users/rsalim/personal/hevy-diff/work/modded_apktool/assets/index.android.bundle`, `HEVY_TEST_ORIGINAL_APK=/Users/rsalim/personal/hevy-diff/work/original_merged.apk`. Tests skip when these are unset (CI has no fixtures).

## Validated ground truth (do not re-derive; verified against the real bundle in this session)

- Hermes v96, little-endian. Magic u64 @0 = `0x1F1903C103BC1FC6`; version u32 @8 = 96; 20-byte source hash @12; 19 u32 header fields @32 (offsets 0..18 in order): `fileLength, globalCodeIndex, functionCount, stringKindCount, identifierCount, stringCount, overflowStringCount, stringStorageSize, bigIntCount, bigIntStorageSize, regExpCount, regExpStorageSize, literalValueBufferSize, objKeyBufferSize, objShapeTableCount, segmentID, cjsModuleCount, functionSourceCount, debugInfoOffset`; then 1 options byte; then pad to 32-byte alignment → `functionsStart` (=128 for this bundle).
- Function headers: 16 bytes each at `functionsStart + 16*i`. Word0: `offset` = bits 0..24, `paramCount` = bits 25..31. Word1: `bytecodeSize` = bits 0..14, `functionName` = bits 15..31. Word2: `infoOffset` = bits 0..24, `frameSize` = bits 25..31. Byte12 = environmentSize, byte13 = highestReadCacheIndex, byte14 = highestWriteCacheIndex, byte15 = flags (bit5 = overflowed). If overflowed: large header at `(infoOffset << 16) | offset` with `offset` = u32 @+0 and `bytecodeSize` = u32 @+8.
- After the function-header table (align 4): `stringKindCount` u32 entries (count = bits 0..30, kind = bit 31), then align 4, then `identifierCount` u32 hashes (skip), then align 4, then `stringCount` u32 small string table entries (isUTF16 = bit 0, offset = bits 1..23, length = bits 24..31), then align 4, then `overflowStringCount` (u32 offset, u32 length) pairs, then align 4 → string storage base. String i: if length == 0xFF use overflow entry, else storage[offset .. offset+length); UTF-16LE if flagged, else Latin-1 (decode with errors ignored).
- Real bundle: functionCount=63609, stringCount=100409, string(62946)="isProStatusOverrideEnabled".
- Site signatures (verified unique on the real bundle):
  - Site 1 (pro-state reset): string sig `{HEVY_PRO_DISK_STORAGE_KEY, HEVY_PRO_LAST_SUCCESSFUL_FETCH}` matches 3 functions (22171 @0x106307b, 22174 @0x1063150, 22177 @0x106331d). Discriminator: `LoadConstFalse` (0x79) whose NEXT instruction (at +2) is `PutNewOwnById` (0x40) whose u16 string operand (at +5 relative to the LoadConstFalse) is the string `is_pro`. Matches exactly once: 0x106332D. Edit: 0x79 → 0x78.
  - Site 2 (isPro getter): string sig `{getProStatusOverride, force-pro, force-free}` matches exactly 1 function @0x1063393, size 127, prologue bytes `7C 00 29 01 01`. Edit: overwrite first 5 bytes with `78 00 5C 00 01` (LoadConstTrue r0; Ret r0).
  - Site 3 (grace getter): string sig `{lastSuccessfulFetch, lastFetchAt, expiresAt}` matches exactly 1 function @0x106357b, size 150. Tail (last 11 bytes) mask: `90 ?? 79 ?? 5C ?? 78 ?? 5C ??` (indices 0,3,5,7,9 of the tail). Edit: tail index 3 (file offset end-8 = 0x1063609): 0x79 → 0x78.
- Final result: 5 changed bytes at 0x106332D, 0x1063393, 0x1063395, 0x1063396, 0x1063609 + recomputed SHA-1 trailer (last 20 bytes = sha1 of everything before). The known-good modded bundle is byte-identical except its stale trailer.
- Instruction sizes for the opcodes used: GetEnvironment=0x29(3B), PutNewOwnById=0x40(5B), Ret=0x5C(2B), LoadConstTrue=0x78(2B), LoadConstFalse=0x79(2B), LoadThisNS=0x7C(2B), JmpTrue=0x90(3B).

## Prerequisites (before Task 1)

- [ ] **P1: Registry credentials.** The gh CLI token is used directly for Gradle: `GITHUB_TOKEN=$(gh auth token) GITHUB_ACTOR=$(gh api user --jq .login)`. Requires the gh session to have the `read:packages` scope; if `gh auth status` does not list it, run `gh auth refresh -h github.com -s read:packages` once (user confirms a device code in the browser). Verify resolution:
  ```bash
  GITHUB_TOKEN=$(gh auth token) GITHUB_ACTOR=RaymondSalim \
  JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
    ./gradlew help --no-daemon
  ```
  Expected: BUILD SUCCESSFUL. (A classic PAT with `read:packages` in `~/.gradle/gradle.properties` as `gpr.user`/`gpr.key` is the fallback if the OAuth route fails.)
- [ ] **P2: gh CLI auth.** `gh auth status` must be authenticated for `RaymondSalim` (needed in Task 8).

---

### Task 1: Repo + build cleanup (compilable baseline on `dev`)

**Files:**
- Modify: `gradle.properties` (restore template content)
- Modify: `.gitignore` (append `.DS_Store`)
- Modify: `patches/build.gradle.kts:1-13` (about block)
- Modify: `.github/ISSUE_TEMPLATE/bug_report.yml`, `.github/ISSUE_TEMPLATE/feature_request.yml`, `.github/ISSUE_TEMPLATE/config.yml` (template URLs)
- Modify: `patches/src/main/kotlin/app/hevy/patches/Patches.kt` (reset to empty list)
- Delete: `patches/CheckSyntax.kt`, `patches/build_temp.gradle.kts`, `patches/src/main/kotlin/app/hevy/patches/packagerename/PackageRenamePatch.kt`, `patches/src/main/kotlin/app/hevy/patches/hermespaywall/HermesPaywallPatch.kt`, all `.DS_Store`
- The uncommitted working tree already contains deletions of `extensions/` and the template example patches (`app/template/...`); commit them too.

**Interfaces:**
- Produces: `app.hevy.patches.allPatches : List<Patch<*>>` (empty for now; Task 5 fills it). Compilable baseline for Tasks 2-4.

- [ ] **Step 1: Create the dev branch**

```bash
cd /Users/rsalim/personal/hevy-diff/hevy-pro-patch
git checkout -b dev
```

- [ ] **Step 2: Restore gradle.properties** with exactly:

```
org.gradle.parallel = true
org.gradle.caching = true
kotlin.code.style = official
version = 1.0.0
```

(The current file has only `gpr.user=dummy` / `gpr.key=dummy` — delete those lines; dummy credentials break dependency resolution and would override env fallbacks.)

- [ ] **Step 3: Delete broken/junk sources**

```bash
git rm -q patches/CheckSyntax.kt patches/build_temp.gradle.kts \
  patches/src/main/kotlin/app/hevy/patches/packagerename/PackageRenamePatch.kt \
  patches/src/main/kotlin/app/hevy/patches/hermespaywall/HermesPaywallPatch.kt
find . -name ".DS_Store" -type f -not -path "./.git/*" -delete
```

- [ ] **Step 4: Reset Patches.kt** to:

```kotlin
package app.hevy.patches

import app.morphe.patcher.patch.Patch

val allPatches = listOf<Patch<*>>()
```

- [ ] **Step 5: Fix patches/build.gradle.kts about block** — replace the whole `patches { about { ... } }` block with:

```kotlin
patches {
    about {
        name = "Hevy Pro Patches"
        description = "Patches for Hevy - Gym Log Workout Tracker"
        source = "https://github.com/RaymondSalim/morphe-patches"
        author = "RaymondSalim"
        contact = "https://github.com/RaymondSalim"
        website = "https://github.com/RaymondSalim/morphe-patches"
        license = "GPLv3"
    }
}
```

(Keep the rest of the file — `group = "app.hevy"`, `kotlin { compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") } }`, the `patchListGeneratorClasspath` config, dependencies, and tasks — unchanged.)

- [ ] **Step 6: Append `.DS_Store` to `.gitignore`** (one line: `.DS_Store`).

- [ ] **Step 7: Fix issue templates.** Run `grep -rn "TEMPLATE" .github/ISSUE_TEMPLATE/`. In every hit, replace:
  - `TEMPlATE/TEMPLATE-patches-template` and `TEMPLATE/template-patches-template` owner/name fragments so URLs become `https://github.com/RaymondSalim/morphe-patches/issues?q=...` and `.../blob/main/CONTRIBUTING.md`
  - comment headings like `# Template Patches template feature request` → `# Hevy Pro Patches feature request`, `# Patches template bug report` → `# Hevy Pro Patches bug report`
  - `UserXYZ Patches`-style names → `Hevy Pro Patches`
  Read each file first; do a full-file replacement pass. If `config.yml` contains template-repo contact links, point them at `RaymondSalim/morphe-patches` or drop the entry.

- [ ] **Step 8: Compile-verify the baseline**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :patches:buildAndroid --no-daemon
```

Expected: BUILD SUCCESSFUL and `patches/build/libs/patches-*.mpp` produced.

- [ ] **Step 9: Commit**

```bash
git add -A -- ':!README.md'
git commit -m "chore: Clean up template remnants and restore build configuration"
```

(README.md is deliberately left uncommitted here; Task 7 replaces it wholesale.)

---

### Task 2: Compatibility constants

**Files:**
- Modify: `patches/src/main/kotlin/app/hevy/patches/shared/Constants.kt` (full rewrite)

**Interfaces:**
- Produces: `Constants.COMPATIBILITY_HEVY_APKM`, `Constants.COMPATIBILITY_HEVY_APK : Compatibility` — consumed by both patches in Task 5 via `compatibleWith(...)`.

- [ ] **Step 1: Replace Constants.kt** with:

```kotlin
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
            AppTarget(version = "3.0.11")
        )
    )

    val COMPATIBILITY_HEVY_APK = Compatibility(
        name = "Hevy - Gym Log Workout Tracker",
        packageName = "com.hevy",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x1C1C1E,
        targets = listOf(
            AppTarget(version = "3.0.11")
        )
    )
}
```

- [ ] **Step 2: Compile-verify**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :patches:buildAndroid --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add patches/src/main/kotlin/app/hevy/patches/shared/Constants.kt
git commit -m "chore: Update Hevy compatibility targets"
```

---

### Task 3: Hermes v96 opcode table + bundle parser

**Files:**
- Create: `patches/src/main/kotlin/app/hevy/patches/hermespaywall/hermes/HermesOpcodes.kt`
- Create: `patches/src/main/kotlin/app/hevy/patches/hermespaywall/hermes/HermesBundle.kt`
- Modify: `patches/build.gradle.kts` (add `testImplementation(kotlin("test"))`)
- Test: `patches/src/test/kotlin/app/hevy/patches/hermespaywall/hermes/HermesBundleTest.kt`

**Interfaces:**
- Produces (consumed by Task 4):
  - `HermesBundle.parse(data: ByteArray): HermesBundle` (throws on invalid/unsupported input)
  - `bundle.version: Int`, `bundle.functions: List<HermesBundle.Function>`
  - `bundle.string(id: Int): String`, `bundle.stringId(value: String): Int`
  - `bundle.referencedStrings(function: Function): Set<String>`
  - `bundle.findFunctions(referencingAllOf: Set<String>): List<Function>`
  - `bundle.updateSha1Trailer()`
  - `HermesOpcodes.TABLE: Array<Opcode>` where `Opcode(size: Int, stringOperands: List<StringOperand>)`, `StringOperand(offset: Int, width: Int)`; constants `LOAD_CONST_TRUE=0x78`, `LOAD_CONST_FALSE=0x79`, `RET=0x5C`, `LOAD_THIS_NS=0x7C`, `GET_ENVIRONMENT=0x29`, `PUT_NEW_OWN_BY_ID=0x40`, `JMP_TRUE=0x90`.

- [ ] **Step 1: Add the test dependency** — in `patches/build.gradle.kts`, inside the existing `dependencies { ... }` block, add:

```kotlin
    testImplementation(kotlin("test"))
```

- [ ] **Step 2: Write the failing test** `patches/src/test/kotlin/app/hevy/patches/hermespaywall/hermes/HermesBundleTest.kt`:

```kotlin
package app.hevy.patches.hermespaywall.hermes

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assumeTrue
import kotlin.test.assertTrue

class HermesBundleTest {

    private fun originalBundle(): ByteArray? =
        System.getenv("HEVY_TEST_ORIGINAL_BUNDLE")?.let { File(it).readBytes() }

    @Test
    fun parsesHermesV96Bundle() {
        val data = originalBundle() ?: return assumeTrue(false, "HEVY_TEST_ORIGINAL_BUNDLE not set")
        val bundle = HermesBundle.parse(data)
        assertEquals(96, bundle.version)
        assertEquals(63609, bundle.functions.size)
        assertEquals("isProStatusOverrideEnabled", bundle.string(62946))
        assertEquals(62946, bundle.stringId("isProStatusOverrideEnabled"))
    }

    @Test
    fun locatesPaywallFunctionsByStringSignatures() {
        val data = originalBundle() ?: return assumeTrue(false, "HEVY_TEST_ORIGINAL_BUNDLE not set")
        val bundle = HermesBundle.parse(data)

        val proStatusGetter = bundle.findFunctions(setOf("getProStatusOverride", "force-pro", "force-free"))
        assertEquals(1, proStatusGetter.size)
        assertEquals(0x1063393, proStatusGetter[0].offset)
        assertEquals(127, proStatusGetter[0].size)

        val graceGetter = bundle.findFunctions(setOf("lastSuccessfulFetch", "lastFetchAt", "expiresAt"))
        assertEquals(1, graceGetter.size)
        assertEquals(0x106357B, graceGetter[0].offset)
        assertEquals(150, graceGetter[0].size)

        val resetCandidates = bundle.findFunctions(
            setOf("HEVY_PRO_DISK_STORAGE_KEY", "HEVY_PRO_LAST_SUCCESSFUL_FETCH")
        )
        assertTrue(resetCandidates.isNotEmpty())
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
HEVY_TEST_ORIGINAL_BUNDLE=/Users/rsalim/personal/hevy-diff/work/original_apktool/assets/index.android.bundle \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :patches:test --no-daemon
```

Expected: compilation FAILS (`HermesBundle`, `HermesOpcodes` unresolved).

- [ ] **Step 4: Write `HermesOpcodes.kt`** — content is generated from the hermes-dec v96 opcode definitions (206 opcodes, indices 0x00..0xCD; `StringOperand(offset, width)` gives the absolute byte offset of the operand inside the instruction, counting the 1-byte opcode):

```kotlin
package app.hevy.patches.hermespaywall.hermes

/**
 * Hermes bytecode version 96 opcode table.
 *
 * [TABLE] is indexed by opcode value. [Opcode.size] is the fixed instruction
 * size in bytes (1 for the opcode byte itself + operand bytes). String-id
 * operands are listed with their absolute offset within the instruction and
 * their integer width.
 */
internal class Opcode internal constructor(
    val size: Int,
    val stringOperands: List<StringOperand>,
)

internal class StringOperand internal constructor(
    val offset: Int,
    val width: Int,
)

internal object HermesOpcodes {
    const val GET_ENVIRONMENT = 0x29
    const val PUT_NEW_OWN_BY_ID = 0x40
    const val RET = 0x5C
    const val LOAD_CONST_TRUE = 0x78
    const val LOAD_CONST_FALSE = 0x79
    const val LOAD_THIS_NS = 0x7C
    const val JMP_TRUE = 0x90

    val TABLE: Array<Opcode> = arrayOf(
        // ---- contents inserted verbatim in Step 5 ----
    )
}
```

- [ ] **Step 5: Insert the 206-entry table** as the body of `TABLE = arrayOf(...)`. The exact generated content (index = opcode):

```kotlin
        Opcode(1, emptyList()),   // 0x00 Unreachable
        Opcode(10, emptyList()),  // 0x01 NewObjectWithBuffer
        Opcode(14, emptyList()),  // 0x02 NewObjectWithBufferLong
        Opcode(2, emptyList()),   // 0x03 NewObject
        Opcode(3, emptyList()),   // 0x04 NewObjectWithParent
        Opcode(8, emptyList()),   // 0x05 NewArrayWithBuffer
        Opcode(10, emptyList()),  // 0x06 NewArrayWithBufferLong
        Opcode(4, emptyList()),   // 0x07 NewArray
        Opcode(3, emptyList()),   // 0x08 Mov
        Opcode(9, emptyList()),   // 0x09 MovLong
        Opcode(3, emptyList()),   // 0x0A Negate
        Opcode(3, emptyList()),   // 0x0B Not
        Opcode(3, emptyList()),   // 0x0C BitNot
        Opcode(3, emptyList()),   // 0x0D TypeOf
        Opcode(4, emptyList()),   // 0x0E Eq
        Opcode(4, emptyList()),   // 0x0F StrictEq
        Opcode(4, emptyList()),   // 0x10 Neq
        Opcode(4, emptyList()),   // 0x11 StrictNeq
        Opcode(4, emptyList()),   // 0x12 Less
        Opcode(4, emptyList()),   // 0x13 LessEq
        Opcode(4, emptyList()),   // 0x14 Greater
        Opcode(4, emptyList()),   // 0x15 GreaterEq
        Opcode(4, emptyList()),   // 0x16 Add
        Opcode(4, emptyList()),   // 0x17 AddN
        Opcode(4, emptyList()),   // 0x18 Mul
        Opcode(4, emptyList()),   // 0x19 MulN
        Opcode(4, emptyList()),   // 0x1A Div
        Opcode(4, emptyList()),   // 0x1B DivN
        Opcode(4, emptyList()),   // 0x1C Mod
        Opcode(4, emptyList()),   // 0x1D Sub
        Opcode(4, emptyList()),   // 0x1E SubN
        Opcode(4, emptyList()),   // 0x1F LShift
        Opcode(4, emptyList()),   // 0x20 RShift
        Opcode(4, emptyList()),   // 0x21 URshift
        Opcode(4, emptyList()),   // 0x22 BitAnd
        Opcode(4, emptyList()),   // 0x23 BitXor
        Opcode(4, emptyList()),   // 0x24 BitOr
        Opcode(3, emptyList()),   // 0x25 Inc
        Opcode(3, emptyList()),   // 0x26 Dec
        Opcode(4, emptyList()),   // 0x27 InstanceOf
        Opcode(4, emptyList()),   // 0x28 IsIn
        Opcode(3, emptyList()),   // 0x29 GetEnvironment
        Opcode(4, emptyList()),   // 0x2A StoreToEnvironment
        Opcode(5, emptyList()),   // 0x2B StoreToEnvironmentL
        Opcode(4, emptyList()),   // 0x2C StoreNPToEnvironment
        Opcode(5, emptyList()),   // 0x2D StoreNPToEnvironmentL
        Opcode(4, emptyList()),   // 0x2E LoadFromEnvironment
        Opcode(5, emptyList()),   // 0x2F LoadFromEnvironmentL
        Opcode(2, emptyList()),   // 0x30 GetGlobalObject
        Opcode(2, emptyList()),   // 0x31 GetNewTarget
        Opcode(2, emptyList()),   // 0x32 CreateEnvironment
        Opcode(7, emptyList()),   // 0x33 CreateInnerEnvironment
        Opcode(5, listOf(StringOperand(1, 4))),  // 0x34 DeclareGlobalVar
        Opcode(5, listOf(StringOperand(1, 4))),  // 0x35 ThrowIfHasRestrictedGlobalProperty
        Opcode(5, listOf(StringOperand(4, 1))),  // 0x36 GetByIdShort
        Opcode(6, listOf(StringOperand(4, 2))),  // 0x37 GetById
        Opcode(8, listOf(StringOperand(4, 4))),  // 0x38 GetByIdLong
        Opcode(6, listOf(StringOperand(4, 2))),  // 0x39 TryGetById
        Opcode(8, listOf(StringOperand(4, 4))),  // 0x3A TryGetByIdLong
        Opcode(6, listOf(StringOperand(4, 2))),  // 0x3B PutById
        Opcode(8, listOf(StringOperand(4, 4))),  // 0x3C PutByIdLong
        Opcode(6, listOf(StringOperand(4, 2))),  // 0x3D TryPutById
        Opcode(8, listOf(StringOperand(4, 4))),  // 0x3E TryPutByIdLong
        Opcode(4, listOf(StringOperand(3, 1))),  // 0x3F PutNewOwnByIdShort
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x40 PutNewOwnById
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x41 PutNewOwnByIdLong
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x42 PutNewOwnNEById
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x43 PutNewOwnNEByIdLong
        Opcode(4, emptyList()),   // 0x44 PutOwnByIndex
        Opcode(7, emptyList()),   // 0x45 PutOwnByIndexL
        Opcode(5, emptyList()),   // 0x46 PutOwnByVal
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x47 DelById
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x48 DelByIdLong
        Opcode(4, emptyList()),   // 0x49 GetByVal
        Opcode(4, emptyList()),   // 0x4A PutByVal
        Opcode(4, emptyList()),   // 0x4B DelByVal
        Opcode(6, emptyList()),   // 0x4C PutOwnGetterSetterByVal
        Opcode(5, emptyList()),   // 0x4D GetPNameList
        Opcode(6, emptyList()),   // 0x4E GetNextPName
        Opcode(4, emptyList()),   // 0x4F Call
        Opcode(4, emptyList()),   // 0x50 Construct
        Opcode(4, emptyList()),   // 0x51 Call1
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x52 CallDirect
        Opcode(5, emptyList()),   // 0x53 Call2
        Opcode(6, emptyList()),   // 0x54 Call3
        Opcode(7, emptyList()),   // 0x55 Call4
        Opcode(7, emptyList()),   // 0x56 CallLong
        Opcode(7, emptyList()),   // 0x57 ConstructLong
        Opcode(7, emptyList()),   // 0x58 CallDirectLongIndex
        Opcode(4, emptyList()),   // 0x59 CallBuiltin
        Opcode(7, emptyList()),   // 0x5A CallBuiltinLong
        Opcode(3, emptyList()),   // 0x5B GetBuiltinClosure
        Opcode(2, emptyList()),   // 0x5C Ret
        Opcode(2, emptyList()),   // 0x5D Catch
        Opcode(4, emptyList()),   // 0x5E DirectEval
        Opcode(2, emptyList()),   // 0x5F Throw
        Opcode(3, emptyList()),   // 0x60 ThrowIfEmpty
        Opcode(1, emptyList()),   // 0x61 Debugger
        Opcode(1, emptyList()),   // 0x62 AsyncBreakCheck
        Opcode(3, emptyList()),   // 0x63 ProfilePoint
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x64 CreateClosure
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x65 CreateClosureLongIndex
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x66 CreateGeneratorClosure
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x67 CreateGeneratorClosureLongIndex
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x68 CreateAsyncClosure
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x69 CreateAsyncClosureLongIndex
        Opcode(4, emptyList()),   // 0x6A CreateThis
        Opcode(4, emptyList()),   // 0x6B SelectObject
        Opcode(3, emptyList()),   // 0x6C LoadParam
        Opcode(6, emptyList()),   // 0x6D LoadParamLong
        Opcode(3, emptyList()),   // 0x6E LoadConstUInt8
        Opcode(6, emptyList()),   // 0x6F LoadConstInt
        Opcode(10, emptyList()),  // 0x70 LoadConstDouble
        Opcode(4, listOf(StringOperand(2, 2))),  // 0x71 LoadConstBigInt
        Opcode(6, listOf(StringOperand(2, 4))),  // 0x72 LoadConstBigIntLongIndex
        Opcode(4, listOf(StringOperand(2, 2))),  // 0x73 LoadConstString
        Opcode(6, listOf(StringOperand(2, 4))),  // 0x74 LoadConstStringLongIndex
        Opcode(2, emptyList()),   // 0x75 LoadConstEmpty
        Opcode(2, emptyList()),   // 0x76 LoadConstUndefined
        Opcode(2, emptyList()),   // 0x77 LoadConstNull
        Opcode(2, emptyList()),   // 0x78 LoadConstTrue
        Opcode(2, emptyList()),   // 0x79 LoadConstFalse
        Opcode(2, emptyList()),   // 0x7A LoadConstZero
        Opcode(3, emptyList()),   // 0x7B CoerceThisNS
        Opcode(2, emptyList()),   // 0x7C LoadThisNS
        Opcode(3, emptyList()),   // 0x7D ToNumber
        Opcode(3, emptyList()),   // 0x7E ToNumeric
        Opcode(3, emptyList()),   // 0x7F ToInt32
        Opcode(3, emptyList()),   // 0x80 AddEmptyString
        Opcode(4, emptyList()),   // 0x81 GetArgumentsPropByVal
        Opcode(3, emptyList()),   // 0x82 GetArgumentsLength
        Opcode(2, emptyList()),   // 0x83 ReifyArguments
        Opcode(14, listOf(StringOperand(2, 4), StringOperand(6, 4))),  // 0x84 CreateRegExp
        Opcode(18, emptyList()),  // 0x85 SwitchImm
        Opcode(1, emptyList()),   // 0x86 StartGenerator
        Opcode(3, emptyList()),   // 0x87 ResumeGenerator
        Opcode(1, emptyList()),   // 0x88 CompleteGenerator
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x89 CreateGenerator
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x8A CreateGeneratorLongIndex
        Opcode(3, emptyList()),   // 0x8B IteratorBegin
        Opcode(4, emptyList()),   // 0x8C IteratorNext
        Opcode(3, emptyList()),   // 0x8D IteratorClose
        Opcode(2, emptyList()),   // 0x8E Jmp
        Opcode(5, emptyList()),   // 0x8F JmpLong
        Opcode(3, emptyList()),   // 0x90 JmpTrue
        Opcode(6, emptyList()),   // 0x91 JmpTrueLong
        Opcode(3, emptyList()),   // 0x92 JmpFalse
        Opcode(6, emptyList()),   // 0x93 JmpFalseLong
        Opcode(3, emptyList()),   // 0x94 JmpUndefined
        Opcode(6, emptyList()),   // 0x95 JmpUndefinedLong
        Opcode(2, emptyList()),   // 0x96 SaveGenerator
        Opcode(5, emptyList()),   // 0x97 SaveGeneratorLong
        Opcode(4, emptyList()),   // 0x98 JLess
        Opcode(7, emptyList()),   // 0x99 JLessLong
        Opcode(4, emptyList()),   // 0x9A JNotLess
        Opcode(7, emptyList()),   // 0x9B JNotLessLong
        Opcode(4, emptyList()),   // 0x9C JLessN
        Opcode(7, emptyList()),   // 0x9D JLessNLong
        Opcode(4, emptyList()),   // 0x9E JNotLessN
        Opcode(7, emptyList()),   // 0x9F JNotLessNLong
        Opcode(4, emptyList()),   // 0xA0 JLessEqual
        Opcode(7, emptyList()),   // 0xA1 JLessEqualLong
        Opcode(4, emptyList()),   // 0xA2 JNotLessEqual
        Opcode(7, emptyList()),   // 0xA3 JNotLessEqualLong
        Opcode(4, emptyList()),   // 0xA4 JLessEqualN
        Opcode(7, emptyList()),   // 0xA5 JLessEqualNLong
        Opcode(4, emptyList()),   // 0xA6 JNotLessEqualN
        Opcode(7, emptyList()),   // 0xA7 JNotLessEqualNLong
        Opcode(4, emptyList()),   // 0xA8 JGreater
        Opcode(7, emptyList()),   // 0xA9 JGreaterLong
        Opcode(4, emptyList()),   // 0xAA JNotGreater
        Opcode(7, emptyList()),   // 0xAB JNotGreaterLong
        Opcode(4, emptyList()),   // 0xAC JGreaterN
        Opcode(7, emptyList()),   // 0xAD JGreaterNLong
        Opcode(4, emptyList()),   // 0xAE JNotGreaterN
        Opcode(7, emptyList()),   // 0xAF JNotGreaterNLong
        Opcode(4, emptyList()),   // 0xB0 JGreaterEqual
        Opcode(7, emptyList()),   // 0xB1 JGreaterEqualLong
        Opcode(4, emptyList()),   // 0xB2 JNotGreaterEqual
        Opcode(7, emptyList()),   // 0xB3 JNotGreaterEqualLong
        Opcode(4, emptyList()),   // 0xB4 JGreaterEqualN
        Opcode(7, emptyList()),   // 0xB5 JGreaterEqualNLong
        Opcode(4, emptyList()),   // 0xB6 JNotGreaterEqualN
        Opcode(7, emptyList()),   // 0xB7 JNotGreaterEqualNLong
        Opcode(4, emptyList()),   // 0xB8 JEqual
        Opcode(7, emptyList()),   // 0xB9 JEqualLong
        Opcode(4, emptyList()),   // 0xBA JNotEqual
        Opcode(7, emptyList()),   // 0xBB JNotEqualLong
        Opcode(4, emptyList()),   // 0xBC JStrictEqual
        Opcode(7, emptyList()),   // 0xBD JStrictEqualLong
        Opcode(4, emptyList()),   // 0xBE JStrictNotEqual
        Opcode(7, emptyList()),   // 0xBF JStrictNotEqualLong
        Opcode(4, emptyList()),   // 0xC0 Add32
        Opcode(4, emptyList()),   // 0xC1 Sub32
        Opcode(4, emptyList()),   // 0xC2 Mul32
        Opcode(4, emptyList()),   // 0xC3 Divi32
        Opcode(4, emptyList()),   // 0xC4 Divu32
        Opcode(4, emptyList()),   // 0xC5 Loadi8
        Opcode(4, emptyList()),   // 0xC6 Loadu8
        Opcode(4, emptyList()),   // 0xC7 Loadi16
        Opcode(4, emptyList()),   // 0xC8 Loadu16
        Opcode(4, emptyList()),   // 0xC9 Loadi32
        Opcode(4, emptyList()),   // 0xCA Loadu32
        Opcode(4, emptyList()),   // 0xCB Store8
        Opcode(4, emptyList()),   // 0xCC Store16
        Opcode(4, emptyList()),   // 0xCD Store32
```

(Sanity anchors: index 0x29 = size 3; 0x40 = size 5 with `StringOperand(3, 2)`; 0x5C/0x78/0x79/0x7C = size 2; 0x90 = size 3; 0x84 = size 14 with two u32 string operands at offsets 2 and 6; total entries = 206 = 0xCE.)

- [ ] **Step 6: Write `HermesBundle.kt`**:

```kotlin
package app.hevy.patches.hermespaywall.hermes

import java.security.MessageDigest

/**
 * Minimal parser for Hermes bytecode files (bytecode version 96).
 *
 * Parses only what is needed to locate functions by the string constants they
 * reference: the file header, the function header table, and the string
 * tables. The bundle is parsed against a mutable byte array so callers can
 * patch bytes in place and recompute the trailing SHA-1.
 */
class HermesBundle private constructor(
    private val data: ByteArray,
) {
    val version: Int

    val functions: List<Function>

    class Function internal constructor(
        val id: Int,
        val offset: Int,
        val size: Int,
    )

    private val stringCount: Int
    private val stringUtf16: BooleanArray
    private val stringOffsets: IntArray
    private val stringLengths: IntArray
    private val overflowOffsets: IntArray
    private val overflowLengths: IntArray
    private val stringStorageBase: Int
    private val stringCache = HashMap<Int, String>()
    private var reverseIndex: Map<String, Int>? = null

    init {
        require(data.size >= 128) { "Hermes bundle too small: ${data.size} bytes" }
        require(readU32(0) == 0xC61FBC03.toInt() && readU32(4) == 0xC103191F.toInt()) {
            "Invalid Hermes magic"
        }
        version = readU32(8)
        require(version == 96) { "Unsupported Hermes bytecode version: $version (expected 96)" }

        val functionCount = readU32(32 + 4 * 2)
        val stringKindCount = readU32(32 + 4 * 3)
        val identifierCount = readU32(32 + 4 * 4)
        stringCount = readU32(32 + 4 * 5)
        val overflowStringCount = readU32(32 + 4 * 6)
        val stringStorageSize = readU32(32 + 4 * 7)

        // Function headers: contiguous 16-byte slots directly after the padded header.
        val functionsStart = align(32 + 4 * 19 + 1, 32)
        val functionHeadersEnd = functionsStart + functionCount * SMALL_HEADER_SIZE
        require(functionHeadersEnd <= data.size) { "Function header table exceeds file length" }

        val functionList = ArrayList<Function>(functionCount)
        for (i in 0 until functionCount) {
            val headerOffset = functionsStart + i * SMALL_HEADER_SIZE
            val word0 = readU32(headerOffset)
            val word1 = readU32(headerOffset + 4)
            val word2 = readU32(headerOffset + 8)
            val flags = data[headerOffset + 15].toInt() and 0xFF
            var offset = word0 and 0x1FFFFFF
            var size = word1 and 0x7FFF
            if (flags and FLAG_OVERFLOWED != 0) {
                // The large header lives at (infoOffset << 16) | offset.
                val infoOffset = word2 and 0x1FFFFFF
                val largeOffset = (infoOffset.toLong() shl 16) or offset.toLong()
                require(largeOffset + 12 <= data.size) {
                    "Overflowed function header for function $i out of range"
                }
                offset = readU32(largeOffset.toInt())
                size = readU32(largeOffset.toInt() + 8)
            }
            require(offset >= 0 && size >= 0 && offset + size <= data.size) {
                "Function $i body out of range: offset=$offset size=$size"
            }
            functionList.add(Function(i, offset, size))
        }
        functions = functionList

        // String kind entries, identifier hashes, string tables, string storage.
        var cursor = align(functionHeadersEnd, 4)
        cursor = align(cursor + stringKindCount * 4, 4)
        cursor = align(cursor + identifierCount * 4, 4)
        require(cursor + stringCount * 4 <= data.size) { "String table exceeds file length" }
        stringUtf16 = BooleanArray(stringCount)
        stringOffsets = IntArray(stringCount)
        stringLengths = IntArray(stringCount)
        for (i in 0 until stringCount) {
            val entry = readU32(cursor + i * 4)
            stringUtf16[i] = entry and 1 != 0
            stringOffsets[i] = (entry shr 1) and 0x7FFFFF
            stringLengths[i] = (entry shr 24) and 0xFF
        }
        cursor = align(cursor + stringCount * 4, 4)
        overflowOffsets = IntArray(overflowStringCount)
        overflowLengths = IntArray(overflowStringCount)
        for (i in 0 until overflowStringCount) {
            overflowOffsets[i] = readU32(cursor + i * 8)
            overflowLengths[i] = readU32(cursor + i * 8 + 4)
        }
        cursor = align(cursor + overflowStringCount * 8, 4)
        require(cursor + stringStorageSize <= data.size) { "String storage exceeds file length" }
        stringStorageBase = cursor
    }

    /** Decode the string with the given id. */
    fun string(id: Int): String {
        stringCache[id]?.let { return it }
        require(id in 0 until stringCount) { "String id out of range: $id" }
        var offset = stringOffsets[id]
        var length = stringLengths[id]
        if (length == 0xFF) {
            offset = overflowOffsets[offset]
            length = overflowLengths[offset]
        }
        val raw = data.copyOfRange(stringStorageBase + offset, stringStorageBase + offset + length)
        val decoded = if (stringUtf16[id]) {
            String(raw, Charsets.UTF_16LE)
        } else {
            String(raw, Charsets.ISO_8859_1)
        }
        stringCache[id] = decoded
        return decoded
    }

    /** Look up the first string id whose value equals [value]. */
    fun stringId(value: String): Int {
        reverseIndex?.get(value)?.let { return it }
        val index = HashMap<String, Int>()
        for (i in 0 until stringCount) {
            index.putIfAbsent(string(i), i)
        }
        reverseIndex = index
        return index[value] ?: throw IllegalArgumentException("String not in bundle: \"$value\"")
    }

    /** All string values referenced by [function]'s instruction stream. */
    fun referencedStrings(function: Function): Set<String> {
        val referenced = mutableSetOf<String>()
        var ip = 0
        while (ip < function.size) {
            val absolute = function.offset + ip
            val opcodeValue = data[absolute].toInt() and 0xFF
            val opcode = TABLE_INDEX[opcodeValue]
                ?: throw IllegalStateException(
                    "Unknown Hermes opcode 0x${opcodeValue.toString(16)} in function ${function.id}"
                )
            for (operand in opcode.stringOperands) {
                val operandAbsolute = absolute + operand.offset
                // Read as Long so high-bit u16/u32 ids never wrap negative.
                val stringIdValue = when (operand.width) {
                    1 -> (data[operandAbsolute].toLong() and 0xFF)
                    2 -> (data[operandAbsolute].toLong() and 0xFF) or
                        ((data[operandAbsolute + 1].toLong() and 0xFF) shl 8)
                    4 -> (data[operandAbsolute].toLong() and 0xFF) or
                        ((data[operandAbsolute + 1].toLong() and 0xFF) shl 8) or
                        ((data[operandAbsolute + 2].toLong() and 0xFF) shl 16) or
                        ((data[operandAbsolute + 3].toLong() and 0xFF) shl 24)
                    else -> throw IllegalStateException("Unsupported string operand width")
                }
                require(stringIdValue < stringCount) {
                    "String id out of range in function ${function.id}: $stringIdValue"
                }
                referenced.add(string(stringIdValue.toInt()))
            }
            ip += opcode.size
        }
        require(ip == function.size) {
            "Instruction stream overran function ${function.id} body"
        }
        return referenced
    }

    /** Functions whose referenced strings contain every value in [referencingAllOf]. */
    fun findFunctions(referencingAllOf: Set<String>): List<Function> =
        functions.filter { referencedStrings(it).containsAll(referencingAllOf) }

    /**
     * Recompute the SHA-1 trailer (last 20 bytes) over the rest of the bundle.
     * Bytecode edits invalidate the original trailer.
     */
    fun updateSha1Trailer() {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(data.copyOfRange(0, data.size - 20))
        System.arraycopy(digest, 0, data, data.size - 20, 20)
    }

    private fun readU32(offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

    private fun align(value: Int, alignment: Int): Int =
        (value + alignment - 1) / alignment * alignment

    companion object {
        private const val SMALL_HEADER_SIZE = 16
        private const val FLAG_OVERFLOWED = 0x20

        private val TABLE_INDEX = HermesOpcodes.TABLE

        fun parse(data: ByteArray): HermesBundle = HermesBundle(data)
    }
}
```

Note: `TABLE_INDEX` is a companion-level alias so `HermesOpcodes` stays `internal` while `HermesBundle` is public.

- [ ] **Step 7: Run the tests to verify they pass**

```bash
HEVY_TEST_ORIGINAL_BUNDLE=/Users/rsalim/personal/hevy-diff/work/original_apktool/assets/index.android.bundle \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :patches:test --no-daemon
```

Expected: `parsesHermesV96Bundle` and `locatesPaywallFunctionsByStringSignatures` PASS (several seconds; the scanner walks ~63k functions). If a test fails on `stringId` reverse lookup or offsets, re-check the parse math against the "Validated ground truth" section before changing anything.

- [ ] **Step 8: Commit**

```bash
git add patches/src/main/kotlin/app/hevy/patches/hermespaywall/hermes/ patches/src/test patches/build.gradle.kts
git commit -m "feat: Add Hermes v96 bytecode parser"
```

---

### Task 4: Paywall edit logic + golden test

**Files:**
- Create: `patches/src/main/kotlin/app/hevy/patches/hermespaywall/hermes/PaywallMod.kt`
- Test: `patches/src/test/kotlin/app/hevy/patches/hermespaywall/hermes/PaywallModGoldenTest.kt`

**Interfaces:**
- Consumes: everything from Task 3 (`HermesBundle`, `HermesOpcodes`).
- Produces: `PaywallMod.apply(data: ByteArray): Int` — applies the 3 paywall edits in place, recomputes the SHA-1 trailer, returns the number of edits applied (always 3), throws if any site is missing or bytes mismatch. Consumed by Task 5 (`hermesPaywallPatch`) and Task 6 (e2e).

- [ ] **Step 1: Write the failing golden test** `patches/src/test/kotlin/app/hevy/patches/hermespaywall/hermes/PaywallModGoldenTest.kt`:

```kotlin
package app.hevy.patches.hermespaywall.hermes

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assumeTrue

class PaywallModGoldenTest {

    @Test
    fun reproducesTheKnownGoodModdedBundle() {
        val originalPath = System.getenv("HEVY_TEST_ORIGINAL_BUNDLE")
        val moddedPath = System.getenv("HEVY_TEST_MODDED_BUNDLE")
        assumeTrue(originalPath != null && moddedPath != null, "bundle env vars not set")

        val original = File(originalPath).readBytes()
        val modded = File(moddedPath).readBytes()

        val data = original.copyOf()
        val applied = PaywallMod.apply(data)
        assertEquals(3, applied)
        assertEquals(modded.size, data.size)

        // Every byte except the 20-byte SHA-1 trailer must match the known-good
        // manual mod exactly.
        for (i in 0 until data.size - 20) {
            if (data[i] != modded[i]) {
                throw AssertionError("Byte mismatch at offset 0x${i.toString(16)}")
            }
        }

        // Our trailer is the correct SHA-1 of the patched content. The manual
        // mod left the trailer stale, so it must NOT match the modded file.
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(data.copyOfRange(0, data.size - 20))
        assertContentEquals(digest, data.copyOfRange(data.size - 20, data.size))
    }

    @Test
    fun patchesAreIdempotentSafe() {
        // A second run must fail loudly instead of double-patching.
        val originalPath = System.getenv("HEVY_TEST_ORIGINAL_BUNDLE")
        assumeTrue(originalPath != null, "HEVY_TEST_ORIGINAL_BUNDLE not set")
        val data = File(originalPath).readBytes()
        PaywallMod.apply(data)
        try {
            PaywallMod.apply(data)
            throw AssertionError("Second apply should have failed")
        } catch (expected: IllegalStateException) {
            // expected
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
HEVY_TEST_ORIGINAL_BUNDLE=/Users/rsalim/personal/hevy-diff/work/original_apktool/assets/index.android.bundle \
HEVY_TEST_MODDED_BUNDLE=/Users/rsalim/personal/hevy-diff/work/modded_apktool/assets/index.android.bundle \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :patches:test --no-daemon
```

Expected: compilation FAILS (`PaywallMod` unresolved).

- [ ] **Step 3: Write `PaywallMod.kt`**:

```kotlin
package app.hevy.patches.hermespaywall.hermes

/**
 * Applies the Hevy Pro unlock edits to a Hermes bytecode bundle:
 *
 * 1. The pro-state reset generator (references HEVY_PRO_* storage keys) stores
 *    `subscription.is_pro = false`; flip its LoadConstFalse to LoadConstTrue.
 * 2. The isPro status getter (references getProStatusOverride / force-pro /
 *    force-free) gets its prologue rewritten to `return true`.
 * 3. The offline grace-period getter (references lastSuccessfulFetch /
 *    lastFetchAt / expiresAt) has its terminal LoadConstFalse flipped to
 *    LoadConstTrue.
 *
 * Each site is located by string references and validated against expected
 * bytes before editing. If anything does not match, this object throws so the
 * patch fails loudly instead of producing a broken app.
 */
object PaywallMod {

    private val PRO_STATE_RESET_STRINGS = setOf(
        "HEVY_PRO_DISK_STORAGE_KEY",
        "HEVY_PRO_LAST_SUCCESSFUL_FETCH",
    )
    private val PRO_STATUS_GETTER_STRINGS = setOf(
        "getProStatusOverride",
        "force-pro",
        "force-free",
    )
    private val GRACE_PERIOD_GETTER_STRINGS = setOf(
        "lastSuccessfulFetch",
        "lastFetchAt",
        "expiresAt",
    )
    private const val IS_PRO_STRING = "is_pro"

    /** Applies all 3 edits in place and recomputes the bundle SHA-1 trailer. */
    fun apply(data: ByteArray): Int {
        val bundle = HermesBundle.parse(data)
        var applied = 0
        applied += patchProStateReset(bundle, data)
        applied += patchProStatusGetter(bundle, data)
        applied += patchGracePeriodGetter(bundle, data)
        check(applied == 3) { "Expected to apply 3 paywall edits, applied $applied" }
        bundle.updateSha1Trailer()
        return applied
    }

    /**
     * Site 1: inside the functions referencing the Hevy Pro storage keys, find
     * the single `LoadConstFalse` immediately followed by `PutNewOwnById` with
     * the `is_pro` string, and flip it to LoadConstTrue.
     */
    private fun patchProStateReset(bundle: HermesBundle, data: ByteArray): Int {
        val candidates = bundle.findFunctions(PRO_STATE_RESET_STRINGS)
        require(candidates.isNotEmpty()) { "Pro-state reset function not found" }

        val hits = mutableListOf<Int>()
        for (candidate in candidates) {
            var ip = 0
            while (ip < candidate.size) {
                val absolute = candidate.offset + ip
                if ((data[absolute].toInt() and 0xFF) == HermesOpcodes.LOAD_CONST_FALSE) {
                    val nextOpcode = data[absolute + 2].toInt() and 0xFF
                    if (nextOpcode == HermesOpcodes.PUT_NEW_OWN_BY_ID) {
                        // PutNewOwnById: opcode(1) + reg(1) + reg(1) + u16 string id at +3.
                        val stringId = readU16(data, absolute + 2 + 3)
                        if (bundle.string(stringId) == IS_PRO_STRING) {
                            hits.add(absolute)
                        }
                    }
                }
                ip += HermesOpcodes.TABLE[data[absolute].toInt() and 0xFF].size
            }
        }
        require(hits.size == 1) {
            "Expected exactly one is_pro store site, found ${hits.size} in $candidates"
        }
        val position = hits[0]
        check((data[position].toInt() and 0xFF) == HermesOpcodes.LOAD_CONST_FALSE) {
            "Unexpected byte at is_pro store site 0x${position.toString(16)}"
        }
        data[position] = HermesOpcodes.LOAD_CONST_TRUE.toByte()
        return 1
    }

    /**
     * Site 2: rewrite the isPro getter prologue `LoadThisNS r0;
     * GetEnvironment rX, n` to `LoadConstTrue r0; Ret r0`.
     */
    private fun patchProStatusGetter(bundle: HermesBundle, data: ByteArray): Int {
        val matches = bundle.findFunctions(PRO_STATUS_GETTER_STRINGS)
        require(matches.size == 1) {
            "Expected exactly one isPro status getter, found ${matches.size}"
        }
        val getter = matches[0]
        check(getter.size >= 5) { "isPro getter too small: ${getter.size} bytes" }
        val base = getter.offset
        check((data[base].toInt() and 0xFF) == HermesOpcodes.LOAD_THIS_NS && data[base + 1].toInt() == 0) {
            "Unexpected isPro getter prologue at 0x${base.toString(16)}"
        }
        check((data[base + 2].toInt() and 0xFF) == HermesOpcodes.GET_ENVIRONMENT) {
            "Unexpected isPro getter second instruction at 0x${base.toString(16)}"
        }
        data[base] = HermesOpcodes.LOAD_CONST_TRUE.toByte()
        data[base + 1] = 0
        data[base + 2] = HermesOpcodes.RET.toByte()
        data[base + 3] = 0
        data[base + 4] = 1
        return 1
    }

    /**
     * Site 3: flip the terminal LoadConstFalse in the grace-period getter's
     * trailing return sequence `JmpTrue; LoadConstFalse rX; Ret rX;
     * LoadConstTrue rX; Ret rX` (11 bytes) to LoadConstTrue.
     */
    private fun patchGracePeriodGetter(bundle: HermesBundle, data: ByteArray): Int {
        val matches = bundle.findFunctions(GRACE_PERIOD_GETTER_STRINGS)
        require(matches.size == 1) {
            "Expected exactly one grace period getter, found ${matches.size}"
        }
        val getter = matches[0]
        val tail = getter.offset + getter.size - 11
        check(tail >= getter.offset) { "Grace period getter too small: ${getter.size} bytes" }
        check(
            (data[tail].toInt() and 0xFF) == HermesOpcodes.JMP_TRUE &&
                (data[tail + 3].toInt() and 0xFF) == HermesOpcodes.LOAD_CONST_FALSE &&
                (data[tail + 5].toInt() and 0xFF) == HermesOpcodes.RET &&
                (data[tail + 7].toInt() and 0xFF) == HermesOpcodes.LOAD_CONST_TRUE &&
                (data[tail + 9].toInt() and 0xFF) == HermesOpcodes.RET
        ) { "Unexpected grace period getter tail at 0x${tail.toString(16)}" }
        data[tail + 3] = HermesOpcodes.LOAD_CONST_TRUE.toByte()
        return 1
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
HEVY_TEST_ORIGINAL_BUNDLE=/Users/rsalim/personal/hevy-diff/work/original_apktool/assets/index.android.bundle \
HEVY_TEST_MODDED_BUNDLE=/Users/rsalim/personal/hevy-diff/work/modded_apktool/assets/index.android.bundle \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :patches:test --no-daemon
```

Expected: all tests PASS, including `reproducesTheKnownGoodModdedBundle` (byte-for-byte match with the known-good mod except the recomputed trailer). If it fails, diff the 5 changed offsets against the "Validated ground truth" section — do NOT weaken the test.

- [ ] **Step 5: Commit**

```bash
git add patches/src/main/kotlin/app/hevy/patches/hermespaywall/ patches/src/test
git commit -m "feat: Add Hermes paywall patch logic"
```

---

### Task 5: The two Morphe patches + registration

**Files:**
- Create: `patches/src/main/kotlin/app/hevy/patches/hermespaywall/HermesPaywallPatch.kt`
- Create: `patches/src/main/kotlin/app/hevy/patches/packagerename/PackageRenamePatch.kt`
- Modify: `patches/src/main/kotlin/app/hevy/patches/Patches.kt`

**Interfaces:**
- Consumes: `PaywallMod.apply(ByteArray): Int` (Task 4), `Constants.COMPATIBILITY_*` (Task 2).
- Produces: `allPatches = listOf(packageRenamePatch, hermesPaywallPatch)` — consumed by the bundle build, `util/PatchListGenerator.kt`, and Task 6's e2e test.

- [ ] **Step 1: Write `HermesPaywallPatch.kt`**:

```kotlin
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
```

- [ ] **Step 2: Compile-verify**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :patches:buildAndroid --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add patches/src/main/kotlin/app/hevy/patches/hermespaywall/HermesPaywallPatch.kt
git commit -m "feat: Added Hevy Pro paywall bypass patch"
```

- [ ] **Step 4: Write `PackageRenamePatch.kt`**:

```kotlin
package app.hevy.patches.packagerename

import app.hevy.patches.shared.Constants
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element

private const val ORIGINAL_PACKAGE = "com.hevy"

val packageRenamePatch = resourcePatch(
    name = "Package rename",
    description = "Renames the app package so the patched app can be installed alongside the original Hevy app.",
    default = true,
) {
    val packageNameOption = stringOption(
        key = "package-name",
        default = "com.hevy.mod",
        values = null,
        title = "Package name",
        description = "The new package name of the patched app. It must differ from the original package name.",
        required = true,
        validator = { value ->
            value != null && Regex("[a-zA-Z][\\w]*(?:\\.[\\w]+)+").matches(value)
        },
    )

    compatibleWith(Constants.COMPATIBILITY_HEVY_APKM, Constants.COMPATIBILITY_HEVY_APK)

    execute {
        val packageName by packageNameOption
        check(packageName != ORIGINAL_PACKAGE) {
            "Package name must differ from $ORIGINAL_PACKAGE"
        }

        // Only the manifest attributes that must stay unique per install are
        // renamed: the package itself, content provider authorities, and
        // Hevy's custom permission. Third-party permissions and class names
        // (activities, services, receivers, application) stay untouched;
        // resource-side package references are remapped by the patcher's
        // PackageRenamingProcessor when the manifest package changes.
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
                    permission.setAttribute(
                        "android:name",
                        name.replaceFirst(ORIGINAL_PACKAGE, packageName),
                    )
                }
            }

            val usesPermissions = manifest.getElementsByTagName("uses-permission")
            for (i in 0 until usesPermissions.length) {
                val usesPermission = usesPermissions.item(i) as Element
                val name = usesPermission.getAttribute("android:name")
                if (name.startsWith(ORIGINAL_PACKAGE)) {
                    usesPermission.setAttribute(
                        "android:name",
                        name.replaceFirst(ORIGINAL_PACKAGE, packageName),
                    )
                }
            }
        }

        println("Package rename: renamed $ORIGINAL_PACKAGE to $packageName")
    }
}
```

- [ ] **Step 5: Register both patches** — replace `patches/src/main/kotlin/app/hevy/patches/Patches.kt` with:

```kotlin
package app.hevy.patches

import app.hevy.patches.hermespaywall.hermesPaywallPatch
import app.hevy.patches.packagerename.packageRenamePatch

val allPatches = listOf(
    packageRenamePatch,
    hermesPaywallPatch,
)
```

- [ ] **Step 6: Compile + run unit tests**

```bash
HEVY_TEST_ORIGINAL_BUNDLE=/Users/rsalim/personal/hevy-diff/work/original_apktool/assets/index.android.bundle \
HEVY_TEST_MODDED_BUNDLE=/Users/rsalim/personal/hevy-diff/work/modded_apktool/assets/index.android.bundle \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :patches:buildAndroid :patches:test --no-daemon
```

Expected: BUILD SUCCESSFUL, all tests pass. If `stringOption`'s parameter names differ from `key/default/values/title/description/required/validator`, mirror the positional style used by patcheddit's `SpoofClientPatch.kt` (positional first six args, named `validator`).

- [ ] **Step 7: Commit**

```bash
git add patches/src/main/kotlin/app/hevy/patches/packagerename/ patches/src/main/kotlin/app/hevy/patches/Patches.kt
git commit -m "feat: Added Hevy package rename patch"
```

---

### Task 6: End-to-end verification harness

**Files:**
- Test: `patches/src/test/kotlin/app/hevy/patches/PatcherEndToEndTest.kt`
- Test: `patches/src/test/kotlin/app/hevy/patches/ManifestAssertions.kt` (shared XML assertion helper)

**Interfaces:**
- Consumes: `allPatches` (Task 5), `PaywallMod` (Task 4), patcher API (`app.morphe.patcher.Patcher`, `PatcherConfig`, `PatcherResult`).
- Produces: env-guarded e2e test that runs both patches on the real APK and proves the output equals the known-good mod. No other task depends on it.

- [ ] **Step 1: Check test classpath.** The convention plugin injects the patcher dependency into the main source set. Verify tests can see it:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :patches:dependencies --configuration testRuntimeClasspath --no-daemon | grep -i morphe
```

Expected: an `app.morphe` patcher artifact listed. If absent, add the same dependency the main source set uses (find it with `./gradlew :patches:dependencies --configuration compileClasspath`) as `testImplementation(...)` in `patches/build.gradle.kts`.

- [ ] **Step 2: Write `ManifestAssertions.kt`**:

```kotlin
package app.hevy.patches

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Assertions for the package-renamed AndroidManifest.xml as decoded by
 * apktool from the patched APK.
 */
object ManifestAssertions {

    fun assertRenamedManifest(manifestFile: File) {
        val document: Document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(manifestFile)
        val root = document.documentElement as Element

        assertEquals("com.hevy.mod", root.getAttribute("package"))

        val providerAuthorities = collectAttributes(document, "provider", "android:authorities")
        assertTrue(
            providerAuthorities.any { it.startsWith("com.hevy.mod.") },
            "Expected renamed provider authorities, got: $providerAuthorities",
        )
        assertFalse(
            providerAuthorities.any { it.startsWith("com.hevy.") && !it.startsWith("com.hevy.mod.") },
            "Found un-renamed com.hevy.* authorities: $providerAuthorities",
        )

        val permissionNames = collectAttributes(document, "permission", "android:name") +
            collectAttributes(document, "uses-permission", "android:name")
        assertTrue(
            permissionNames.any { it == "com.hevy.mod.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" },
            "Custom permission was not renamed",
        )
        assertFalse(
            permissionNames.any { it.startsWith("com.hevy.") && !it.startsWith("com.hevy.mod.") },
            "Found un-renamed com.hevy.* permissions: $permissionNames",
        )
        // Third-party permissions must stay untouched (the manual mod mangled these).
        assertFalse(
            permissionNames.any { it.startsWith("com.hevy.mod_") },
            "Found mangled third-party permissions: $permissionNames",
        )
        assertTrue(
            permissionNames.contains("com.google.android.gms.permission.ACTIVITY_RECOGNITION"),
            "Google permission must be untouched",
        )

        val applications = document.getElementsByTagName("application")
        assertEquals(1, applications.length)
        val application = applications.item(0) as Element
        assertEquals("true", application.getAttribute("android:supportsRtl"))
        assertEquals("com.hevy.MainApplication", application.getAttribute("android:name"))
    }

    private fun collectAttributes(document: Document, tag: String, attribute: String): List<String> {
        val nodes = document.getElementsByTagName(tag)
        val values = ArrayList<String>(nodes.length)
        for (i in 0 until nodes.length) {
            values.add((nodes.item(i) as Element).getAttribute(attribute))
        }
        return values
    }
}
```

- [ ] **Step 3: Write `PatcherEndToEndTest.kt`**:

```kotlin
package app.hevy.patches

import app.hevy.patches.hermespaywall.hermes.PaywallMod
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assumeTrue
import kotlin.test.fail

class PatcherEndToEndTest {

    @Test
    fun patchesTheOriginalApkIntoTheKnownGoodMod() {
        val apkPath = System.getenv("HEVY_TEST_ORIGINAL_APK")
        val originalBundlePath = System.getenv("HEVY_TEST_ORIGINAL_BUNDLE")
        assumeTrue(apkPath != null && originalBundlePath != null, "e2e env vars not set")

        val outputDir = File(System.getenv("HEVY_TEST_OUTPUT_DIR") ?: "build/e2e-output")
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        Patcher(
            PatcherConfig(
                apkFile = File(apkPath),
                temporaryFilesPath = File("build/e2e-temp"),
            )
        ).use { patcher ->
            patcher += allPatches.toSet()

            for (result in patcher()) {
                result.exception?.let { exception ->
                    fail("Patch ${result.patch.name} failed: $exception", exception)
                }
            }

            val patcherResult = patcher.get()

            // 1. The patched Hermes bundle must be byte-identical to the
            //    known-good mod (PaywallMod output, trailer recomputed).
            val expectedBundle = File(originalBundlePath).readBytes().also { PaywallMod.apply(it) }
            val patchedBundle = findFile(
                listOfNotNull(patcherResult.resources.otherResources, File("build/e2e-temp/apk")),
                "index.android.bundle",
            )
            assertContentEquals(expectedBundle, patchedBundle.readBytes())

            // 2. Stage the compiled resources for the manifest assertion.
            val resourcesApk = patcherResult.resources.resourcesApk
                ?: fail("Expected compiled resources APK")
            val staged = outputDir.resolve("resources.apk")
            resourcesApk.copyTo(staged, overwrite = true)
        }

        // 3. Decode the compiled resources and assert the renamed manifest.
        val decodedDir = outputDir.resolve("decoded")
        val process = ProcessBuilder(
            "apktool", "d", "-f", "-s",
            "-o", decodedDir.absolutePath,
            outputDir.resolve("resources.apk").absolutePath,
        ).redirectErrorStream(true).start()
        val apktoolOutput = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "apktool failed:\n$apktoolOutput" }

        ManifestAssertions.assertRenamedManifest(decodedDir.resolve("AndroidManifest.xml"))
    }

    private fun findFile(roots: List<File>, name: String): File {
        val matches = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.name == name }.toList()
        }
        return matches.firstOrNull()
            ?: fail("Could not find $name in patched output; searched: $roots")
    }
}
```

- [ ] **Step 4: Run the e2e test**

```bash
HEVY_TEST_ORIGINAL_APK=/Users/rsalim/personal/hevy-diff/work/original_merged.apk \
HEVY_TEST_ORIGINAL_BUNDLE=/Users/rsalim/personal/hevy-diff/work/original_apktool/assets/index.android.bundle \
HEVY_TEST_OUTPUT_DIR=/Users/rsalim/personal/hevy-diff/work/e2e-output \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :patches:test --tests "app.hevy.patches.PatcherEndToEndTest" --no-daemon
```

Expected: PASS. This runs the full patcher (full arsclib resource decode of the ~200 MB APK, several minutes). Failure triage order:
- arsclib resource compile errors → capture the log; if the decode/encode pipeline itself fails on this APK, fall back to: (a) keep the unit/golden tests as the automated gate, (b) have the user verify the bundle in Morphe Desktop/Manager manually, and (c) note the failure in the task report. Do NOT ship without either e2e green or the user's manual verification.
- Patch failures referencing `PaywallMod` → cross-check against "Validated ground truth".
- `index.android.bundle` not found in output staging → print the `otherResources` tree once during triage to locate the staging layout, then adjust the `findFile` roots accordingly (staging layout only, never the assertion logic).

- [ ] **Step 5: Commit**

```bash
git add patches/src/test
git commit -m "chore: Add end-to-end verification harness"
```

---

### Task 7: README rewrite

**Files:**
- Modify: `README.md` (full rewrite)

**Interfaces:**
- Produces: public README with working add-source link and the untouched auto-generated patch-list markers that `release.yml` fills on the first release.

- [ ] **Step 1: Replace README.md** with exactly:

```markdown
# 🔋 Hevy Pro Patches

Morphe compatible patches for Hevy - Gym Log Workout Tracker (com.hevy).

## ❓ About

Hevy Pro Patches is a Morphe compatible patch bundle that unlocks the Pro features of the Hevy workout tracking app and renames the app so the patched version can be installed alongside the original.

### How to use these patches

1. Install [Morphe Manager](https://morphe.software/).
2. Click [this link](https://morphe.software/add-source?github=RaymondSalim/morphe-patches) to add these patches to Morphe Manager.
3. Get Hevy 3.0.11 as an APK or APKM from [APKMirror](https://www.apkmirror.com/apk/hevy/hevy-gym-log-workout-tracker/).
4. In Morphe Manager, select the patches and patch the app.
5. Install the patched app alongside the original.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->

<!-- Do not modify this section by hand. The patch list is generated when release.yml creates a new release.

     If you wish for the patches list to be collapsed, then remove the word 'EXPANDED' from the comment tag above.

     If you wish to manually keep this list updated then remove the PATCHES_START and PATCHES_END
     comment blocks entirely. -->

#### A list of your patches will automatically be shown here after your first patches release is created.

&nbsp;

<!-- The patches end tag is intentionally placed here so the first release will clean up
     this readme of all developer instructions above. -->
<!-- PATCHES_END -->

## 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Apply the mpp file using [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop) like any other patch bundle.

## 🧑‍💻 Development

- Development happens on the `dev` branch. `feat:` and `fix:` commits on `dev` create prereleases. Merging `dev` into `main` (no squash) creates stable releases. Everything is released automatically by `release.yml`.
- Never manually edit generated files (`patches-list.json`, `patches-bundle.json`, `CHANGELOG.md`, the README patch list). They are updated by `release.yml`.

## 📜 License

Hevy Pro Patches are licensed under the [GNU General Public License v3.0](LICENSE)
```

- [ ] **Step 2: Verify the auto-gen markers survived.** `grep -n "PATCHES_START\|PATCHES_END" README.md` must show both markers with `EXPANDED` intact.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "chore: Update readme"
```

---

### Task 8: Publish pipeline

**Files:** none (git + GitHub operations only)

**Interfaces:**
- Consumes: `dev` branch with Tasks 1-7 committed; gh CLI auth; user's repo settings.

- [ ] **Step 1: Configure repo settings (needs the user's GitHub session).**

```bash
gh api -X PUT repos/RaymondSalim/morphe-patches/actions/permissions -f enabled=true -f allowed_actions=all
gh api -X PUT repos/RaymondSalim/morphe-patches/actions/permissions/workflow \
  -f default_workflow_permissions=write -F can_approve_pull_request_reviews=true
```

(These correspond to Settings → Actions → "Allow all actions"; Workflow permissions → "Read and write"; and "Allow GitHub Actions to create and approve pull requests". The backmerge job needs the last one.)

- [ ] **Step 2: Push dev and watch the prerelease run**

```bash
git push -u origin dev
RUN_ID=$(gh run list --repo RaymondSalim/morphe-patches --branch dev --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$RUN_ID" --repo RaymondSalim/morphe-patches
```

Expected: release workflow green; a prerelease created from the `feat:` commits (semantic-release version derived from the changelog, e.g. `v1.0.0-rc.1`-style); `patches-list.json`, `patches-bundle.json`, `CHANGELOG.md` and the README patch list updated by the release bot. On failure, read `gh run view "$RUN_ID" --log-failed`, fix, and push again — do not create releases by hand.

- [ ] **Step 3: Pull the release bot's commits back into dev**

```bash
git pull --ff-only origin dev
```

- [ ] **Step 4: Merge dev into main for the stable release**

```bash
git checkout main
git merge dev --no-ff
git push origin main
RUN_ID=$(gh run list --repo RaymondSalim/morphe-patches --branch main --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$RUN_ID" --repo RaymondSalim/morphe-patches
```

Expected: stable `v1.0.0` release with a `patches-*.mpp` asset.

- [ ] **Step 5: Final verification**

```bash
git pull --ff-only origin main
gh release list --repo RaymondSalim/morphe-patches --limit 3
gh release view v1.0.0 --repo RaymondSalim/morphe-patches --json assets --jq '.assets[].name'
git checkout dev
```

Expected: release asset `patches-1.0.0.mpp`; on GitHub, the README shows the generated patch list (Package rename, Hermes paywall bypass); `patches-list.json` version = 1.0.0.

- [ ] **Step 6: Smoke-test the released bundle (user-assisted).** Download the release `.mpp`, apply it to the original Hevy 3.0.11 APK(M) in Morphe Manager/Desktop, install, and confirm Pro features are unlocked and the app installs alongside the stock app. If the user reports a problem, open a follow-up task rather than re-releasing silently.

---

## Self-review notes

- Spec coverage: ground-truth edits (Tasks 3-4), corrected rename (Task 5), constants with APKM+APK (Task 2), build config + junk removal (Task 1), README (Task 7), verification harness (Tasks 3-4 tests + Task 6 e2e), publish pipeline (Task 8).
- The plan's correctness bar is the golden test: byte-identical to the known-good modded bundle except the recomputed SHA-1 trailer. Never weaken it.
- Version pinning: `Constants` targets 3.0.11 only; the Hermes parser rejects other bytecode versions, so future Hevy versions fail loudly until tested.

