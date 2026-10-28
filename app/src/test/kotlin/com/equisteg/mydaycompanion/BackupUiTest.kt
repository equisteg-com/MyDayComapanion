package com.equisteg.mydaycompanion

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.equisteg.mydaycompanion.domain.agent.ExecutionResult
import com.equisteg.mydaycompanion.domain.agent.ProposedAction
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.policy.UserConfirmation
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.domain.tools.CreateBackupTool
import com.equisteg.mydaycompanion.navigation.AppNavigation
import com.equisteg.mydaycompanion.ui.AuthOutcome
import com.equisteg.mydaycompanion.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class BackupUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val passphrase = "correct horse battery"
    private val picked = Uri.parse("content://test/mydaycompanion-20261006.mdcbackup")

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** A backup made on "the old phone", through the same approved tool the app uses. */
    private fun oldPhoneBackup(): ByteArray = runBlocking {
        val old = TestGraph()
        val now = old.clock.instant()
        old.tasks.upsert(listOf(Task("t1", "Call Raj", date = LocalDate.of(2026, 10, 6), createdAt = now), Task("t2", "Buy milk", createdAt = now)))
        old.preferences.set(UserPreferences(onboardingDone = true))
        old.exportTargets.registered += "file"
        val action = ProposedAction("backup.create", CreateBackupTool.args("file", old.staging.stagePassphrase(passphrase.toCharArray())))
        val ask = old.orchestrator.execute(action, Requester.USER) as ExecutionResult.NeedsConfirmation
        assertTrue(old.orchestrator.execute(action, Requester.USER, UserConfirmation(ask.actionFingerprint)) is ExecutionResult.Done)
        old.exportTargets.written.getValue("file")
    }

    private fun typePassphrase(text: String) {
        compose.onNodeWithTag("backup-passphrase").performTextClearance()
        compose.onNodeWithTag("backup-passphrase").performTextInput(text)
        compose.onNodeWithTag("backup-passphrase-submit").performClick()
    }

    @Test
    fun `a new phone restores from a backup during first-run setup`() {
        val graph = TestGraph()
        val services = TestServices(graph, onboarded = false).apply { backupFile = oldPhoneBackup() }
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { AppNavigation(vm) }
        compose.waitUntil(5_000) { has("onboarding-restore") }

        vm.onRestoreFileChosen(picked)
        compose.waitUntil(5_000) { has("backup-passphrase") }
        typePassphrase("wrong horse battery")
        compose.waitUntil(10_000) { has("backup-error") }
        compose.onNodeWithTag("backup-error").assertTextContains("9 tries left", substring = true)
        assertEquals(1, services.deviceSettings.restoreFailures)

        typePassphrase(passphrase)
        compose.waitUntil(10_000) { has("confirm") }
        assertEquals(0, runBlocking { graph.tasks.count() }, "nothing replaced before approval")
        compose.onNodeWithTag("confirm").performClick()
        compose.waitUntil(5_000) { vm.state.value.authRequest != null }
        vm.onDeviceAuthResult(AuthOutcome.SUCCEEDED)

        compose.waitUntil(10_000) { runBlocking { graph.tasks.count() } == 2 }
        compose.waitUntil(5_000) { !has("onboarding") }
        assertEquals(0, services.deviceSettings.restoreFailures)
        val history = graph.audit.entries.joinToString { "${it.action} ${it.reasonCode} ${it.detail}" }
        assertTrue("backup.restore" in history)
        assertTrue("horse" !in history && "Call Raj" !in history, history)
    }

    @Test
    fun `ten wrong passphrases close Restore for an hour`() {
        val graph = TestGraph()
        val services = TestServices(graph).apply { backupFile = oldPhoneBackup() }
        services.deviceSettings.restoreFailures = 9
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { AppNavigation(vm) }

        vm.onRestoreFileChosen(picked)
        compose.waitUntil(5_000) { has("backup-passphrase") }
        typePassphrase("wrong horse battery")
        compose.waitUntil(10_000) { vm.state.value.backupPrompt == null }
        assertTrue(vm.state.value.message.orEmpty().startsWith("Too many wrong passphrases"))
        assertTrue(services.deviceSettings.restoreLockedUntilMillis > graph.clock.millis())

        vm.onRestoreFileChosen(picked)
        compose.waitUntil(5_000) { !vm.state.value.busy }
        assertNull(vm.state.value.backupPrompt, "no new tries while locked")
        assertEquals(0, runBlocking { graph.tasks.count() })
    }

    @Test
    fun `a new backup's passphrase must be long enough and typed twice the same`() {
        val services = TestServices(TestGraph())
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { AppNavigation(vm) }
        assertTrue(vm.backupFileName().matches(Regex("mydaycompanion-\\d{8}\\.mdcbackup")))

        vm.onBackupDestinationChosen(picked)
        compose.waitUntil(5_000) { has("backup-passphrase") }
        compose.onNodeWithTag("backup-passphrase").performTextInput("short")
        compose.onNodeWithTag("backup-strength").assertTextContains("Too short", substring = true)
        compose.onNodeWithTag("backup-passphrase-submit").performClick()
        compose.waitUntil(5_000) { has("backup-error") }
        compose.onNodeWithTag("backup-error").assertTextContains("at least 10", substring = true)

        compose.onNodeWithTag("backup-passphrase").performTextInput("correct horse battery staple")
        compose.onNodeWithTag("backup-strength").assertTextContains("Strong", substring = true)
        compose.onNodeWithTag("backup-passphrase-repeat").performTextInput("correct horse battery stable")
        compose.onNodeWithTag("backup-passphrase-submit").performClick()
        compose.waitUntil(5_000) { vm.state.value.backupPrompt?.error?.contains("don't match") == true }

        vm.onCancelBackupPrompt()
        compose.waitUntil(5_000) { !has("backup-passphrase") }
    }
}
