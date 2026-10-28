package com.equisteg.mydaycompanion

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.equisteg.mydaycompanion.domain.ai.LocalModelControl
import com.equisteg.mydaycompanion.domain.ai.LocalModelStatus
import com.equisteg.mydaycompanion.domain.ai.ModelDownload
import com.equisteg.mydaycompanion.domain.config.FeatureFlags
import com.equisteg.mydaycompanion.domain.config.Flag
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.screens.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeModel(var status: LocalModelStatus) : LocalModelControl {
    var downloads = 0
    var statusCalls = 0
    override suspend fun status(): LocalModelStatus {
        statusCalls++
        return status
    }
    override fun download(): Flow<ModelDownload> = flow {
        downloads++
        emit(ModelDownload.Started)
        emit(ModelDownload.Progress(250_000_000))
        status = LocalModelStatus.AVAILABLE
        emit(ModelDownload.Done)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SmartModelUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun viewModel(model: LocalModelControl, flags: FeatureFlags = FeatureFlags()): Pair<MainViewModel, TestServices> {
        val services = TestServices(TestGraph(), flags = flags).apply { localModel = model }
        return MainViewModel(services, Dispatchers.Unconfined) to services
    }

    @Test
    fun `a phone that can use Gemini Nano offers the download, then says it is ready`() {
        val model = FakeModel(LocalModelStatus.DOWNLOADABLE)
        val (vm, _) = viewModel(model)
        compose.setContent { SettingsScreen(vm) }
        compose.onNodeWithTag("model-download").performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.state.value.modelStatus == LocalModelStatus.AVAILABLE }
        compose.onNodeWithTag("model-status").performScrollTo().assertTextContains("Ready", substring = true)
        assertTrue(model.downloads == 1)
        assertTrue(compose.onAllNodes(hasTestTag("model-download")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the switch is kept on the phone and turns the model off`() {
        val (vm, services) = viewModel(FakeModel(LocalModelStatus.AVAILABLE))
        compose.setContent { SettingsScreen(vm) }
        compose.waitUntil(5_000) { vm.state.value.modelStatus == LocalModelStatus.AVAILABLE }
        compose.onNodeWithTag("smart-toggle").performScrollTo().performClick().assertIsOff()
        assertFalse(services.deviceSettings.smartModel)
    }

    @Test
    fun `unsupported phones say so and can't switch it on`() {
        val (vm, _) = viewModel(FakeModel(LocalModelStatus.UNAVAILABLE))
        compose.setContent { SettingsScreen(vm) }
        compose.waitUntil(5_000) { vm.state.value.modelStatus == LocalModelStatus.UNAVAILABLE }
        compose.onNodeWithTag("smart-toggle").performScrollTo().assertIsNotEnabled().assertIsOff()
        compose.onNodeWithTag("model-status").assertTextContains("doesn't support", substring = true)
    }

    @Test
    fun `with the local model flag off there is no Gemini Nano section`() {
        val (vm, _) = viewModel(FakeModel(LocalModelStatus.AVAILABLE), FeatureFlags(mapOf(Flag.LOCAL_LLM to false)))
        compose.setContent { SettingsScreen(vm) }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("delete-all")).fetchSemanticsNodes().isNotEmpty(), "Settings is shown")
        assertTrue(compose.onAllNodes(hasTestTag("smart-toggle")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `ML Kit isn't asked anything while the flag or the switch is off`() {
        val model = FakeModel(LocalModelStatus.AVAILABLE)
        viewModel(model, FeatureFlags(mapOf(Flag.LOCAL_LLM to false)))
        val services = TestServices(TestGraph()).apply { localModel = model; deviceSettings.smartModel = false }
        val vm = MainViewModel(services, Dispatchers.Unconfined)
        compose.setContent { SettingsScreen(vm) }
        compose.waitForIdle()
        assertEquals(0, model.statusCalls, "nothing called while off")
        compose.onNodeWithTag("model-status").performScrollTo().assertTextContains("Switched off", substring = true)

        compose.onNodeWithTag("smart-toggle").performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.state.value.modelStatus == LocalModelStatus.AVAILABLE }
        assertEquals(1, model.statusCalls, "turning it on checks the phone once")
    }
}
