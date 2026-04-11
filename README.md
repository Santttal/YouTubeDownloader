# Video Downloader

Android app for downloading videos from YouTube and Instagram. Paste a link, pick quality, download.

## Features

- **YouTube** — download in 360p, 720p, 1080p or audio-only (m4a)
- **Instagram** — download public Reels and posts
- **Share intent** — share a link from any app to start downloading
- **Clipboard detection** — detects video links when you open the app
- **Progress tracking** — real-time progress bar with download speed
- **Notifications** — foreground progress notification + completion notification with "Open" action
- **Material You** — dynamic colors on Android 12+

## Architecture

```
URL input
  -> NewPipe Extractor (YouTube stream URLs, ~1-2s)
  -> Instagram GraphQL API (Instagram video URLs, ~1s)
  -> OkHttp parallel download (4 threads, Range chunked)
  -> MediaMuxer (1080p DASH: video + audio muxing)
  -> MediaStore (save to Downloads)
```

No Python, no yt-dlp, no server required. Everything runs natively on device.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| YouTube extraction | NewPipe Extractor v0.26.1 |
| Instagram extraction | Instagram GraphQL API (no login) |
| HTTP client | OkHttp |
| Video/audio muxing | Android MediaMuxer |
| Background work | WorkManager |
| Image loading | Coil 3 |
| DI | Koin |
| Min SDK | 26 (Android 8.0) |

## Build

```bash
git clone https://github.com/Santttal/VideoDownloader.git
cd VideoDownloader
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-universal-debug.apk`

### Install on device

```bash
adb install app/build/outputs/apk/debug/app-universal-debug.apk
```

## Tests

Functional tests make real HTTP requests to YouTube and Instagram.

```bash
# All tests
./gradlew testDebugUnitTest

# YouTube only
./gradlew testDebugUnitTest --tests "com.santttal.videodownloader.NewPipeExtractorTest"

# Instagram only
./gradlew testDebugUnitTest --tests "com.santttal.videodownloader.InstagramExtractorTest"
```

## Project Structure

```
app/src/main/java/com/santttal/videodownloader/
  App.kt                          # Application — NewPipe init, FFmpeg init, Koin
  di/AppModule.kt                 # Dependency injection
  data/
    DownloadRepository.kt         # YouTube stream resolution via NewPipe
    InstagramRepository.kt        # Instagram video extraction via GraphQL
  domain/
    VideoInfoUseCase.kt           # Fetch video metadata
    StartDownloadUseCase.kt       # Enqueue WorkManager download job
  model/
    VideoInfo.kt                  # Title, thumbnail, duration
    Quality.kt                   # 360p, 720p, 1080p, MP3
    StreamUrls.kt                # Resolved download URLs
  worker/
    DownloadWorker.kt             # OkHttp parallel download + MediaMuxer + notifications
  ui/
    MainActivity.kt               # Navigation, Share intent handling
    splash/SplashScreen.kt        # Launch screen
    download/
      DownloadScreen.kt           # Main UI — URL input, video card, chips, progress
      DownloadViewModel.kt        # State management, WorkManager observer
    theme/                        # Material 3 theme with dynamic colors
  util/
    UrlValidator.kt               # YouTube + Instagram URL detection
    NewPipeDownloader.kt          # OkHttp-based downloader for NewPipe Extractor
    MediaStoreHelper.kt           # Save files to Downloads via MediaStore
    FilenameUtils.kt              # Sanitize filenames
```

## Supported URL Formats

**YouTube:**
- `https://www.youtube.com/watch?v=...`
- `https://youtu.be/...`
- `https://www.youtube.com/shorts/...`

**Instagram:**
- `https://www.instagram.com/reel/...`
- `https://www.instagram.com/p/...`
- `https://www.instagram.com/tv/...`

## License

This project is for personal/educational use. Video downloading may violate platform terms of service.
