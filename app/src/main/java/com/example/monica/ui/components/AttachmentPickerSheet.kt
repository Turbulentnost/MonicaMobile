package com.example.monica.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.monica.data.GalleryMediaItem
import com.example.monica.data.GalleryMediaLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class AttachTab { PhotoVideo, Place, File }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    onSendUris: (List<Pair<Uri, String>>) -> Unit,
    onSendLocationText: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableStateOf(AttachTab.PhotoVideo) }
    var media by remember { mutableStateOf<List<GalleryMediaItem>>(emptyList()) }
    var loadingGallery by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var locationBusy by remember { mutableStateOf(false) }
    var pendingCapture by remember { mutableStateOf<Pair<Uri, String>?>(null) }

    val imageLoader = remember { ImageLoader(context) }
    val videoImageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    fun hasGalleryPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun reloadGallery() {
        if (!hasGalleryPermission()) return
        loadingGallery = true
        scope.launch {
            media = withContext(Dispatchers.IO) { GalleryMediaLoader.loadRecent(context) }
            loadingGallery = false
        }
    }

    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) reloadGallery()
        else Toast.makeText(context, "Нужен доступ к галерее", Toast.LENGTH_SHORT).show()
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Нужен доступ к камере", Toast.LENGTH_SHORT).show()
        }
    }

    fun deliverLocation() {
        locationBusy = true
        scope.launch {
            val loc = withContext(Dispatchers.IO) { readLastLocation(context) }
            locationBusy = false
            if (loc == null) {
                Toast.makeText(context, "Не удалось определить местоположение", Toast.LENGTH_SHORT).show()
                return@launch
            }
            onSendLocationText("📍 https://maps.google.com/?q=${loc.latitude},${loc.longitude}")
            onDismiss()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            deliverLocation()
        } else {
            Toast.makeText(context, "Нужен доступ к геолокации", Toast.LENGTH_SHORT).show()
            locationBusy = false
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        val capture = pendingCapture
        pendingCapture = null
        if (ok && capture != null) {
            onSendUris(listOf(capture))
            onDismiss()
        }
    }

    val takeVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { ok ->
        val capture = pendingCapture
        pendingCapture = null
        if (ok && capture != null) {
            onSendUris(listOf(capture))
            onDismiss()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        onSendUris(listOf(uri to mime))
        onDismiss()
    }

    fun ensureGalleryPermission() {
        if (hasGalleryPermission()) {
            reloadGallery()
            return
        }
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        galleryPermissionLauncher.launch(perms)
    }

    fun createCaptureUri(prefix: String, ext: String): Uri? {
        return runCatching {
            val dir = File(context.cacheDir, "capture").also { it.mkdirs() }
            val file = File(dir, "$prefix-${System.currentTimeMillis()}.$ext")
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    fun launchCameraPhoto() {
        val camOk = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!camOk) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        val uri = createCaptureUri("photo", "jpg") ?: return
        pendingCapture = uri to "image/jpeg"
        takePictureLauncher.launch(uri)
    }

    fun launchCameraVideo() {
        val camOk = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!camOk) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        val uri = createCaptureUri("video", "mp4") ?: return
        pendingCapture = uri to "video/mp4"
        takeVideoLauncher.launch(uri)
    }

    fun sendLocation() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            locationBusy = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            return
        }
        deliverLocation()
    }

    LaunchedEffect(Unit) {
        ensureGalleryPermission()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        contentColor = Color.White,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                AttachCategory(
                    selected = tab == AttachTab.PhotoVideo,
                    icon = Icons.Outlined.Image,
                    label = "Фото/Видео",
                    onClick = {
                        tab = AttachTab.PhotoVideo
                        ensureGalleryPermission()
                    },
                )
                AttachCategory(
                    selected = tab == AttachTab.Place,
                    icon = Icons.Outlined.LocationOn,
                    label = "Место",
                    onClick = { tab = AttachTab.Place },
                )
                AttachCategory(
                    selected = tab == AttachTab.File,
                    icon = Icons.Outlined.AttachFile,
                    label = "Файл",
                    onClick = { tab = AttachTab.File },
                )
            }

            when (tab) {
                AttachTab.PhotoVideo -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        QuickAction(
                            icon = Icons.Outlined.PhotoCamera,
                            label = "Фото",
                            onClick = ::launchCameraPhoto,
                        )
                        QuickAction(
                            icon = Icons.Outlined.Videocam,
                            label = "Видео",
                            onClick = ::launchCameraVideo,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    when {
                        loadingGallery -> Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color(0xFF2D81E0))
                        }
                        media.isEmpty() -> Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Галерея пуста или нет доступа",
                                color = Color(0xFF9AA0A6),
                                textAlign = TextAlign.Center,
                            )
                        }
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(media, key = { it.id }) { item ->
                                val isSelected = item.id in selected
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clickable {
                                            selected = if (isSelected) {
                                                selected - item.id
                                            } else {
                                                selected + item.id
                                            }
                                        },
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(item.uri)
                                            .crossfade(true)
                                            .apply {
                                                if (item.isVideo) videoFrameMillis(0)
                                            }
                                            .build(),
                                        imageLoader = if (item.isVideo) videoImageLoader else imageLoader,
                                        contentDescription = item.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    if (item.isVideo) {
                                        Icon(
                                            Icons.Outlined.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(6.dp)
                                                .size(22.dp)
                                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                                .padding(2.dp),
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isSelected) {
                                            Icons.Outlined.CheckCircle
                                        } else {
                                            Icons.Outlined.RadioButtonUnchecked
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF2D81E0) else Color.White,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                AttachTab.Place -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF2D81E0),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Отправить текущее местоположение собеседнику",
                            color = Color(0xFFCFCFD4),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = ::sendLocation,
                            enabled = !locationBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D81E0)),
                        ) {
                            if (locationBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Отправить место")
                        }
                    }
                }
                AttachTab.File -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = null,
                            tint = Color(0xFF2D81E0),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Выберите любой файл с устройства",
                            color = Color(0xFFCFCFD4),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D81E0)),
                        ) {
                            Text("Выбрать файл")
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Surface(
                        color = Color(0xFF2C2C2E),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Отменить",
                            modifier = Modifier.padding(vertical = 14.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                if (tab == AttachTab.PhotoVideo && selected.isNotEmpty()) {
                    Button(
                        onClick = {
                            val payload = media
                                .filter { it.id in selected }
                                .map { it.uri to it.mimeType }
                            if (payload.isNotEmpty()) {
                                onSendUris(payload)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D81E0)),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Отправить (${selected.size})")
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachCategory(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else Color(0xFF2C2C2E)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) Color.Black else Color(0xFFCFCFD4),
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (selected) Color.White else Color(0xFF9AA0A6),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF2D81E0), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color(0xFF2D81E0), fontWeight = FontWeight.Medium)
    }
}

private fun readLastLocation(context: android.content.Context): Location? {
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )
    return providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }
}
