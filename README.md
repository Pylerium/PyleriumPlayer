# Pylerium Player

A polished Android TV app for watching your own **movies and TV shows**, from a USB drive or local storage.
Built in the Pylerium graphite, orange and cyan visual system, and driven entirely by the TV remote.

Part of the Pylerium TV suite: [pyleriumTV](https://github.com/Pylerium/pyleriumTV) (personal TV channels) ·
[Pylerium Purge](https://github.com/Pylerium/PyleriumPurge) (launcher).

## Features

- **Automatic library.** Add one or more folders; files are scanned and sorted into movies and TV shows.
  `Show.Name.S01E02`, `1x02` and `Season N/` folder layouts are recognised, and titles and years are cleaned up
  from release-style filenames
- **Home screen.** Continue Watching, Movies and TV Shows poster rows with generated artwork, progress bars,
  watched badges and a hero panel that describes whatever has focus
- **Show pages.** Seasons, episode list, resume / next-up button and per-episode watched state
- **Player.** Resume where you left off, accelerating seek (10 s → 30 s → 60 s), pause, audio and subtitle
  track selection, and automatic next episode with a 10 s countdown
- **Plays everything.** libVLC decodes XviD/AVI, AC3, E-AC3 (DD+), DTS, 10-bit x265 and more, with hardware
  decoding where the TV supports it
- **MENU** on the home screen: add / remove folders, rescan, mark watched / unwatched

## Remote controls

| Where | Key | Action |
| --- | --- | --- |
| Home | D-pad / OK | Browse / play or open a show |
| Home, show page | MENU | Library options / toggle watched |
| Player | ◀ ▶ | Seek (hold to accelerate) |
| Player | OK | Play / pause |
| Player | ▲ | Audio & subtitles |
| Player | ▼ | Show / hide controls |
| Player | CH± / Next / Prev | Next / previous episode |

## Build

```
./gradlew assembleDebug
```

Requires the Android SDK (`local.properties` with `sdk.dir=...`) and JDK 17+. Output:
`app/build/outputs/apk/debug/app-debug.apk`. Application id `com.pylerium.player`, minSdk 26,
`armeabi-v7a` and `arm64-v8a`.

On first use, Android asks for full file access so the built-in folder browser can read your drive.

## Credits

- [libVLC for Android](https://code.videolan.org/videolan/libvlc-android-samples) (LGPL v2.1+)
- [Onest](https://github.com/googlefonts/onest) typeface (SIL Open Font License, see `app/src/main/assets/fonts`)
