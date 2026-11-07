// path: app/src/test/java/com/example/smsexpensertracker/security/PrivacyManifestTest.kt
package com.example.smsexpensertracker.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Validates privacy and security assertions defined in Phase 7:
 * - Manifest must NEVER request INTERNET permission or network state.
 * - Application must strictly disallow backups (allowBackup=false).
 * - Full backup and data extraction rules must exclude local databases and preferences.
 * - SmsReceiver must be guarded by BROADCAST_SMS system permission.
 */
class PrivacyManifestTest {

    private fun findProjectFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find file at $relativePath in ${File(".").absolutePath}")
    }

    @Test
    fun `manifest never requests INTERNET or network permissions`() {
        val manifestFile = findProjectFile("src/main/AndroidManifest.xml")
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile)

        val permissionNodes = doc.getElementsByTagName("uses-permission")
        val permissions = (0 until permissionNodes.length).map { i ->
            (permissionNodes.item(i) as Element).getAttribute("android:name")
        }

        // Strictly verify zero network permissions
        assertFalse(
            permissions.contains("android.permission.INTERNET"),
            "CRITICAL SECURITY VIOLATION: android.permission.INTERNET found in manifest!"
        )
        assertFalse(
            permissions.contains("android.permission.ACCESS_NETWORK_STATE"),
            "Privacy violation: ACCESS_NETWORK_STATE must not be requested"
        )
        assertFalse(
            permissions.contains("android.permission.ACCESS_WIFI_STATE"),
            "Privacy violation: ACCESS_WIFI_STATE must not be requested"
        )
    }

    @Test
    fun `manifest strictly disables allowBackup and configures backup exclusion rules`() {
        val manifestFile = findProjectFile("src/main/AndroidManifest.xml")
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile)

        val appNodes = doc.getElementsByTagName("application")
        assertTrue(appNodes.length > 0, "Application element missing")
        val app = appNodes.item(0) as Element

        val allowBackup = app.getAttribute("android:allowBackup")
        assertEquals("false", allowBackup, "allowBackup must be explicitly set to false")

        val dataExtractionRules = app.getAttribute("android:dataExtractionRules")
        assertEquals("@xml/data_extraction_rules", dataExtractionRules)

        val fullBackupContent = app.getAttribute("android:fullBackupContent")
        assertEquals("@xml/backup_rules", fullBackupContent)
    }

    @Test
    fun `backup and data extraction rules explicitly exclude database and preferences`() {
        val backupRulesFile = findProjectFile("src/main/res/xml/backup_rules.xml")
        val backupText = backupRulesFile.readText()
        assertTrue(backupText.contains("""<exclude domain="database" path="." />"""))
        assertTrue(backupText.contains("""<exclude domain="sharedpref" path="." />"""))

        val dataExtractionFile = findProjectFile("src/main/res/xml/data_extraction_rules.xml")
        val extractionText = dataExtractionFile.readText()
        assertTrue(extractionText.contains("""<exclude domain="database" path="." />"""))
        assertTrue(extractionText.contains("""<exclude domain="sharedpref" path="." />"""))
    }

    @Test
    fun `sms receiver is secured with BROADCAST_SMS permission`() {
        val manifestFile = findProjectFile("src/main/AndroidManifest.xml")
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile)

        val receiverNodes = doc.getElementsByTagName("receiver")
        assertTrue(receiverNodes.length > 0, "No receivers found in manifest")

        var smsReceiverFound = false
        for (i in 0 until receiverNodes.length) {
            val receiver = receiverNodes.item(i) as Element
            if (receiver.getAttribute("android:name") == ".ingestion.SmsReceiver") {
                smsReceiverFound = true
                val permission = receiver.getAttribute("android:permission")
                assertEquals(
                    "android.permission.BROADCAST_SMS",
                    permission,
                    "SmsReceiver must be protected by android.permission.BROADCAST_SMS"
                )
            }
        }
        assertTrue(smsReceiverFound, "SmsReceiver declaration not found in manifest")
    }
}

