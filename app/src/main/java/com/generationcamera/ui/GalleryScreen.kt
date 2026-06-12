package com.generationcamera.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.generationcamera.R
import com.generationcamera.ui.theme.Charcoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var viewingIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        photos = withContext(Dispatchers.IO) { queryPhotos(context) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Charcoal)) {
        val viewing = viewingIndex
        val pagerState = rememberPagerState(initialPage = viewing ?: 0) { photos.size }
        LaunchedEffect(viewing) {
            if (viewing != null) pagerState.scrollToPage(viewing)
        }

        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { if (viewingIndex != null) viewingIndex = null else onBack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                )
            }
            Text(
                if (viewing != null && photos.isNotEmpty())
                    "${pagerState.currentPage + 1} / ${photos.size}"
                else stringResource(R.string.gallery),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            IconButton(
                onClick = {
                    val uri = photos.getOrNull(pagerState.currentPage) ?: return@IconButton
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                },
                enabled = viewing != null,
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.share),
                    tint = if (viewing != null) Color.White else Color.Gray,
                )
            }
        }

        if (viewing != null && photos.isNotEmpty()) {
            // full-screen viewer: swipe horizontally between photos
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                AsyncImage(
                    model = photos[page],
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.empty_gallery),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(photos) { index, uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(3f / 4f)
                            .clickable { viewingIndex = index },
                    )
                }
            }
        }
    }
}

private fun queryPhotos(context: android.content.Context): List<Uri> {
    val uris = mutableListOf<Uri>()
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?" to arrayOf("%GenerationCamera%")
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.DATA + " LIKE ?" to arrayOf("%GenerationCamera%")
    }
    context.contentResolver.query(
        collection, projection, selection, args,
        MediaStore.Images.Media.DATE_ADDED + " DESC",
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext()) {
            uris += Uri.withAppendedPath(collection, cursor.getLong(idCol).toString())
        }
    }
    return uris
}
