package com.airplay.tv.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airplay.tv.R

@Composable
fun MediaPlaybackScreen(
    kind: String,
    clientIp: String,
    sessionId: String,
    imageData: ByteArray?,
    assetKey: String?,
    transition: String?,
    theme: String?,
    slideDurationSeconds: Int
) {
    val imageBitmap = remember(imageData) {
        imageData?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    val title = if (kind == "slideshow") {
        stringResource(R.string.state_media_slideshow_title)
    } else {
        stringResource(R.string.state_media_photo_title)
    }

    AirPlayBackdrop(
        topRightContent = {
            AirPlayTopBadge(
                text = if (kind == "slideshow") {
                    "Modo: slideshow"
                } else {
                    "Modo: foto"
                }
            )
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 54.dp, end = 54.dp, top = 136.dp, bottom = 42.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.Top
        ) {
            AirPlayGlassCard(
                modifier = Modifier
                    .weight(1.2f)
                    .height(500.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.state_media_waiting_photo),
                            style = MaterialTheme.typography.headlineMedium,
                            color = AirPlayWhiteSoft
                        )
                    }
                }
            }

            AirPlayGlassCard(
                modifier = Modifier
                    .weight(0.9f)
                    .height(500.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.state_media_hint),
                            style = MaterialTheme.typography.titleLarge,
                            color = AirPlayWhiteSoft
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        MediaMetaRow(label = "Cliente", value = clientIp)
                        MediaMetaRow(label = "Sessao", value = sessionId)
                        assetKey?.takeIf { it.isNotBlank() }?.let {
                            MediaMetaRow(label = "Asset", value = it)
                        }
                        transition?.takeIf { it.isNotBlank() }?.let {
                            MediaMetaRow(label = "Transicao", value = it)
                        }
                        if (kind == "slideshow") {
                            theme?.takeIf { it.isNotBlank() }?.let {
                                MediaMetaRow(label = "Tema", value = it)
                            }
                            if (slideDurationSeconds > 0) {
                                MediaMetaRow(
                                    label = "Duracao",
                                    value = "${slideDurationSeconds}s por slide"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaMetaRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = AirPlayBlue300
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.1f))
        )
    }
}
