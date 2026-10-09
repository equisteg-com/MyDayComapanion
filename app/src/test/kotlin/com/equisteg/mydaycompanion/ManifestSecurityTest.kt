package com.equisteg.mydaycompanion

import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the local-first and permission-minimisation rules in the source manifest. CI also checks
 * the merged manifest of the built APK, which catches permissions added by libraries.
 */
class ManifestSecurityTest {
    private val ns = "http://schemas.android.com/apk/res/android"
    private val tools = "http://schemas.android.com/tools"
    private val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))

    /** Permissions allowed in the current phase. Adding one requires an entry in docs/prebuild/06-permission-model.md. */
    private val allowedPermissions = setOf("android.permission.USE_BIOMETRIC", "android.permission.USE_FINGERPRINT")

    @Test
    fun `no network permission and no unapproved permissions`() {
        val perms = doc.getElementsByTagName("uses-permission")
        for (i in 0 until perms.length) {
            val el = perms.item(i) as Element
            val name = el.getAttributeNS(ns, "name")
            val removed = el.getAttributeNS(tools, "node") == "remove"
            if (name == "android.permission.INTERNET") assertTrue(removed, "INTERNET must be explicitly removed")
            if (!removed) assertTrue(name in allowedPermissions, "Unapproved permission $name")
        }
    }

    @Test
    fun `backups are disabled`() {
        val app = doc.getElementsByTagName("application").item(0) as Element
        assertEquals("false", app.getAttributeNS(ns, "allowBackup"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(ns, "dataExtractionRules"))
        assertTrue(app.getAttributeNS(ns, "usesCleartextTraffic").let { it.isEmpty() || it == "false" })
    }

    @Test
    fun `only the launcher activity is exported`() {
        val activities = doc.getElementsByTagName("activity")
        var exported = 0
        for (i in 0 until activities.length) {
            if ((activities.item(i) as Element).getAttributeNS(ns, "exported") == "true") exported++
        }
        assertEquals(1, exported)
        listOf("service", "receiver", "provider").forEach { assertEquals(0, doc.getElementsByTagName(it).length, it) }
    }
}
