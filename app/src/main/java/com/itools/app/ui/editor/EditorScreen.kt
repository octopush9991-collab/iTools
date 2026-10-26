package com.itools.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import com.itools.app.IToolsApp
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.formatDuration
import com.itools.app.core.model.formatTimecode
import com.itools.app.core.render.CompositionFactory
import com.itools.app.core.settings.AppSettings
import com.itools.app.core.preview.PreviewController
import com.itools.app.core.render.ExportEngine
import com.itools.app.ui.components.SmallCaps
import com.itools.app.ui.components.ToolTile
import com.itools.app.ui.theme.IToolsPalette
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = context.applicationContext as IToolsApp
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings()
    )

    val compositionFactory = remember(app) { CompositionFactory(context, app.store) }
    val preview = remember(compositionFactory) { PreviewController(context, compositionFactory) }
    val player by preview.player.collectAsStateWithLifecycle()
    val isPlaying by preview.isPlaying.collectAsStateWithLifecycle()
    val previewError by preview.error.collectAsStateWithLifecycle()
    val latestProject by rememberUpdatedState(state.project)

    val snackbar = remember { SnackbarHostState() }
    var activeTool by remember { mutableStateOf<EditorTool?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) {
        onDispose { preview.release() }
    }

    // Rebuild the CompositionPlayer after edits settle. Slider ticks produce many previewVersion
    // bumps; the debounce keeps them from starting a decoder per tick.
    LaunchedEffect(state.previewVersion) {
        if (state.previewVersion <= 0L) return@LaunchedEffect
        delay(330)
        latestProject?.let { preview.setProject(it) }
    }

    LaunchedEffect(preview) {
        preview.positionMs.collect { position ->
            viewModel.onPreviewPosition(position)
        }
    }

    LaunchedEffect(state.message, state.error, state.project) {
        if (state.project == null) return@LaunchedEffect
        val text = state.error ?: state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.clearTransientMessages()
    }

    LaunchedEffect(exportState) {
        when (val result = exportState) {
            is ExportEngine.State.Success -> viewModel.showMessage("Ekspor selesai: ${result.displayName}")
            is ExportEngine.State.Failure -> viewModel.showMessage(result.message)
            else -> Unit
        }
    }

    val seekTo: (Long) -> Unit = remember(preview) {
        { position ->
            preview.pause()
            viewModel.setPlayhead(position)
            preview.notePosition(position)
            preview.seekTo(position)
        }
    }

    BackHandler {
        when {
            activeTool != null -> {
                viewModel.endTransaction()
                activeTool = null
            }
            renameOpen -> renameOpen = false
            else -> {
                viewModel.endTransaction()
                preview.pause()
                viewModel.saveNow()
                onBack()
            }
        }
    }

    val project = state.project
    if (project == null) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IToolsPalette.Accent)
            }
        } else {
            EditorLoadError(
                message = state.error ?: "Proyek tidak dapat dibuka.",
                onRetry = { viewModel.load() },
                onBack = onBack
            )
        }
        return
    }

    val selected = state.selectedClipId?.let { project.clip(it) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            EditorTopBar(
                projectName = project.name,
                ratioLabel = project.canvas.ratioLabel,
                undoCount = state.undoCount,
                redoCount = state.redoCount,
                onBack = {
                    viewModel.endTransaction()
                    preview.pause()
                    viewModel.saveNow()
                    onBack()
                },
                onRename = { renameOpen = true },
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onSave = {
                    viewModel.saveNow()
                    viewModel.showMessage("Proyek disimpan.")
                },
                onExport = { activeTool = EditorTool.EXPORT }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            state.busy?.let { busy ->
                Surface(color = IToolsPalette.Accent.copy(alpha = 0.10f)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
                        Text(
                            text = busy,
                            style = MaterialTheme.typography.labelMedium,
                            color = IToolsPalette.Accent
                        )
                        Spacer(Modifier.height(5.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = IToolsPalette.Accent,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }

            PreviewStage(
                player = player,
                project = project,
                background = Color(project.canvas.background),
                showSafeArea = settings.showSafeArea,
                error = previewError,
                playheadMs = state.playheadMs,
                onRetry = { latestProject?.let { preview.setProject(it) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )

            TransportBar(
                playheadMs = state.playheadMs,
                durationMs = project.durationMs,
                isPlaying = isPlaying,
                canStep = player != null,
                onPlayPause = {
                    if (player == null) {
                        viewModel.showMessage("Pratinjau sedang disiapkan.")
                    } else {
                        preview.playPause()
                    }
                },
                onStepBack = {
                    preview.pause()
                    viewModel.stepFrame(-1)
                    seekTo(viewModel.state.value.playheadMs)
                },
                onStepForward = {
                    preview.pause()
                    viewModel.stepFrame(1)
                    seekTo(viewModel.state.value.playheadMs)
                },
                onSplit = { viewModel.splitAtPlayhead() }
            )

            selected?.let { clip ->
                SelectedClipActions(
                    clip = clip,
                    onSplit = { viewModel.splitAtPlayhead() },
                    onDuplicate = { viewModel.duplicateSelected() },
                    onMute = { viewModel.toggleSelectedClipMuted() },
                    onDelete = { viewModel.deleteSelected() }
                )
            }

            Surface(color = Color(0xFF0D1214)) {
                TimelineView(
                    project = project,
                    selectedClipId = state.selectedClipId,
                    playheadMs = state.playheadMs,
                    dpPerSecond = 34f,
                    onSelectClip = viewModel::selectClip,
                    onScrub = seekTo,
                    onMoveClip = { id, delta -> viewModel.moveClipBy(id, delta) },
                    onTrimStart = { id, delta -> viewModel.trimStartBy(id, delta) },
                    onTrimEnd = { id, delta -> viewModel.trimEndBy(id, delta) },
                    onGestureStart = viewModel::beginTransaction,
                    onGestureEnd = viewModel::endTransaction,
                    onToggleMute = viewModel::toggleTrackMuted,
                    onToggleHidden = viewModel::toggleTrackHidden,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 250.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            ToolStrip(
                activeTool = activeTool,
                onSelect = { tool ->
                    preview.pause()
                    // Finish any slider gesture left open by the previous panel before switching.
                    viewModel.endTransaction()
                    activeTool = tool
                }
            )
        }
    }

    val tool = activeTool
    if (tool != null) {
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.endTransaction()
                activeTool = null
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            when (tool) {
                EditorTool.MEDIA -> MediaPanel(
                    project = project,
                    selected = selected,
                    viewModel = viewModel,
                    busy = state.busy != null,
                    onSeek = seekTo
                )
                EditorTool.AUDIO -> AudioPanel(
                    project = project,
                    selected = selected,
                    viewModel = viewModel,
                    busy = state.busy != null,
                    onSeek = seekTo
                )
                EditorTool.TEXT -> TextPanel(selected, viewModel)
                EditorTool.STICKER -> StickerPanel(selected, viewModel)
                EditorTool.FILTER -> FilterPanel(selected, viewModel)
                EditorTool.ADJUST -> AdjustPanel(selected, viewModel)
                EditorTool.TRANSFORM -> TransformPanel(selected, viewModel)
                EditorTool.SPEED -> SpeedPanel(selected, viewModel)
                EditorTool.TRANSITION -> TransitionPanel(selected, viewModel)
                EditorTool.MASK -> MaskPanel(selected, viewModel)
                EditorTool.CHROMA -> ChromaPanel(selected, viewModel)
                EditorTool.KEYFRAME -> KeyframePanel(
                    selected = selected,
                    playheadMs = state.playheadMs,
                    viewModel = viewModel,
                    onSeek = seekTo
                )
                EditorTool.CANVAS -> CanvasPanel(project.canvas, viewModel)
                EditorTool.CAPTION -> CaptionPanel(
                    project = project,
                    selectedClipId = state.selectedClipId,
                    busy = state.busy != null,
                    viewModel = viewModel,
                    onSeek = seekTo
                )
                EditorTool.LOCAL_AI -> LocalToolsPanel(
                    project = project,
                    selected = selected,
                    busy = state.busy != null,
                    viewModel = viewModel
                )
                EditorTool.EXPORT -> ExportPanel(
                    project = project,
                    exportState = exportState,
                    busy = state.busy != null,
                    viewModel = viewModel
                )
            }
        }
    }

    if (renameOpen) {
        var name by remember(project.id) { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Ganti nama proyek") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameProject(name)
                        renameOpen = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun EditorTopBar(
    projectName: String,
    ratioLabel: String,
    undoCount: Int,
    redoCount: Int,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRename)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                SmallCaps("$ratioLabel • ketuk untuk ganti nama")
            }
            IconButton(onClick = onUndo, enabled = undoCount > 0) {
                Icon(Icons.Default.Undo, contentDescription = "Urungkan")
            }
            IconButton(onClick = onRedo, enabled = redoCount > 0) {
                Icon(Icons.Default.Redo, contentDescription = "Ulangi")
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = "Simpan")
            }
            TextButton(onClick = onExport) {
                Icon(
                    Icons.Default.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Ekspor", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PreviewStage(
    player: androidx.media3.common.Player?,
    project: com.itools.app.core.model.Project,
    background: Color,
    showSafeArea: Boolean,
    error: String?,
    playheadMs: Long,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .aspectRatio(project.canvas.aspectRatio)
                .clip(RoundedCornerShape(12.dp))
                .background(background)
        ) {
            if (player != null) {
                PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator(
                    color = IToolsPalette.Accent,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                )
            }

            if (showSafeArea) {
                SafeAreaGuides()
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(7.dp),
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Text(
                    text = formatTimecode(playheadMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }

            if (error != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = IToolsPalette.Danger.copy(alpha = 0.90f)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "Pratinjau gagal",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        TextButton(onClick = onRetry) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Coba lagi", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeAreaGuides() {
    Canvas(Modifier.fillMaxSize()) {
        val insetX = size.width * 0.05f
        val insetY = size.height * 0.05f
        drawRect(
            color = Color.White.copy(alpha = 0.55f),
            topLeft = Offset(insetX, insetY),
            size = Size(size.width - insetX * 2, size.height - insetY * 2),
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        SmallCaps(
            "Area aman",
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 16.dp, bottom = 14.dp)
        )
    }
}

@Composable
private fun TransportBar(
    playheadMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    canStep: Boolean,
    onPlayPause: () -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onSplit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${formatTimecode(playheadMs)} / ${formatTimecode(durationMs)}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(108.dp)
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onStepBack, enabled = canStep) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Bingkai sebelumnya")
        }
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Jeda" else "Putar"
            )
        }
        IconButton(onClick = onStepForward, enabled = canStep) {
            Icon(Icons.Default.SkipNext, contentDescription = "Bingkai berikutnya")
        }
        IconButton(onClick = onSplit) {
            Icon(Icons.Default.ContentCut, contentDescription = "Potong di playhead")
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = IToolsPalette.TextMuted,
            modifier = Modifier.width(48.dp)
        )
    }
}

@Composable
private fun SelectedClipActions(
    clip: Clip,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${kindLabel(clip.kind)} • ${clip.name}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSplit, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.ContentCut,
                    contentDescription = "Potong klip",
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Duplikat klip",
                    modifier = Modifier.size(17.dp)
                )
            }
            if (clip.kind == ClipKind.AUDIO || clip.kind == ClipKind.VIDEO) {
                IconButton(onClick = onMute, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = if (clip.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (clip.muted) "Aktifkan suara" else "Bisukan klip",
                        tint = if (clip.muted) IToolsPalette.Danger else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Hapus klip",
                    tint = IToolsPalette.Danger,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolStrip(
    activeTool: EditorTool?,
    onSelect: (EditorTool) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorTool.values().forEach { tool ->
                ToolTile(
                    label = tool.label,
                    icon = tool.icon,
                    selected = tool == activeTool,
                    onClick = { onSelect(tool) }
                )
            }
        }
    }
}

@Composable
private fun EditorLoadError(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = IToolsPalette.Danger,
                modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("Proyek tidak dapat dibuka", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onBack) { Text("Kembali") }
                TextButton(onClick = onRetry) { Text("Coba lagi") }
            }
        }
    }
}
