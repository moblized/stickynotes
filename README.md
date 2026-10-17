# Sticky Notes

A Google Keep–style notes app for Android, with a sticky-note look and handwriting fonts.
Kotlin + Jetpack Compose + Room.

## Install on your phone

Every push to `main` builds a signed APK on GitHub Actions and publishes it as a release.

1. Open the repo's **Releases** page on your phone and download `StickyNotes.apk` from the latest release.
2. Open the file. If Android asks, allow your browser to install apps.
3. Later builds install over the old one and keep your notes, because every build uses the same signing key (`keystore/`).

## Features

- Sticky notes in 9 paper colors, with a slight tilt, a curled corner and a soft shadow
- Handwriting fonts: Caveat, Patrick Hand, Indie Flower, Kalam, Shadows Into Light, Gochi Hand (Settings)
- Notes and checklists (Enter adds the next item; checked items move to a collapsible section)
- Pinning, search, labels (drawer, filter, picker, Edit labels), archive, Trash (kept 7 days)
- Grid or list layout; long-press and drag to reorder; swipe a note to archive it
- Reminders with notifications, re-set automatically after a reboot or app update
- Image attachments (photo picker, full-screen viewer with pinch zoom)
- Send a note as text to any app; make a copy
- Delete animation: the note crumples into a paper ball that falls, bounces and rolls with a shadow
- Undo bar after delete or archive, duration adjustable in Settings (default 5 seconds)
- Light, dark or follow-system theme

## Build it yourself

Open the folder in Android Studio (JDK 17), or run `./gradlew assembleRelease`.
The APK lands in `app/build/outputs/apk/release/`.

Fonts are from Google Fonts under the SIL Open Font License (see `FONTS-OFL.txt`).
