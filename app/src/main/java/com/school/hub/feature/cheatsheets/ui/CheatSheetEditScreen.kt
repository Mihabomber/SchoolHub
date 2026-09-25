package com.school.hub.feature.cheatsheets.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.school.hub.feature.cheatsheets.model.Subject
import com.school.hub.navigation.AppViewModelFactory
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CheatSheetEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: CheatSheetEditViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onImagePicked(uri)
    }
    LaunchedEffect(s.saved) { if (s.saved) onSaved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isNew) "Новая шпаргалка" else "Редактирование") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
    ) { inner ->
        if (s.isLoading) {
            Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(inner).imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Предмет", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Subject.entries.forEach { subj ->
                    val c = Color(subj.color)
                    FilterChip(
                        selected = s.subject == subj,
                        onClick = { vm.onSubject(subj) },
                        label = { Text(subj.title) },
                        leadingIcon = { Text(subj.emoji) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.22f)),
                    )
                }
            }
            OutlinedTextField(
                value = s.title, onValueChange = vm::onTitle,
                label = { Text("Тема") }, placeholder = { Text("Например: Теорема Пифагора") },
                singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.content, onValueChange = vm::onContent,
                label = { Text("Текст шпаргалки") },
                placeholder = { Text("Формулы, правила, даты…") },
                minLines = 8, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )

            val imagePath = s.imagePath
            if (imagePath != null) {
                Box {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(File(imagePath)).crossfade(true).build(),
                        contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)),
                    )
                    IconButton(
                        onClick = vm::removeImage,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    ) { Icon(Icons.Filled.Close, "Убрать фото", tint = Color.White) }
                }
            } else {
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    enabled = !s.isImporting,
                ) {
                    if (s.isImporting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.AddPhotoAlternate, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (s.isImporting) "Обрабатываю фото…" else "Добавить фото / скан конспекта")
                }
            }

            OutlinedTextField(
                value = s.author, onValueChange = vm::onAuthor,
                label = { Text("Автор (необязательно)") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = vm::save,
                enabled = s.canSave && !s.isSaving,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Filled.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Сохранить и поделиться с классом")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
