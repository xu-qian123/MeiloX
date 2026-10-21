package com.ljyh.mei.ui.component.player

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ljyh.mei.ui.component.player.overlay.PlayerPageOverlay
import com.ljyh.mei.ui.component.sheet.BottomSheet
import com.ljyh.mei.ui.component.sheet.BottomSheetState
import com.ljyh.mei.ui.component.sheet.LocalPlayerSheet
import com.ljyh.mei.ui.component.sheet.expandedAnchor
import com.ljyh.mei.ui.component.sheet.rememberBottomSheetState
import com.ljyh.mei.ui.component.sheet.rememberPlayerSheetLayers
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.navigation.MeiNavigator
import com.ljyh.mei.ui.navigation.MeiRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNavigationDeviceTest {
    @Test fun commentsCoverPlaybackWithoutCollapsingIt() = exerciseOverlay(false)
    @Test fun commentsCoverLyricsAndBackThenReturnsToPlayback() = exerciseOverlay(true)

    private fun exerciseOverlay(lyricsInitiallyVisible: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n ${instrumentation.targetContext.packageName}/androidx.activity.ComponentActivity",
        ).use { descriptor -> ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } }
        lateinit var activity: ComponentActivity
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ComponentActivity>().single()
        }
        val visible = mutableStateOf(false)
        val lyrics = mutableStateOf(lyricsInitiallyVisible)
        lateinit var sheet: BottomSheetState
        lateinit var pageNavigator: MeiNavigator
        lateinit var rootNavigator: MeiNavigator
        var pageRoute: String? = null
        var hostReady = false
        var overlayIsComposed = false
        fun awaitCondition(message: String, predicate: () -> Boolean) {
            val deadline = android.os.SystemClock.uptimeMillis() + 10_000
            while (android.os.SystemClock.uptimeMillis() < deadline) {
                var ready = false
                instrumentation.runOnMainSync { ready = predicate() }
                if (ready) return
                Thread.sleep(20)
            }
            throw AssertionError(message)
        }
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    val rootStack = rememberNavBackStack(MeiRoute("library"))
                    rootNavigator = remember { MeiNavigator(activity, rootStack) }
                    sheet = rememberBottomSheetState(0.dp, 700.dp, 80.dp, expandedAnchor)
                    CompositionLocalProvider(
                        LocalNavController provides rootNavigator,
                        LocalPlayerSheet provides rememberPlayerSheetLayers(sheet),
                    ) {
                        BottomSheet(
                            state = sheet,
                            transitionBackdrop = rememberLayerBackdrop(),
                            onBack = { if (lyrics.value) lyrics.value = false else sheet.collapseSoft() },
                            collapsedContent = { Text("Mini player") },
                        ) { Text(if (lyrics.value) "Lyrics" else "Playback") }
                        if (visible.value) {
                            PlayerPageOverlay("comment/123", onDismiss = { visible.value = false }) { route ->
                                val navigator = LocalNavController.current
                                Text(route)
                                SideEffect { pageNavigator = navigator; pageRoute = route }
                            }
                        }
                    }
                    SideEffect { hostReady = true; overlayIsComposed = visible.value }
                }
            }
            awaitCondition("Expanded player must be ready") { hostReady && sheet.isExpanded }
            instrumentation.runOnMainSync { visible.value = true }
            awaitCondition("Comments must be visible") { pageRoute == "comment/123" }
            instrumentation.runOnMainSync {
                assertTrue(sheet.isExpanded)
                assertEquals("library", rootNavigator.currentRoute)
                assertEquals(1, rootNavigator.backStack.size)
                pageNavigator.navigate("netease_login")
            }
            awaitCondition("Login must stay inside the comments overlay") { pageRoute == "netease_login" }
            instrumentation.runOnMainSync {
                assertTrue(sheet.isExpanded)
                assertEquals("library", rootNavigator.currentRoute)
                pageNavigator.popBackStack()
            }
            // NavDisplay keeps outgoing entries during its animation; their SideEffects
            // are not an authoritative indication of the active route after a pop.
            awaitCondition("Nested Back must return to comments") { pageNavigator.currentRoute == "comment/123" }
            instrumentation.runOnMainSync { pageNavigator.popBackStack() }
            awaitCondition("Root Back must dispose the overlay window") { !visible.value && !overlayIsComposed }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue(sheet.isExpanded)
                assertEquals(lyricsInitiallyVisible, lyrics.value)
                assertEquals("library", rootNavigator.currentRoute)
            }
            if (lyricsInitiallyVisible) {
                instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
                awaitCondition("Lyrics Back must return to playback") { !lyrics.value && sheet.isExpanded }
            }
            instrumentation.runOnMainSync {
                activity.onBackPressedDispatcher.onBackPressed()
                assertTrue(sheet.targetAnchor != expandedAnchor)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
