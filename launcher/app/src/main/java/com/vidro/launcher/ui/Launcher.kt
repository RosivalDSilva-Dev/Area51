package com.vidro.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidro.launcher.data.AppEntry
import com.vidro.launcher.data.AppRepository
import com.vidro.launcher.data.GlassTuning
import com.vidro.launcher.data.LauncherState
import com.vidro.launcher.data.normalize
import com.vidro.launcher.glass.Backdrop
import com.vidro.launcher.glass.GlassSurface
import com.vidro.launcher.glass.LocalGlassLight
import com.vidro.launcher.glass.LocalGlassStyle
import com.vidro.launcher.glass.backdropSource
import com.vidro.launcher.glass.rememberBackdrop
import com.vidro.launcher.glass.rememberGravityLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TopGap = 20.dp
private val ClockHeight = 148.dp
private const val HomeColumns = 4
private const val HomeMax = 16
private const val DockMax = 5

private fun Rect.toAndroid() = android.graphics.Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

@Composable
fun LauncherRoot(repo: AppRepository, state: LauncherState, homeTicks: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by repo.apps.collectAsState()
    val byKey = remember(apps) { apps.associateBy { it.key } }

    LaunchedEffect(apps) {
        state.ensureDefaults(apps, repo.defaultDockPackages(apps.map { it.pkg }.toSet()))
    }
    LaunchedEffect(Unit) { state.loadWallpaper() }

    val backdrop = rememberBackdrop()
    val light = rememberGravityLight()
    val style = state.tuning.toStyle()

    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    var tuningOpen by remember { mutableStateOf(false) }
    val drawer = remember { Animatable(0f) }
    var query by remember { mutableStateOf("") }
    var focusTick by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current

    fun closeDrawer() {
        keyboard?.hide()
        scope.launch { drawer.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 380f)) }
        query = ""
    }

    fun openDrawer(focus: Boolean) {
        scope.launch { drawer.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 380f)) }
        if (focus) focusTick++
    }

    LaunchedEffect(homeTicks) {
        if (homeTicks > 0) {
            menu = null
            tuningOpen = false
            closeDrawer()
        }
    }

    BackHandler(enabled = true) {
        when {
            menu != null -> menu = null
            tuningOpen -> tuningOpen = false
            drawer.targetValue > 0f -> closeDrawer()
            else -> Unit
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { state.setWallpaperFromUri(uri) }
    }

    fun launchApp(app: AppEntry, bounds: Rect) {
        repo.launch(app, bounds.toAndroid())
        scope.launch {
            delay(450)
            keyboard?.hide()
            drawer.snapTo(0f)
            query = ""
        }
    }

    val homeApps = state.home.orEmpty().mapNotNull { byKey[it] }
    val dockApps = state.dock.orEmpty().mapNotNull { byKey[it] }

    CompositionLocalProvider(LocalGlassStyle provides style, LocalGlassLight provides light) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    var active = false
                    detectVerticalDragGestures(
                        onDragStart = { active = drawer.value == 0f && menu == null && !tuningOpen },
                        onDragEnd = {
                            if (active) {
                                if (drawer.value > 0.22f) openDrawer(false) else closeDrawer()
                            }
                            active = false
                        },
                        onDragCancel = {
                            if (active) closeDrawer()
                            active = false
                        },
                        onVerticalDrag = { change, dy ->
                            if (active) {
                                change.consume()
                                val v = (drawer.value - dy / (size.height * 0.75f)).coerceIn(0f, 1f)
                                scope.launch { drawer.snapTo(v) }
                            }
                        },
                    )
                }
        ) {
            val heightPx = constraints.maxHeight.toFloat()
            val progress = drawer.value

            // 1) O que fica atrás do vidro: papel de parede + ícones da tela inicial
            Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { tuningOpen = true }) }
                ) {
                    Wallpaper(state.wallpaper)
                }
                HomeGrid(
                    apps = homeApps,
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - drawer.value * 0.7f
                        val s = 1f - drawer.value * 0.06f
                        scaleX = s
                        scaleY = s
                    },
                    onClick = ::launchApp,
                    onLongClick = { app, r -> menu = MenuRequest(app, r, MenuSource.HOME) },
                )
            }

            // 2) Peças de vidro da tela inicial
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp)
                    .graphicsLayer {
                        alpha = (1f - drawer.value * 1.6f).coerceIn(0f, 1f)
                        translationY = drawer.value * 60f
                    },
            ) {
                Spacer(Modifier.height(TopGap))
                ClockWidget(backdrop, Modifier.fillMaxWidth().height(ClockHeight))
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GlassSurface(
                        backdrop,
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { openDrawer(true) },
                        cornerRadius = 23.dp,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pesquisar", style = LabelStyle.copy(fontSize = 15.sp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                GlassSurface(
                    backdrop,
                    Modifier
                        .fillMaxWidth()
                        .height(94.dp),
                    cornerRadius = 36.dp,
                ) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dockApps.forEach { app ->
                            AppIcon(
                                app = app,
                                iconSize = 60.dp,
                                showLabel = false,
                                onClick = { r -> launchApp(app, r) },
                                onLongClick = { r -> menu = MenuRequest(app, r, MenuSource.DOCK) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 3) Gaveta de apps (puxe para cima)
            if (progress > 0.001f) {
                AppDrawer(
                    backdrop = backdrop,
                    offsetPx = ((1f - progress) * heightPx).roundToInt(),
                    apps = apps,
                    query = query,
                    onQuery = { query = it },
                    focusTick = focusTick,
                    onLaunch = ::launchApp,
                    onLongClick = { app, r -> menu = MenuRequest(app, r, MenuSource.DRAWER) },
                    onHandleDrag = { dy ->
                        val v = (drawer.value - dy / (heightPx * 0.75f)).coerceIn(0f, 1f)
                        scope.launch { drawer.snapTo(v) }
                    },
                    onHandleRelease = { if (drawer.value < 0.8f) closeDrawer() else openDrawer(false) },
                )
            }

            // 4) Menu de toque longo
            menu?.let { req ->
                val key = req.app.key
                val inHome = key in state.home.orEmpty()
                val inDock = key in state.dock.orEmpty()
                AppMenuOverlay(
                    backdrop = backdrop,
                    request = req,
                    actions = menuActions(
                        request = req,
                        inHome = inHome,
                        inDock = inDock,
                        onToggleHome = {
                            val cur = state.home.orEmpty()
                            state.updateHome(if (inHome) cur - key else (cur + key).takeLast(HomeMax))
                        },
                        onToggleDock = {
                            val cur = state.dock.orEmpty()
                            state.updateDock(if (inDock) cur - key else (cur.take(DockMax - 1) + key))
                        },
                        onInfo = { repo.showAppInfo(req.app) },
                        onUninstall = { repo.uninstall(req.app) },
                    ),
                    onDismiss = { menu = null },
                )
            }

            // 5) Painel de calibração
            if (tuningOpen) {
                TuningSheet(
                    backdrop = backdrop,
                    tuning = state.tuning,
                    onChange = { state.updateTuning(it) },
                    onPickWallpaper = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onSystemWallpaper = {
                        scope.launch {
                            try {
                                state.copySystemWallpaper()
                            } catch (e: SecurityException) {
                                Toast.makeText(
                                    context,
                                    "Ative \"Acesso a todos os arquivos\" para o Vidro e toque de novo",
                                    Toast.LENGTH_LONG,
                                ).show()
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Throwable) {
                                }
                            } catch (t: Throwable) {
                                Toast.makeText(context, "Não consegui copiar: ${t.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onResetWallpaper = { state.resetWallpaper() },
                    onResetTuning = { state.updateTuning(GlassTuning()) },
                    onSetDefaultLauncher = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Throwable) {
                        }
                    },
                    onDismiss = { tuningOpen = false },
                )
            }
        }
    }
}

@Composable
private fun HomeGrid(
    apps: List<AppEntry>,
    modifier: Modifier,
    onClick: (AppEntry, Rect) -> Unit,
    onLongClick: (AppEntry, Rect) -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(TopGap + ClockHeight + 26.dp))
        apps.chunked(HomeColumns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    AppIcon(
                        app = app,
                        modifier = Modifier.weight(1f),
                        onClick = { r -> onClick(app, r) },
                        onLongClick = { r -> onLongClick(app, r) },
                    )
                }
                repeat(HomeColumns - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AppDrawer(
    backdrop: Backdrop,
    offsetPx: Int,
    apps: List<AppEntry>,
    query: String,
    onQuery: (String) -> Unit,
    focusTick: Int,
    onLaunch: (AppEntry, Rect) -> Unit,
    onLongClick: (AppEntry, Rect) -> Unit,
    onHandleDrag: (Float) -> Unit,
    onHandleRelease: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusTick) {
        if (focusTick > 0) {
            delay(120)
            try {
                focus.requestFocus()
            } catch (_: Throwable) {
            }
        }
    }
    val needle = normalize(query.trim())
    val shown = if (needle.isEmpty()) apps else apps.filter { it.searchText.contains(needle) }

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetPx) }
    ) {
        GlassSurface(
            backdrop,
            Modifier.fillMaxSize(),
            style = LocalGlassStyle.current.copy(
                blur = 26.dp,
                bevel = 34.dp,
                tint = Color.Black.copy(alpha = 0.16f),
            ),
            cornerRadius = 40.dp,
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = onHandleRelease,
                                onDragCancel = onHandleRelease,
                                onVerticalDrag = { change, dy ->
                                    change.consume()
                                    onHandleDrag(dy)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.6f))
                    )
                }
                Row(
                    Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = LabelStyle.copy(fontSize = 17.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            shown.firstOrNull()?.let { onLaunch(it, Rect.Zero) }
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) {
                                    Text("Pesquisar apps", style = LabelStyle.copy(fontSize = 17.sp, color = Color.White.copy(alpha = 0.6f)))
                                }
                                inner()
                            }
                        },
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.key }) { app ->
                        AppIcon(
                            app = app,
                            onClick = { r -> onLaunch(app, r) },
                            onLongClick = { r -> onLongClick(app, r) },
                        )
                    }
                }
            }
        }
    }
}
