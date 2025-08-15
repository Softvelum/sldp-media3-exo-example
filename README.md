# SLDP Media3 ExoPlayer sample app

Media3 ExoPlayer app example utilizing [SLDP Media3 DataSource](https://github.com/Softvelum/sldp-media3-exo-plugin).

Read more about our SLDP open source playback [in this blog post](https://softvelum.com/2025/08/sldp-exoplayer-media3-open-source/).

## Usage

### Version Catalog

Add the following to your `libs.versions.toml` file:

```toml
[versions]
#...
sldp = "0.0.1"

[libraries]
#...
sldp-media3-exo-plugin = { module = "com.github.softvelum:sldp-media3-exo-plugin", version.ref = "sldp" }
```

then

```kotlin
dependencies {
    // ...
    implementation(libs.sldp.media3.exo.plugin)
}
```

### Simple Example

If you are new to ExoPlayer, check out the [Getting started](https://developer.android.com/media/media3/exoplayer/hello-world) page for an introduction to using ExoPlayer.

```kotlin
val uri = "wss://demo-nimble.softvelum.com/live/bbb"

val mediaItem = MediaItem.fromUri(uri)

val mediaSource = SldpMediaSource.Factory().createMediaSource(mediaItem)

val exoPlayer = ExoPlayer.Builder(context).build().apply { 
    setMediaSource(mediaSource) 
}
```
