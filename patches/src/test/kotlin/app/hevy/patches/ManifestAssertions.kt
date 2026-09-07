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
