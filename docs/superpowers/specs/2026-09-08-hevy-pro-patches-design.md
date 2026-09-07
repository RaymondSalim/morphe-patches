# Hevy Pro Patches — Bundle Compliance Design

Date: 2026-09-08
Repo: `RaymondSalim/morphe-patches` (local: `hevy-pro-patch/`)
Target app: Hevy - Gym Log Workout Tracker (`com.hevy`) 3.0.11 (version code 2015570)

## Goal

Turn the current half-ported Morphe patches template into a compliant, working patch
bundle: two patches that reproduce the known-good manually modded APK
(`hevy-diff/work/modded_apktool`), a build that compiles, a de-templated README,
and the template's `dev`-branch semantic-release pipeline publishing to GitHub.

## Ground truth: the known-good mod

The manual mod (`work/original_apktool` vs `work/modded_apktool`) is exactly:

1. `AndroidManifest.xml`: `package="com.hevy"` → `com.hevy.mod`, provider
   authorities `com.hevy.*` → `com.hevy.mod.*`, custom permission
   `com.hevy.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (declaration +
   uses-permission) renamed. (The manual edit also accidentally prefixed
   Google permissions with `com.hevy.mod_`; that is a defect we do NOT
   replicate. `android:supportsRtl` loss is an editing artifact, also not
   replicated.)
2. `assets/index.android.bundle` (Hermes bytecode, u64 magic `0x1F1903C103BC1FC6`
   stored little-endian, bytecode version 96 at header offset 8): 5 changed bytes
   at 3 sites. Opcodes (v96, little-endian): `0x78` LoadConstTrue, `0x79`
   LoadConstFalse, `0x5c` Ret, `0x7c` LoadThisNS, `0x29` GetEnvironment.

| # | Function (identified by string refs) | Original bytes | Modded bytes | Effect |
|---|--------------------------------------|----------------|--------------|--------|
| 1 | anon generator referencing `HEVY_PRO_DISK_STORAGE_KEY` + `HEVY_PRO_LAST_SUCCESSFUL_FETCH`, writing `is_pro` into `subscription` | `79 03` (LoadConstFalse r3) at fn offset +0x10 | `78 03` | pro-state reset stores `is_pro: true` |
| 2 | getter referencing `isProStatusOverrideEnabled`, `getProStatusOverride`, `force-pro`, `force-free` | `7c 00 29 01 01` (LoadThisNS r0; GetEnvironment r1,1) | `78 00 5c 00 01` (LoadConstTrue r0; Ret r0) | main isPro getter returns true unconditionally |
| 3 | getter referencing `lastSuccessfulFetch` + `isWithinProOfflineGracePeriod`, building `{lastFetchAt, expiresAt, now}` | `79 00` (LoadConstFalse r0) at fn offset +0x8E | `78 00` | terminal grace-period return path yields true |

The mod left the bundle's SHA1 trailer (last 20 bytes) stale. Our implementation
recomputes it.

Correctness bar: a patcher-produced bundle must differ from the original by
exactly these 5 bytes plus a valid recomputed SHA1 trailer, and the manifest
must equal the corrected rename described above.

## Architecture

### Patch 1: `PackageRenamePatch` (`resourcePatch`)

- `document("AndroidManifest.xml")`:
  - set `package` to the option value (Morphe `StringOption`, default
    `com.hevy.mod`);
  - rename `android:authorities` values starting `com.hevy.` to the new
    package prefix;
  - rename the custom permission name `com.hevy.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
    and its `uses-permission` reference;
  - leave third-party permissions (android.*, com.google.android.*, etc.)
    untouched.
- Resource-side renaming (`@com.hevy:` references, `public.xml`,
  `package.json`) is done by the patcher's built-in `PackageRenamingProcessor`
  when the manifest package attribute changes. The patch does not duplicate
  that work.

### Patch 2: `HermesPaywallPatch` (`rawResourcePatch`)

- `get("assets/index.android.bundle")`; if absent, throw (fail the patch) —
  never silently no-op.
- A dependency-free Hermes v96 parser (`app.hevy.patches.hermespaywall.hermes`):
  header (magic + version asserted), string kind table, string table,
  function header table, and an instruction scanner that resolves string-id
  operands to string values. Opcode operand table ported from hermes-dec's
  v96 definitions.
