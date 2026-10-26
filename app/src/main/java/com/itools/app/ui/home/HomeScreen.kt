package com.itools.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.itools.app.core.model.CanvasSpec
import com.itools.app.core.model.LocalTemplate
import com.itools.app.core.model.LocalTemplates
import com.itools.app.core.model.formatBytes
import com.itools.app.core.model.formatDuration
import com.itools.app.core.project.ProjectSummary
import com.itools.app.ui.components.SmallCaps
import com.itools.app.ui.theme.IToolsPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNewDialog by remember { mutableStateOf(false) }
    var initialTemplate by remember { mutableStateOf<LocalTemplate?>(null) }
    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenProject -> onOpenProject(event.id)
            }
        }
    }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        IToolsPalette.Accent.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.background
                    ),
                    radius = 1100f
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    HomeHeader(onOpenSettings = onOpenSettings)
                }
                item {
                    HeroPanel(
                        projectCount = state.projects.size,
                        storageBytes = state.storageBytes,
                        onCreate = { initialTemplate = null; showNewDialog = true }
                    )
                }
                if (state.sharedUris.isNotEmpty()) {
                    item {
                        SharedMediaCard(
                            count = state.sharedUris.size,
                            busy = state.busy != null,
                            onCreate = {
                                viewModel.createProject(
                                    name = "Proyek bagikan",
                                    importUris = state.sharedUris
                                )
                            },
                            onDismiss = { viewModel.clearSharedUris() }
                        )
                    }
                }
                item {
                    TemplatesSection(
                        onCreateFrom = { template ->
                            initialTemplate = template
                            showNewDialog = true
                        }
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        SmallCaps("Draf terbaru", modifier = Modifier.weight(1f))
                        Text(
                            text = "${state.projects.size} proyek",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (state.loading) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = IToolsPalette.Accent
                            )
                        }
                    }
                } else if (state.projects.isEmpty()) {
                    item {
                        EmptyProjects(onCreate = { initialTemplate = null; showNewDialog = true })
                    }
                } else {
                    items(items = state.projects, key = { it.id }) { summary ->
                        ProjectRow(
                            summary = summary,
                            onClick = { onOpenProject(summary.id) },
                            onRename = { renameTarget = summary },
                            onDuplicate = { viewModel.duplicateProject(summary.id) },
                            onDelete = { deleteTarget = summary }
                        )
                    }
                }
                item {
                    StoragePanel(bytes = state.storageBytes)
                }
                item {
                    Text(
                        text = "iTools tidak memiliki izin INTERNET. Semua proyek, media, dan hasil ekspor " +
                            "tetap berada di perangkat ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (state.busy != null) {
            BusyOverlay(state.busy!!)
        }
    }

    if (showNewDialog) {
        NewProjectDialog(
            template = initialTemplate,
            onDismiss = { showNewDialog = false },
            onCreate = { name, canvas ->
                showNewDialog = false
                viewModel.createProject(name = name, canvas = canvas)
            }
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            initial = target.name,
            onDismiss = { renameTarget = null },
            onRename = { name ->
                renameTarget = null
                viewModel.renameProject(target.id, name)
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus proyek?") },
            text = {
                Text(
                    "Proyek \"${target.name}\" beserta salinan media di dalamnya akan dihapus. " +
                        "File asli di galeri tidak tersentuh."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.deleteProject(target.id)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = IToolsPalette.Danger)
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IToolsPalette.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "iT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF162000)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "iTools",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = "Edit video. Sepenuhnya offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
        }
    }
}

@Composable
private fun HeroPanel(
    projectCount: Int,
    storageBytes: Long,
    onCreate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SmallCaps("Studio lokal", color = IToolsPalette.Accent)
            Spacer(Modifier.height(7.dp))
            Text(
                text = "Semua fitur terbuka.\nTanpa watermark, tanpa kredit.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Badge("100% lokal")
                Badge("Tanpa iklan")
                Badge("Gratis penuh")
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$projectCount proyek • ${formatBytes(storageBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Editor multi-track, efek, subtitle, ekspor hingga 4K.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onCreate,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IToolsPalette.Accent,
                        contentColor = Color(0xFF162000)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Proyek baru", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = IToolsPalette.Accent.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            IToolsPalette.Accent.copy(alpha = 0.28f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = IToolsPalette.Accent,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SharedMediaCard(
    count: Int,
    busy: Boolean,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = IToolsPalette.SecondaryBlue.copy(alpha = 0.12f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IToolsPalette.SecondaryBlue.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = IToolsPalette.SecondaryBlue
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Media dibagikan ke iTools",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "$count file siap diimpor menjadi satu proyek baru.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCreate,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Buat proyek") }
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Abaikan") }
            }
        }
    }
}

@Composable
private fun TemplatesSection(onCreateFrom: (LocalTemplate) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallCaps("Template lokal", modifier = Modifier.weight(1f))
            Text(
                text = "Tanpa unduhan",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(9.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(LocalTemplates.all, key = { it.id }) { template ->
                TemplateCard(template = template, onClick = { onCreateFrom(template) })
            }
        }
    }
}

@Composable
private fun TemplateCard(template: LocalTemplate, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(158.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.55f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                IToolsPalette.Accent.copy(alpha = 0.22f),
                                IToolsPalette.Video.copy(alpha = 0.18f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = template.canvas.ratioLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = IToolsPalette.Accent,
                    fontWeight = FontWeight.Black
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = template.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProjectRow(
    summary: ProjectSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember(summary.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(74.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (summary.thumbnail != null) {
                    AsyncImage(
                        model = summary.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = formatDuration(summary.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = summary.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${summary.clipCount} klip • ${formatBytes(summary.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = relativeDate(summary.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opsi proyek")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Ganti nama") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplikat") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = { menu = false; onDuplicate() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Hapus", color = IToolsPalette.Danger) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = IToolsPalette.Danger
                            )
                        },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProjects(onCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(IToolsPalette.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = IToolsPalette.Accent,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("Belum ada proyek", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(5.dp))
            Text(
                "Mulai dari video, foto, atau template lokal. Semua diproses di perangkat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(15.dp))
            FilledTonalButton(onClick = onCreate) { Text("Buat proyek pertama") }
        }
    }
}

@Composable
private fun StoragePanel(bytes: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ruang proyek terpakai", style = MaterialTheme.typography.labelMedium)
                Text(
                    formatBytes(bytes),
                    style = MaterialTheme.typography.titleLarge,
                    color = IToolsPalette.Accent
                )
            }
            Text(
                "Media disalin ke folder aplikasi agar proyek tetap utuh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(180.dp)
            )
        }
    }
}

@Composable
private fun NewProjectDialog(
    template: LocalTemplate?,
    onDismiss: () -> Unit,
    onCreate: (String, CanvasSpec) -> Unit
) {
    var name by remember { mutableStateOf(template?.name ?: "") }
    var selected by remember { mutableStateOf(template ?: LocalTemplates.all.first()) }
    val presets = LocalTemplates.all + LocalTemplate(
        "custom-square",
        "Kustom persegi",
        "1:1 • 1080×1080",
        CanvasSpec(1080, 1080, 30),
        "1:1"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Proyek baru") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama proyek") },
                    placeholder = { Text("Contoh: Vlog liburan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                SmallCaps("Kanvas")
                Spacer(Modifier.height(7.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    presets.distinctBy { it.id }.forEach { preset ->
                        val active = preset.id == selected.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) IToolsPalette.Accent
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { selected = preset }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Text(
                                    text = preset.canvas.ratioLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (active) Color(0xFF162000)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${preset.canvas.width}×${preset.canvas.height}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) Color(0xFF162000).copy(alpha = 0.75f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Rasio ${selected.canvas.ratioLabel} • ${selected.canvas.fps} fps • " +
                        "${selected.canvas.width}×${selected.canvas.height}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, selected.canvas) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = IToolsPalette.Accent,
                    contentColor = Color(0xFF162000)
                )
            ) { Text("Buat") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                onClick = { if (name.isNotBlank()) onRename(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun BusyOverlay(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = IToolsPalette.Accent,
                    strokeWidth = 2.5.dp
                )
                Spacer(Modifier.width(13.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun relativeDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0)
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "Baru saja"
        minutes < 60 -> "$minutes menit lalu"
        minutes < 24 * 60 -> "${minutes / 60} jam lalu"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} hari lalu"
        else -> SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(Date(timestamp))
    }
}
