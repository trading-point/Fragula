package com.fragula2.compose

import android.animation.TimeInterpolator
import android.util.Log
import android.view.animation.DecelerateInterpolator
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.*
import androidx.navigation.compose.DialogHost
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.LocalOwnersProvider
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState

@Composable
fun FragulaNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    route: String? = null,
    builder: NavGraphBuilder.() -> Unit
) {
    FragulaNavHost(
        navController.apply {
            navigatorProvider.addNavigator(SwipeBackNavigator())
        },
        remember(route, startDestination, builder) {
            navController.createGraph(startDestination, route, builder)
        },
        modifier
    )
}

fun NavGraphBuilder.swipeable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit
) {
    addDestination(
        SwipeBackNavigator.Destination(provider[SwipeBackNavigator::class], content).apply {
            this.route = route
            arguments.forEach { (argumentName, argument) ->
                addArgument(argumentName, argument)
            }
            deepLinks.forEach { deepLink ->
                addDeepLink(deepLink)
            }
        }
    )
}

@Composable
@OptIn(ExperimentalPagerApi::class)
fun FragulaNavHost(
    navController: NavHostController,
    graph: NavGraph,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "FragulaNavHost requires a ViewModelStoreOwner to be provided via LocalViewModelStoreOwner"
    }
    val onBackPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    val onBackPressedDispatcher = onBackPressedDispatcherOwner?.onBackPressedDispatcher

    navController.setLifecycleOwner(lifecycleOwner)
    navController.setViewModelStore(viewModelStoreOwner.viewModelStore)
    if (onBackPressedDispatcher != null) {
        navController.setOnBackPressedDispatcher(onBackPressedDispatcher)
    }

    DisposableEffect(navController) {
        navController.enableOnBackPressed(true)
        onDispose {
            navController.enableOnBackPressed(false)
        }
    }

    navController.graph = graph

    // Custom navigation

    val swipeBackNavigator = navController.navigatorProvider
        .get<Navigator<out NavDestination>>(SwipeBackNavigator.NAME) as? SwipeBackNavigator
        ?: return

    val backStack by swipeBackNavigator.backStack.collectAsState()
    val saveableStateHolder = rememberSaveableStateHolder()
    val pagerState = rememberPagerState()

    var scrollToEnd by remember { mutableStateOf(false) }
    var scrollOffset by remember { mutableStateOf(0f) }
    var renderOffset by remember { mutableStateOf(0f) }
    var previousPage by remember { mutableStateOf(0) }

    HorizontalPager(
        count = backStack.size,
        // key = { backStack[it].id },
        state = pagerState,
        userScrollEnabled = renderOffset > 0.5f || renderOffset == 0f
    ) { page ->
        val entry = backStack[page]
        val destination = entry.destination as SwipeBackNavigator.Destination

        scrollToEnd = currentPage + currentPageOffset < scrollOffset
        scrollOffset = currentPage + currentPageOffset
        renderOffset = if (currentPageOffset < 0) 1 + currentPageOffset else currentPageOffset

        Log.d("Fragula", "renderOffset = $renderOffset")

        /**
         * Call popBackStack() when user swipe-out screen
         */
        if (renderOffset <= 0) {
            if (previousPage < pagerState.currentPage) {
                Log.d("Fragula", "Swipe to right ->")
            } else if (previousPage > pagerState.currentPage) {
                Log.d("Fragula", "Swipe to left <-")
                navController.popBackStack()
            }
            previousPage = pagerState.currentPage
        }

        /**
         * User's @Composable content
         * TODO add Modifier with alpha/translationX animation
         */
        Box {
            entry.LocalOwnersProvider(saveableStateHolder) {
                destination.content(entry)
            }
        }

        /**
         * Scroll to selected screen
         */
        LaunchedEffect(entry) {
            val pageIndex = backStack.indexOfFirst { it.id == entry.id }
            if (pageIndex > -1) {
                pagerState.animateScrollToPage(
                    page = pageIndex,
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = DecelerateInterpolator(1.78f).toEasing() // TODO doesn't work
                    )
                )
            }
        }
    }

    /**
     * Draw elevation effect
     */
    if (renderOffset > 0) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(modifier = Modifier.fillMaxHeight()
                .requiredWidth(3.dp)
                .offset(x = -(renderOffset * maxWidth.value).dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(ElevationEnd, ElevationStart)
                    )
                )
            )
        }
    }

    val dialogNavigator = navController.navigatorProvider
        .get<Navigator<out NavDestination>>("dialog") as? DialogNavigator // DialogNavigator.NAME
        ?: return

    DialogHost(dialogNavigator)
}

private fun TimeInterpolator.toEasing() = Easing { x -> getInterpolation(x) }