- Locate each of the 3 target functions by its unique string-reference
  signature (never by file offset or function id, so app builds with shifted
  layouts still match). For each site assert the exact expected original
  bytes at the computed location before writing:
  1. generator: `LoadConstFalse rX` immediately followed by
     `PutNewOwnById` with string id of `is_pro` → flip to LoadConstTrue;
  2. isPro getter: function must begin `LoadThisNS r0; GetEnvironment rX,n`
     → rewrite first 5 bytes to `LoadConstTrue r0; Ret r0`;
  3. grace getter: trailing `JmpTrue; LoadConstFalse rX; Ret rX;` return
     pair near end of body → flip LoadConstFalse to LoadConstTrue.
- Exactly 3 edits total, else throw. Recompute the SHA1 trailer after edits.
- Log a per-site summary.

### Shared constants

`Constants.kt`: two `Compatibility` entries — `APKM` (preferred; apkmirror
distribution) and universal `APK` — both targeting version `3.0.11`, package
`com.hevy`. No per-ABI version codes needed (all splits share version code
2015570). Corrected icon color (near-black Hevy brand).

### Patch registration

`Patches.kt` exports `packageRenamePatch`, `hermesPaywallPatch` in `allPatches`.
`util/PatchListGenerator.kt` remains untouched.

## Build and config

- Restore `gradle.properties` from template: `org.gradle.parallel`,
  `org.gradle.caching`, `kotlin.code.style = official`, `version = 1.0.0`.
  Remove the committed `gpr.user/gpr.key` dummy credentials; registry
  credentials live in `~/.gradle/gradle.properties` locally (never in the
  repo) and in `GITHUB_ACTOR`/`GITHUB_TOKEN` in CI, which
  `settings.gradle.kts` already reads.
- Delete `patches/CheckSyntax.kt`, `patches/build_temp.gradle.kts`, tracked
  and stray `.DS_Store` files; add `.DS_Store` to `.gitignore`.
- `patches/build.gradle.kts`: fix `about` placeholders (`contact`, `website`,
  `source` = `https://github.com/RaymondSalim/morphe-patches`).
- Issue templates: replace `TEMPLATE` placeholder URLs with the real repo.
- Local build needs no Android SDK (verified: CI builds with only Java 21 +
  npm). Java 21 via `openjdk@21` for local runs.

## Verification

1. **Unit (committed):** test source set in the patches module exercising the
   Hermes parser/patcher on `work/original_apktool/assets/index.android.bundle`,
   asserting output byte-identical to `work/modded_apktool/assets/index.android.bundle`
   except the final 20-byte trailer, which must equal sha1 of the rest.
   Bundle paths supplied via environment variables `HEVY_TEST_ORIGINAL_BUNDLE`
   and `HEVY_TEST_MODDED_BUNDLE`; test skips when unset.
2. **End to end (local):** build the `.mpp`, run both patches headlessly via
   the patcher API against the APK named by `HEVY_TEST_ORIGINAL_APK`
   (`work/original_merged.apk`), then diff the output's manifest and bundle
   against the known-good mod. If the headless runner proves impractical,
   fall back to manual verification in Morphe Manager by the user.

## Publish pipeline

- Branch model per template: work lands on `dev` (semantic commits);
  `feat:`/`fix:` on `dev` produce prereleases; merging `dev`→`main`
  (no squash) produces the stable release. Release workflow regenerates
  `patches-list.json`, `patches-bundle.json`, `CHANGELOG.md`, README patch
  list — never edited by hand.
- Commit series: `chore:` for config cleanup, `feat: Added Hevy package
  rename patch`, `feat: Added Hevy Pro paywall bypass patch`.
- Push `dev` to `git@github.com:RaymondSalim/morphe-patches`; verify the
  release workflow runs green. Requires repo settings: Actions enabled,
  "Allow GitHub Actions to create and approve pull requests" (backmerge).
- README rewritten (patcheddit-style): about, supported app/version, usage,
  dev notes, license; add-source link
  `https://morphe.software/add-source?github=RaymondSalim/morphe-patches`.
  The auto-generated patch list section stays.

## Non-goals

- No additional patches (analytics removal, app name override, etc.).
- No extension module (`extensions/` stays deleted; neither patch needs one).
- No support for other Hevy versions until tested.
- Not replicating the manual mod's Google-permission mangling or stale SHA1.

## Risks

- Hermes opcode table port must match v96 exactly; mitigated by the
  byte-identical unit test against the known-good mod.
- Content-provider authorities referenced from JS (`com.hevy.fileprovider`
  etc.) are NOT rewritten in the bundle (the known-good mod doesn't either);
  features sharing files through those providers may behave differently than
  the stock app. Accepted: matches proven behavior.
- Function signatures could change in future Hevy versions; patch fails
  loudly (throws) rather than producing a broken app.
