package com.softvelum.sldpplayer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.softvelum.media3.SldpMediaSource
import com.softvelum.sldpplayer.ui.theme.MyApplicationTheme

data class TrackSelectorItem(
    val displayName: String,
    val trackSelectionOverride: TrackSelectionOverride,
    val height: Int
)

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@OptIn(UnstableApi::class)
fun isSupportedFormat(
    mappedTrackInfo: MappingTrackSelector.MappedTrackInfo?,
    rendererIndex: Int
): Boolean {
    val trackGroupArray = mappedTrackInfo?.getTrackGroups(rendererIndex)
    return if (trackGroupArray?.length == 0) {
        false
    } else mappedTrackInfo?.getRendererType(rendererIndex) == C.TRACK_TYPE_VIDEO
}

@OptIn(UnstableApi::class)
fun MappingTrackSelector.generateQualityList(): List<TrackSelectorItem> {
    val trackOverrideList = mutableListOf<TrackSelectorItem>()

    val renderTrack = this.currentMappedTrackInfo
    val renderCount = renderTrack?.rendererCount ?: 0

    for (rendererIndex in 0 until renderCount) {
        if (!isSupportedFormat(renderTrack, rendererIndex)) {
            continue
        }

        val trackGroupType = renderTrack?.getRendererType(rendererIndex) ?: continue
        if (trackGroupType != C.TRACK_TYPE_VIDEO) {
            continue
        }

        val trackGroups = renderTrack.getTrackGroups(rendererIndex)
        val trackGroupsCount = trackGroups.length

        for (groupIndex in 0 until trackGroupsCount) {
            val videoQualityTrackCount = trackGroups[groupIndex].length

            for (trackIndex in 0 until videoQualityTrackCount) {
                val trackSupport = renderTrack.getTrackSupport(
                    rendererIndex,
                    groupIndex,
                    trackIndex
                )
                if (trackSupport != C.FORMAT_HANDLED) {
                    continue
                }

                val track = trackGroups[groupIndex]
                val format = track.getFormat(trackIndex)

                val trackName = "${format.width}x${format.height}"
                val trackSelectionOverride = TrackSelectionOverride(
                    track, listOf(trackIndex)
                )
                val item = TrackSelectorItem(
                    displayName = trackName,
                    trackSelectionOverride = trackSelectionOverride,
                    height = format.width
                )
                trackOverrideList.add(item)
            }
        }
    }

    return trackOverrideList.sortedByDescending { it.height }
}

@OptIn(UnstableApi::class)
@Composable
fun ExoScreen(uri: String) {

    val context = LocalContext.current

    var qualityList: List<TrackSelectorItem> by remember { mutableStateOf(emptyList()) }
    var isQualityListExpanded by remember { mutableStateOf(false) }

    var selectedQuality: List<Boolean> by remember { mutableStateOf(emptyList()) }

    val videoTrackSelectionFactory = AdaptiveTrackSelection.Factory()

    val trackSelector = DefaultTrackSelector(context, videoTrackSelectionFactory)

    val mediaItem = MediaItem.fromUri(uri)

    val mediaSource = SldpMediaSource.Factory()
        .setTrustAllCerts(true)
        .createMediaSource(mediaItem)

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build().apply {
                setMediaSource(mediaSource)
                playWhenReady = true
                prepare()
            }
    }

    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    DisposableEffect(Unit) {
        val window = context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val lifecycle = lifecycleOwner.value.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    insetsController.apply {
                        hide(WindowInsetsCompat.Type.statusBars())
                        hide(WindowInsetsCompat.Type.navigationBars())
                        hide(WindowInsetsCompat.Type.systemBars())
                    }
                    exoPlayer.play()
                }

                Lifecycle.Event.ON_STOP -> {
                    insetsController.apply {
                        show(WindowInsetsCompat.Type.statusBars())
                        show(WindowInsetsCompat.Type.navigationBars())
                        show(WindowInsetsCompat.Type.systemBars())
                    }
                    exoPlayer.stop()
                }

                else -> {}
            }
        }
        lifecycle.addObserver(observer)

        onDispose {
            exoPlayer.release()
            lifecycle.removeObserver(observer)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PlayerView(context).apply {
                        val playerView = this

                        exoPlayer.addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                playerView.keepScreenOn =
                                    playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY

                                if (playbackState == Player.STATE_READY) {
                                    isQualityListExpanded = false
                                    val quality = trackSelector.generateQualityList()
                                    val list = MutableList(quality.size + 1) { false }
                                    list[0] = true
                                    selectedQuality = list
                                    qualityList = quality
                                }
                            }
                        })

                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = exoPlayer
                    }
                })

            if (qualityList.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(48.dp),
                ) {
                    IconButton(
                        onClick = {
                            isQualityListExpanded = !isQualityListExpanded
                        }
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Settings,
                            tint = Color.Cyan,
                            contentDescription = null
                        )
                    }

                    DropdownMenu(
                        expanded = isQualityListExpanded,
                        onDismissRequest = {
                            isQualityListExpanded = false
                        }
                    ) {
                        DropdownMenuItem(
                            onClick = {
                                trackSelector.setParameters(
                                    trackSelector.parameters
                                        .buildUpon()
                                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                        .build()
                                )

                                isQualityListExpanded = false
                                val list = MutableList(qualityList.size + 1) { false }
                                list[0] = true
                                selectedQuality = list
                            },
                            text = {
                                Text(
                                    text = if (selectedQuality[0])
                                        "\u2713 " + stringResource(id = R.string.track_selector_auto)
                                    else
                                        stringResource(id = R.string.track_selector_auto)
                                )
                            }
                        )

                        for ((idx, quality) in qualityList.withIndex()) {
                            DropdownMenuItem(
                                onClick = {
                                    trackSelector.setParameters(
                                        trackSelector.parameters
                                            .buildUpon()
                                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                            .addOverride(quality.trackSelectionOverride)
                                            .build()
                                    )

                                    isQualityListExpanded = false
                                    val list = MutableList(qualityList.size + 1) { false }
                                    list[idx + 1] = true
                                    selectedQuality = list
                                },
                                text = {
                                    Text(
                                        if (selectedQuality[idx + 1])
                                            "\u2713 " + quality.displayName
                                        else
                                            quality.displayName
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    MyApplicationTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ExoScreen(uri = "")
        }
    }
}
