package com.ljyh.mei.ui.component.player.overlay

import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.navigation.MeiNavEntryViewModelStoreOwner
import com.ljyh.mei.ui.navigation.MeiNavigator
import com.ljyh.mei.ui.navigation.MeiRoute

/** Full-screen page above an unchanged player, with its own nested navigation lifetime. */
@Composable
internal fun PlayerPageOverlay(
    initialRoute: String,
    onDismiss: () -> Unit,
    content: @Composable (String) -> Unit,
) {
    val context = LocalContext.current
    val background = LocalGlassColors.current.groupedBackground
    val activity = checkNotNull(context.overlayActivity())
    val dismiss by rememberUpdatedState(onDismiss)
    val stack = rememberNavBackStack(MeiRoute(initialRoute))
    val navigator = remember(context, stack) {
        MeiNavigator(context, stack, onRootBack = { dismiss(); true })
    }
    val owners = remember { mutableMapOf<String, MeiNavEntryViewModelStoreOwner>() }
    DisposableEffect(owners) {
        onDispose { owners.values.forEach { it.clear() }; owners.clear() }
    }
    val ownerDecorator = remember(activity, owners) {
        NavEntryDecorator<NavKey>(
            onPop = { key -> owners.remove(key.toString())?.clear() },
            decorate = { entry ->
                val owner = owners.getOrPut(entry.contentKey.toString()) {
                    MeiNavEntryViewModelStoreOwner(activity)
                }
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { entry.Content() }
            },
        )
    }
    Dialog(
        onDismissRequest = { navigator.popBackStack() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view, background) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            window?.setDimAmount(0f)
            window?.let {
                WindowInsetsControllerCompat(it, view).apply {
                    isAppearanceLightStatusBars = background.luminance() > 0.5f
                    isAppearanceLightNavigationBars = background.luminance() > 0.5f
                }
            }
            onDispose { }
        }
        CompositionLocalProvider(
            LocalNavController provides navigator,
            LocalPlayerAwareWindowInsets provides WindowInsets.systemBars,
        ) {
            Box(Modifier.fillMaxSize().background(background)) {
                NavDisplay(
                    backStack = stack,
                    onBack = { navigator.popBackStack() },
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), ownerDecorator),
                    entryProvider = { key ->
                        val route = (key as MeiRoute).route
                        NavEntry(key = key, contentKey = route) { content(route) }
                    },
                )
            }
        }
    }
}

private tailrec fun Context.overlayActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.overlayActivity()
    else -> null
}
