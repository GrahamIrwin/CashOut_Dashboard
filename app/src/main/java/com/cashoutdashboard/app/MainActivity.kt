package com.cashoutdashboard.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cashoutdashboard.app.ui.CashoutTheme
import com.cashoutdashboard.app.ui.DashboardScreen
import com.cashoutdashboard.app.ui.IconBadge
import com.cashoutdashboard.app.ui.ReviewScreen
import com.cashoutdashboard.app.ui.SettingsScreen
import com.cashoutdashboard.app.ui.ShiftDetailScreen
import com.cashoutdashboard.app.ui.ShiftsScreen
import java.io.File

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    DASHBOARD("dashboard", "Dashboard", Icons.Outlined.Insights, Icons.Filled.Insights),
    SHIFTS("shifts", "Shifts", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

// Quick, quiet motion: tabs cross-fade; pushed screens slide in a short way from the right.
private val fadeInFast = fadeIn(tween(150))
private val fadeOutFast = fadeOut(tween(100))
private val pushIn = slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 8 } + fadeIn(tween(180))
private val popOut = slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { it / 8 } + fadeOut(tween(150))

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var pendingShare by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            CashoutTheme {
                Surface(color = MaterialTheme.colorScheme.background) { App(vm) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Photos shared from the gallery ("Share → Cashout") go straight into the scan queue. */
    private fun handleShare(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            )
            Intent.ACTION_SEND_MULTIPLE ->
                (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            vm.scan(uris)
            pendingShare = true
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun App(vm: AppViewModel) {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val tab = Tab.entries.firstOrNull { it.route == route }
        val queue by vm.queue.collectAsStateWithLifecycle()
        val shifts by vm.shifts.collectAsStateWithLifecycle()
        val message by vm.messages.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        var sheet by remember { mutableStateOf(false) }
        var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
        val colors = MaterialTheme.colorScheme
        // The FAB shrinks to its icon while scrolling down so it doesn't sit on top of content.
        var fabExpanded by remember { mutableStateOf(true) }
        val fabScroll = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < -1f) fabExpanded = false else if (available.y > 1f) fabExpanded = true
                    return Offset.Zero
                }
            }
        }
        LaunchedEffect(tab) { fabExpanded = true }

        LaunchedEffect(message) {
            message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
        }
        LaunchedEffect(pendingShare) {
            if (pendingShare) { nav.navigate("review"); pendingShare = false }
        }

        val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val uri = cameraUri
            if (ok && uri != null) { vm.scan(listOf(uri)); nav.navigate("review") }
        }
        val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
            if (uris.isNotEmpty()) { vm.scan(uris); nav.navigate("review") }
        }

        Scaffold(
            containerColor = colors.background,
            topBar = {
                // The dashboard pins its own period/tab header, so it skips the title bar to save space.
                if (tab != null && tab != Tab.DASHBOARD) {
                    TopAppBar(
                        title = { Text(tab.label, style = MaterialTheme.typography.headlineSmall) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                    )
                }
            },
            bottomBar = {
                if (tab != null) {
                    Column {
                        HorizontalDivider(color = colors.outlineVariant)
                        NavigationBar(containerColor = colors.surfaceContainerLow, tonalElevation = 0.dp) {
                            Tab.entries.forEach { t ->
                                val selected = t == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        nav.navigate(t.route) {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(if (selected) t.selectedIcon else t.icon, null) },
                                    label = { Text(t.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colors.onPrimaryContainer,
                                        selectedTextColor = colors.onSurface,
                                        indicatorColor = colors.primaryContainer,
                                        unselectedIconColor = colors.onSurfaceVariant,
                                        unselectedTextColor = colors.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                // With no shifts yet, the empty state carries its own "Add cashout" button instead.
                if ((tab == Tab.DASHBOARD || tab == Tab.SHIFTS) && shifts.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = { sheet = true },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("Add cashout") },
                        expanded = fabExpanded,
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { pad ->
            val inner = if (tab != null) Modifier.padding(pad) else Modifier
            Column(inner.nestedScroll(fabScroll)) {
                AnimatedVisibility(tab != null && queue.isNotEmpty()) {
                    QueueBanner(queue.size, queue.count { it.status == ScanStatus.PROCESSING }) { nav.navigate("review") }
                }
                NavHost(
                    nav,
                    startDestination = Tab.DASHBOARD.route,
                    enterTransition = { fadeInFast },
                    exitTransition = { fadeOutFast },
                    popEnterTransition = { fadeInFast },
                    popExitTransition = { fadeOutFast },
                ) {
                    composable(Tab.DASHBOARD.route) {
                        DashboardScreen(vm, onOpenShift = { nav.navigate("shift/$it") }, onAdd = { sheet = true })
                    }
                    composable(Tab.SHIFTS.route) { ShiftsScreen(vm, onOpen = { nav.navigate("shift/$it") }, onAdd = { sheet = true }) }
                    composable(Tab.SETTINGS.route) { SettingsScreen(vm) }
                    composable("review", enterTransition = { pushIn }, popExitTransition = { popOut }) {
                        ReviewScreen(vm, onDone = { nav.popIf("review") })
                    }
                    composable("shift/{id}", enterTransition = { pushIn }, popExitTransition = { popOut }) { entry ->
                        ShiftDetailScreen(vm, entry.arguments?.getString("id")!!, onBack = { nav.popIf("shift/{id}") })
                    }
                }
            }
        }

        if (sheet) {
            ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = colors.surfaceContainerLow) {
                Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                    Text(
                        "Add a cashout",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                    )
                    SheetItem(Icons.Default.PhotoCamera, "Take a photo", "Snap tonight's cashout slip") {
                        sheet = false
                        val dir = File(cacheDir, "camera").apply { mkdirs() }
                        val file = File(dir, "cashout_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                        cameraUri = uri
                        camera.launch(uri)
                    }
                    SheetItem(Icons.Default.PhotoLibrary, "Choose from gallery", "Pick one or many — great for old slips") {
                        sheet = false
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    SheetItem(Icons.Default.Edit, "Enter manually", "Type the numbers in yourself") {
                        sheet = false
                        vm.newManualEntry()
                        nav.navigate("review")
                    }
                }
            }
        }
    }

    @Composable
    private fun QueueBanner(count: Int, processing: Int, onReview: () -> Unit) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onReview)
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (processing > 0) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                if (processing > 0) "Reading $processing of $count…" else "$count cashout${if (count == 1) "" else "s"} ready to review",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReview) { Text("Review") }
        }
    }

    @Composable
    private fun SheetItem(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, 44.dp)
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Pops only if [route] is on top, so a screen that closes itself twice can't pop its parent. */
private fun NavHostController.popIf(route: String) {
    if (currentBackStackEntry?.destination?.route == route) popBackStack()
}
