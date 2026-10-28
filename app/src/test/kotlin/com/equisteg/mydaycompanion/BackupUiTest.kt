package com.equisteg.mydaycompanion

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.equisteg.mydaycompanion.domain.agent.ExecutionResult
import com.equisteg.mydaycompanion.domain.agent.ProposedAction
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.UserPreferences
import com.equisteg.mydaycompanion.domain.policy.Requester
import com.equisteg.mydaycompanion.domain.policy.UserConfirmation
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.domain.tools.CreateBackupTool
import com.equisteg.mydaycompanion.ui.AuthOutcome
import com.equisteg.mydaycompanion.ui.BackupPrompt
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.UiState
import com.equisteg.mydaycompanion.ui.screens.BackupPassphraseForm
import com.equisteg.mydaycompanion.ui.screens.HistoryScreen
import com.equisteg.mydaycompanion.ui.screens.OnboardingScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The passphrase form and the onboarding button are rendered on their own, like ChallengeForm:
 * Robolectric can't lay out a FLAG_SECURE dialog window (it re-measures it forever). The backup
 * and restore flows are driven through the view model, which the dialog only forwards to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class BackupUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val passphrase = "correct horse battery"
    private val picked = Uri.parse("content://test/mydaycompanion-20261006.mdcbackup")

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** A backup made on "the old phone", through the same approved tool the app uses. */
    private fun oldPhoneBackup(extra: suspend (TestGraph) -> Unit = {}): ByteArray = runBlocking {
        val old = TestGraph()
        extra(old)
        val now = old.clock.instant()
        old.tasks.upsert(listOf(Task("t1", "Call Raj", date = LocalDate.of(2026, 10, 6), createdAt = now), Task("t2", "Buy milk", createdAt = now)))
        old.preferences.set(UserPreferences(onboardingDone = true))
        old.exportTargets.registered += "file"
        val action = ProposedAction("backup.create", CreateBackupTool.args("file", old.staging.stagePassphrase(passphrase.toCharArray())))
        val ask = old.orchestrator.execute(action, Requester.USER) as ExecutionResult.NeedsConfirmation
        assertTrue(old.orchestrator.execute(action, Requester.USER, UserConfirmation(ask.actionFingerprint)) is ExecutionResult.Done)
        old.exportTargets.written.getValue("file")
    }

    @Test
    fun `the passphrase form shows strength and hands over both passphrases`() {
        var sent: Pair<String, String>? = null
        compose.setContent {
            BackupPassphraseForm(BackupPrompt(BackupPrompt.Kind.CREATE, "The two passphrases don't match."), busy = false, onCreate = { a, b -> sent = String(a) to String(b) }, onRestore = {}, onCancel = {})
        }
        compose.onNodeWithTag("backup-error").assertTextContains("don't match", substring = true)
        compose.onNodeWithTag("backup-passphrase").performTextInput("short")
        compose.onNodeWithTag("backup-strength").assertTextContains("Too short", substring = true)
        compose.onNodeWithTag("backup-passphrase").performTextInput(" horse battery staple")
        compose.onNodeWithTag("backup-strength").assertTextContains("Strong", substring = true)
        compose.onNodeWithTag("backup-passphrase-repeat").performTextInput("short horse battery staple")
        compose.onNodeWithTag("backup-passphrase-submit").performClick()
        assertEquals("short horse battery staple" to "short horse battery staple", sent)
    }

    @Test
    fun `first-run setup offers Restore from backup`() {
        var asked = false
        compose.setContent { OnboardingScreen(UiState(selectedDate = LocalDate.of(2026, 10, 6)), {}, {}, onRestoreFromBackup = { asked = true }) }
        compose.onNodeWithTag("onboarding-restore").performClick()
        assertTrue(asked)
    }

    @Test
    fun `a new phone restores from a backup during first-run setup`() {
        val graph = TestGraph()
        val services = TestServices(graph, onboarded = false).apply { backupFile = oldPhoneBackup() }
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        idle()
        assertTrue(vm.state.value.showOnboarding)

        vm.onRestoreFileChosen(picked)
        idle()
        assertEquals(BackupPrompt.Kind.RESTORE, vm.state.value.backupPrompt?.kind)
        vm.onRestorePassphrase("wrong horse battery".toCharArray())
        idle()
        assertTrue(vm.state.value.backupPrompt?.error.orEmpty().contains("9 tries left"))
        assertEquals(1, services.deviceSettings.restoreFailures)

        vm.onRestorePassphrase(passphrase.toCharArray())
        idle()
        val pending = assertNotNull(vm.state.value.pending)
        assertEquals("Replace everything with this backup?", pending.title)
        assertTrue("2 tasks" in pending.body && "App lock: on" in pending.body, pending.body)
        assertEquals(0, runBlocking { graph.tasks.count() }, "nothing replaced before approval")

        vm.onConfirmPending()
        idle()
        assertNotNull(vm.state.value.authRequest)
        vm.onDeviceAuthResult(AuthOutcome.SUCCEEDED)
        idle()

        assertEquals(2, runBlocking { graph.tasks.count() })
        assertTrue(!vm.state.value.showOnboarding, "setup closes with the restored settings")
        assertTrue(vm.state.value.message.orEmpty().startsWith("Restored."))
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
        idle()

        vm.onRestoreFileChosen(picked)
        idle()
        vm.onRestorePassphrase("wrong horse battery".toCharArray())
        idle()
        assertNull(vm.state.value.backupPrompt)
        assertTrue(vm.state.value.message.orEmpty().startsWith("Too many wrong passphrases"))
        assertTrue(services.deviceSettings.restoreLockedUntilMillis > graph.clock.millis())

        vm.onRestoreFileChosen(picked)
        idle()
        assertNull(vm.state.value.backupPrompt, "no new tries while locked")
        assertEquals(0, runBlocking { graph.tasks.count() })
    }

    @Test
    fun `a new backup's passphrase must be long enough and typed twice the same`() {
        val vm = MainViewModel(TestServices(TestGraph()), Dispatchers.Unconfined)
        idle()
        assertTrue(vm.backupFileName().matches(Regex("mydaycompanion-\\d{8}\\.mdcbackup")))

        vm.onBackupDestinationChosen(picked)
        assertEquals(BackupPrompt.Kind.CREATE, vm.state.value.backupPrompt?.kind)
        vm.onBackupPassphrase("short".toCharArray(), "short".toCharArray())
        idle()
        assertTrue(vm.state.value.backupPrompt?.error.orEmpty().contains("at least 10"))
        vm.onBackupPassphrase("correct horse battery staple".toCharArray(), "correct horse battery stable".toCharArray())
        idle()
        assertTrue(vm.state.value.backupPrompt?.error.orEmpty().contains("don't match"))

        vm.onCancelBackupPrompt()
        assertNull(vm.state.value.backupPrompt)
    }

    @Test
    fun `opening a different backup does not reset wrong guesses on the first one`() {
        val graph = TestGraph()
        val target = oldPhoneBackup()
        val other = oldPhoneBackup()
        assertTrue(!target.contentEquals(other), "two backups differ (salt and nonce)")
        val services = TestServices(graph).apply { backupFile = target }
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        idle()

        vm.onRestoreFileChosen(picked)
        idle()
        vm.onRestorePassphrase("wrong horse battery".toCharArray())
        idle()
        assertEquals(1, services.deviceSettings.restoreFailures)

        // A backup the person holding the phone can open doesn't clear the count on the first file.
        vm.onCancelBackupPrompt()
        services.backupFile = other
        vm.onRestoreFileChosen(picked)
        idle()
        vm.onRestorePassphrase(passphrase.toCharArray())
        idle()
        assertNotNull(vm.state.value.pending)
        vm.onCancelPending()
        assertEquals(1, services.deviceSettings.restoreFailures)

        // The right passphrase on the first file does.
        services.backupFile = target
        vm.onRestoreFileChosen(picked)
        idle()
        vm.onRestorePassphrase(passphrase.toCharArray())
        idle()
        assertEquals(0, services.deviceSettings.restoreFailures)
    }

    @Test
    fun `a decoy backup guessed on first can't reset guesses on the target`() {
        val graph = TestGraph()
        val decoy = oldPhoneBackup()
        val target = oldPhoneBackup()
        val services = TestServices(graph).apply { backupFile = decoy }
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        idle()
        fun guess(file: ByteArray, words: String) {
            vm.onCancelPending()
            vm.onCancelBackupPrompt()
            services.backupFile = file
            vm.onRestoreFileChosen(picked)
            idle()
            vm.onRestorePassphrase(words.toCharArray())
            idle()
        }
        guess(decoy, "wrong horse battery")
        repeat(3) { guess(target, "wrong horse battery") }
        assertEquals(4, services.deviceSettings.restoreFailures)
        guess(decoy, passphrase)
        assertEquals(4, services.deviceSettings.restoreFailures, "opening the decoy doesn't clear guesses made on the target")
        guess(target, passphrase)
        assertEquals(4, services.deviceSettings.restoreFailures, "a mixed count waits for the lock")
    }

    @Test
    fun `setting the phone's clock forward doesn't reopen Restore`() {
        val graph = TestGraph()
        val services = TestServices(graph).apply { backupFile = oldPhoneBackup() }
        services.deviceSettings.restoreFailures = 9
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        idle()
        vm.onRestoreFileChosen(picked)
        idle()
        vm.onRestorePassphrase("wrong horse battery".toCharArray())
        idle()
        assertTrue(vm.state.value.message.orEmpty().startsWith("Too many wrong passphrases"))

        // The wall clock says the hour is over, but only ten minutes of uptime have passed.
        services.deviceSettings.restoreLockedUntilMillis = graph.clock.millis() - 1
        services.elapsedNow += 10 * 60_000L
        vm.onRestoreFileChosen(picked)
        idle()
        assertNull(vm.state.value.backupPrompt, "still closed")
        assertTrue(vm.state.value.message.orEmpty().startsWith("Too many wrong passphrases"))

        // A full hour of uptime opens it again.
        services.elapsedNow += 50 * 60_000L
        vm.onRestoreFileChosen(picked)
        idle()
        assertEquals(BackupPrompt.Kind.RESTORE, vm.state.value.backupPrompt?.kind)
    }

    @Test
    fun `after a restore, History marks what came with the backup and restored days are planned again on view`() {
        val graph = TestGraph()
        val tomorrow = LocalDate.now(graph.clock).plusDays(1)
        val backup = oldPhoneBackup { old ->
            // An action from the old phone, an hour before the restore, and a saved plan for tomorrow.
            old.audit.record(
                com.equisteg.mydaycompanion.domain.audit.AuditEntry(
                    "old-phone", old.clock.instant().minusSeconds(3_600), com.equisteg.mydaycompanion.domain.audit.AuditActor.USER, "tasks.complete",
                    com.equisteg.mydaycompanion.domain.tools.RiskLevel.L1, com.equisteg.mydaycompanion.domain.audit.AuditDecision.ALLOWED,
                    com.equisteg.mydaycompanion.domain.audit.AuditOutcome.SUCCESS,
                ),
            )
            // A plan needs at least one block to travel in a backup; an empty one is simply planned again.
            val block = com.equisteg.mydaycompanion.domain.model.PlanBlock(
                "t2", "Buy milk", com.equisteg.mydaycompanion.domain.model.BlockKind.TASK, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(9, 30),
            )
            old.plans.save(com.equisteg.mydaycompanion.domain.model.DayPlan(tomorrow, listOf(block), emptyList(), emptyList(), "old"))
        }
        val services = TestServices(graph).apply { backupFile = backup }
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        idle()
        vm.onRestoreFileChosen(picked)
        idle()
        vm.onRestorePassphrase(passphrase.toCharArray())
        idle()
        vm.onConfirmPending()
        idle()
        vm.onDeviceAuthResult(AuthOutcome.SUCCEEDED)
        idle()
        assertTrue(vm.state.value.message.orEmpty().startsWith("Restored."))

        // OQ-24 c: entries older than the restore show "from backup".
        vm.refreshHistory()
        idle()
        val restoredAt = assertNotNull(vm.state.value.restoredAt)
        assertTrue(vm.state.value.history.single { it.id == "old-phone" }.timestamp < restoredAt)
        // Only the old phone's entry, so it is on screen whatever else History holds.
        val shown = vm.state.value.let { st -> st.copy(history = st.history.filter { it.id == "old-phone" }) }
        compose.setContent { HistoryScreen(shown, {}, {}) }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasText("from backup", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())

        // OQ-24 d: tomorrow came in with the backup; opening it plans it again, once.
        assertEquals("old", runBlocking { graph.plans.get(tomorrow) }?.plannerVersion)
        val plansBefore = graph.audit.entries.count { it.action == "plan.generate" }
        vm.selectDate(tomorrow)
        idle()
        assertEquals(plansBefore + 1, graph.audit.entries.count { it.action == "plan.generate" })
        assertTrue(runBlocking { graph.plans.get(tomorrow) }?.plannerVersion != "old")
        vm.selectDate(tomorrow.minusDays(1))
        vm.selectDate(tomorrow)
        idle()
        assertEquals(plansBefore + 1, graph.audit.entries.count { it.action == "plan.generate" }, "only the first view")
    }
}
