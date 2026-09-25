package com.school.hub.core.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** Камера (полное разрешение) и галерея. */
class PhotoActions(val camera: () -> Unit, val gallery: () -> Unit)

@Composable
fun rememberPhotoActions(onPhoto: (Uri) -> Unit): PhotoActions {
    val ctx = LocalContext.current
    val shot = remember {
        val dir = File(ctx.cacheDir, "shots").apply { mkdirs() }
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(dir, "shot.jpg"))
    }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) onPhoto(shot) }
    val gal = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(onPhoto) }
    return remember { PhotoActions({ cam.launch(shot) }, { gal.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) }
}
