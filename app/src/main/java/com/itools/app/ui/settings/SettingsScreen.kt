package com.itools.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itools.app.BuildConfig
import com.itools.app.IToolsApp
import com.itools.app.core.model.formatBytes
import com.itools.app.core.settings.AppSettings
import com.itools.app.ui.components.SectionTitle
import com.itools.app.ui.components.SmallCaps
import com.itools.app.ui.theme.IToolsPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as IToolsApp
    val settings by app.settings.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings()
    )
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var storage by remember { mutableStateOf<Long?>(null) }
    var clearing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        storage = app.store.storageUsed()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                }
                Column(Modifier.weight(1f)) {
                    Text("Pengaturan", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Preferensi lokal untuk aplikasi ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            SettingsCard {
                SectionTitle(
                    title = "Tampilan",
                    subtitle = "Tema gelap adalah bawaan editor."
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ThemeChip("Gelap", "dark", settings.theme) {
                        scope.launch { app.settings.setTheme(it) }
                    }
                    ThemeChip("Terang", "light", settings.theme) {
                        scope.launch { app.settings.setTheme(it) }
                    }
                    ThemeChip("Sistem", "system", settings.theme) {
                        scope.launch { app.settings.setTheme(it) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SettingsCard {
                SectionTitle(
                    title = "Editor",
                    subtitle = "Perilaku selama mengedit."
                )
                Spacer(Modifier.height(6.dp))
                SettingSwitch(
                    title = "Layar tetap menyala",
                    subtitle = "Mencegah layar mati saat pratinjau diputar.",
                    checked = settings.keepScreenOn,
                    onChecked = { scope.launch { app.settings.setKeepScreenOn(it) } }
                )
                SettingSwitch(
                    title = "Panduan safe area",
                    subtitle = "Menampilkan batas aman TikTok/Reels pada pratinjau.",
                    checked = settings.showSafeArea,
                    onChecked = { scope.launch { app.settings.setShowSafeArea(it) } }
                )
                SettingSwitch(
                    title = "Simpan otomatis",
                    subtitle = "Menyimpan JSON proyek ±0,5 detik setelah perubahan.",
                    checked = settings.autoSave,
                    onChecked = { scope.launch { app.settings.setAutoSave(it) } }
                )
            }

            Spacer(Modifier.height(12.dp))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = IToolsPalette.Accent)
                    Spacer(Modifier.size(10.dp))
                    SectionTitle(
                        title = "Penyimpanan",
                        subtitle = "Video asli tidak diubah; media disalin ke proyek.",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        SmallCaps("Ruang proyek terpakai")
                        Text(
                            text = storage?.let { formatBytes(it) } ?: "Menghitung…",
                            style = MaterialTheme.typography.headlineSmall,
                            color = IToolsPalette.Accent
                        )
                    }
                    Button(
                        onClick = {
                            if (!clearing) {
                                clearing = true
                                scope.launch {
                                    app.store.clearPreviewCache()
                                    storage = withContext(Dispatchers.IO) { app.store.storageUsed() }
                                    clearing = false
                                    snackbar.showSnackbar("Cache pratinjau dibersihkan.")
                                }
                            }
                        },
                        enabled = !clearing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Bersihkan cache")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = IToolsPalette.SecondaryBlue)
                    Spacer(Modifier.size(10.dp))
                    SectionTitle(title = "Tentang iTools")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "iTools adalah editor video lokal yang dibangun dengan Kotlin, " +
                        "Jetpack Compose, dan AndroidX Media3. Tidak ada akun, iklan, analytics, " +
                        "langganan, kredit, atau watermark.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                KeyValue("Versi", BuildConfig.VERSION_NAME)
                KeyValue("Izin jaringan", "Tidak ada (INTERNET tidak dideklarasikan)")
                KeyValue("Mesin render", "Media3 Transformer & CompositionPlayer")
                KeyValue("Format proyek", "JSON + folder media per proyek")
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Dokumentasi dan kode sumber disertakan. Fitur eksperimental " +
                    "(reverse, batas frame rate) dijelaskan di docs/FEATURES.md.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) }
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF162000),
                checkedTrackColor = IToolsPalette.Accent
            )
        )
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.62f)
        )
    }
}
