package com.school.hub.feature.cheatsheets.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.school.hub.core.ui.components.EmptyState
import com.school.hub.core.ui.components.SmallBadge
import com.school.hub.core.ui.components.SubjectBadge
import com.school.hub.core.ui.theme.Gold
import com.school.hub.core.util.plural
import com.school.hub.core.util.relativeTime
import com.school.hub.feature.cheatsheets.model.CheatSheet
import com.school.hub.feature.cheatsheets.model.Subject
import com.school.hub.navigation.AppViewModelFactory
import java.io.File

@Composable
fun CheatSheetListScreen(
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenChat: () -> Unit = {},
    vm: CheatSheetListViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                expanded = fabExpanded,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Шпаргалка") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Шпаргалки", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            plural(state.total, "шпаргалка", "шпаргалки", "шпаргалок") + " в классе",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    com.school.hub.feature.social.UrgentButton(onSent = onOpenChat)
                    FilledTonalIconButton(onClick = onOpenSync) {
                        Icon(Icons.Filled.CloudSync, contentDescription = "Обмен")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    placeholder = { Text("Поиск по тексту, теме, автору") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (vm.query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
            }
            item {
                SubjectFilterRow(state.counts, vm.selectedSubject, vm::onSubjectSelected)
            }
            if (!state.isLoading && state.items.isEmpty()) {
                item {
                    if (state.total == 0) EmptyState("📚", "Пока пусто", "Добавь первую шпаргалку — она сохранится на телефоне и разойдётся по классу")
                    else EmptyState("🔍", "Ничего не найдено", "Попробуй другой запрос или предмет")
                }
            }
            items(state.items, key = { it.id }) { sheet ->
                CheatCard(
                    sheet = sheet,
                    onClick = { onOpen(sheet.id) },
                    modifier = Modifier.padding(horizontal = 20.dp).animateItem(),
                )
            }
        }
    }
}

@Composable
private fun SubjectFilterRow(counts: Map<Subject, Int>, selected: Subject?, onSelect: (Subject?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Все") })
        }
        val sorted = Subject.entries.sortedByDescending { counts[it] ?: 0 }
        items(sorted) { s ->
            val color = Color(s.color)
            val n = counts[s] ?: 0
            FilterChip(
                selected = selected == s,
                onClick = { onSelect(s) },
                label = { Text(if (n > 0) "${s.title} · $n" else s.title) },
                leadingIcon = { Text(s.emoji) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Composable
fun CheatCard(sheet: CheatSheet, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = Color(sheet.subject.color)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(color, size = Size(6.dp.toPx(), size.height)) }
                .padding(start = 22.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubjectBadge(sheet.subject)
                if (sheet.isMine) {
                    Spacer(Modifier.width(6.dp))
                    SmallBadge("моя", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.weight(1f))
                if (sheet.isFavorite) Icon(Icons.Filled.Star, contentDescription = "Избранное", tint = Gold)
            }
            Text(sheet.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sheet.content.isNotBlank()) {
                Text(
                    sheet.content, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            sheet.imagePath?.let { path ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(File(path)).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)),
                )
            }
            Text(
                listOfNotNull(sheet.author.takeIf { it.isNotBlank() }, relativeTime(sheet.updatedAt)).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
