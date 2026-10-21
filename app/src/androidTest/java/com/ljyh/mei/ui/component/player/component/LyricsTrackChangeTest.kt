package com.ljyh.mei.ui.component.player.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.compose.ui.unit.dp
import com.ljyh.mei.utils.lyric.YRCParser
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTrackChangeTest {
    @Test
    fun changingTracksRendersWrappedKaraokeLyricsWithoutLinkageErrors() {
        val track = mutableIntStateOf(0)
        val laidOut = List(2) { CountDownLatch(1) }
        val lyrics = listOf(
            YRCParser.parse("[1000,4000](1000,2000,0)A long English lyric (3000,2000,0)with trailing spaces ", null, null),
            YRCParser.parse("[1000,4000](1000,2000,0)切换歌曲之后 (3000,2000,0)继续显示逐字歌词 ", null, null),
        )
        // This Android 17 device rejects app-originated test activity launches, and its
        // InputManager API is incompatible with Espresso. Launch via the shell and use
        // platform lifecycle/layout synchronization instead of input injection.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetPackage = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n $targetPackage/androidx.activity.ComponentActivity",
        ).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        lateinit var activity: ComponentActivity
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ComponentActivity>()
                .single()
        }
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    MaterialTheme {
                        key(track.intValue) {
                            Box(Modifier.width(180.dp).height(500.dp).onGloballyPositioned {
                                if (it.size.width > 0 && it.size.height > 0) {
                                    laidOut[track.intValue].countDown()
                                }
                            }) {
                                PlayerLyricsView(
                                    listState = rememberLazyListState(),
                                    lyrics = lyrics[track.intValue],
                                    currentPosition = { 1500 },
                                    onLineClicked = {},
                                    onLinePressed = {},
                                    isManualScrolling = false,
                                )
                            }
                        }
                    }
                }
            }
            assertTrue("First track must complete lyric layout", laidOut[0].await(15, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { track.intValue = 1 }
            assertTrue("Next track must complete lyric layout", laidOut[1].await(15, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
