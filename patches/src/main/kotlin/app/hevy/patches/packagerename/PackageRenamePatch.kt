package app.hevy.patches.packagerename

import app.hevy.patches.shared.Constants
import app.hevy.patches.shared.preserveAppCode
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element

private const val ORIGINAL_PACKAGE = "com.hevy"
private const val ORIGINAL_APP_NAME = "Hevy"
private const val APP_NAME_RESOURCE = "app_name"

val packageRenamePatch = resourcePatch(
    name = "Rename package & app name",
    description = "Renames the app package so the patched app can be installed alongside the original Hevy app, and lets you change the app's launcher name.",
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

    val appNameOption = stringOption(
        key = "app-name",
        default = "Hevý",
        values = null,
        title = "App name",
        description = "The launcher name of the patched app.",
        required = true,
        validator = { value -> !value.isNullOrBlank() },
    )

    compatibleWith(Constants.COMPATIBILITY_HEVY_APKM, Constants.COMPATIBILITY_HEVY_APK)

    dependsOn(preserveAppCode)

    execute {
        // The option is required and its default is non-null, so a value is
        // guaranteed by the time the patch executes; assert it explicitly.
        val packageName = checkNotNull(packageNameOption.value) {
            "The package name option must be set"
        }
        check(packageName != ORIGINAL_PACKAGE) {
            "Package name must differ from $ORIGINAL_PACKAGE"
        }
        val appName = checkNotNull(appNameOption.value) {
            "The app name option must be set"
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

        // The launcher label resolves to the app_name string; widget labels
        // use their own strings and keep the stock names.
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
