package com.school.hub.feature.cheatsheets.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.school.hub.core.ui.components.EmptyState
import com.school.hub.core.ui.components.SubjectBadge
import com.school.hub.core.ui.theme.Gold
import com.school.hub.core.util.relativeTime
import com.school.hub.navigation.AppViewModelFactory
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheatSheetDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: CheatSheetDetailViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sheet = state.sheet
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var fullImage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = {
                    if (sheet != null) {
                        IconButton(onClick = { vm.toggleFavorite() }) {
                            if (sheet.isFavorite) Icon(Icons.Filled.Star, "Убрать из избранного", tint = Gold)
                            else Icon(Icons.Filled.StarBorder, "В избранное")
                        }
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${sheet.subject.emoji} ${sheet.title}\n\n${sheet.content}")
                            }
                            context.startActivity(Intent.createChooser(send, "Поделиться шпаргалкой"))
                        }) { Icon(Icons.Filled.Share, "Поделиться") }
                        IconButton(onClick = { onEdit(sheet.id) }) { Icon(Icons.Filled.Edit, "Редактировать") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Удалить") }
                    }
                },
            )
        },
    ) { inner ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) { CircularProgressIndicator() }
            sheet == null -> Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) {
                EmptyState("🫥", "Шпаргалка не найдена", "Возможно, её удалили")
            }
            else -> Column(
                Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SubjectBadge(sheet.subject)
                Text(sheet.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(sheet.author.takeIf { it.isNotBlank() }?.let { "✍️ $it" }, "обновлено ${relativeTime(sheet.updatedAt)}")
                        .joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                sheet.imagePath?.let { path ->
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(File(path)).crossfade(true).build(),
                        contentDescription = "Фото шпаргалки",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)
                            .clip(RoundedCornerShape(20.dp)).clickable { fullImage = path },
                    )
                }
                if (sheet.content.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SelectionContainer {
                            Text(
                                sheet.content,
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text("Удалить шпаргалку?") },
            text = { Text("Она удалится у всех одноклассников при следующей синхронизации.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }

    fullImage?.let { path -> ZoomableImageDialog(path) { fullImage = null } }
}

@Composable
private fun ZoomableImageDialog(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val tState = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 6f)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                    .transformable(tState),
            )
        }
    }
}
