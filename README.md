# PictureSorter

A "Tinder for pictures" for sorting photos (desktop: Windows and Ubuntu).

1. Choose a folder – the pictures in it (jpg, jpeg, png, webp, gif, bmp) are shown one after another.
2. Rate each picture with **✓ Yes**, **✕ No** or **? Maybe**
   (keyboard: `→` Yes, `←` No, `↑`/`↓` Maybe, `Backspace` back).
3. At the end of a round:
   - **Move Yes + Maybe** or **Move Yes only**: choose a target folder and the pictures are
     **moved** there (not copied). Existing files are never overwritten; if a name already exists,
     `_1`, `_2`, … is appended.
   - **New round over Yes + Maybe**: these pictures are rated again, and the rating from the
     previous round is shown next to each one.
4. The done screen shows the number of rounds, the counts of the last round, the chosen option,
   and a button to open the target folder in the file manager.

"No" pictures stay untouched in the source folder.

### Continue later

Progress is saved automatically after every rating, so large folders can be sorted over several
sittings. Click the logo (or just close the app) and the start screen lists all unfinished sessions
with **Continue** and **Discard**; choosing a folder that has saved progress asks whether to continue
or start over. When continuing, pictures deleted in the meantime are skipped, and pictures added to
the folder are appended to round 1. Progress is removed once pictures have been moved.

Progress files are stored outside the picture folder, one per source folder:
`%APPDATA%\PictureSorter\progress` (Windows) or `~/.local/share/PictureSorter/progress` (Ubuntu).

## Run / build

```shell
./gradlew :desktopApp:run            # run (Windows: .\gradlew.bat :desktopApp:run)
./gradlew :shared:jvmTest            # tests
./gradlew :desktopApp:packageMsi     # Windows installer (run on Windows)
./gradlew :desktopApp:packageDeb     # Ubuntu package (run on Ubuntu)
```

## Structure

- `shared/src/commonMain` – platform-independent logic (`model/SortSession.kt`, `model/SavedProgress.kt`), `SorterViewModel` and UI (`ui/`).
- `shared/src/commonMain/.../PhotoPlatform.kt` – interface for file access (choosing folders, loading pictures, moving, opening folders, storing progress).
- `shared/src/jvmMain/.../JvmPhotoPlatform.kt` – desktop implementation.
- `desktopApp` – desktop entry point.

For a future Android version: add `androidTarget()` in `shared` and implement `PhotoPlatform` for Android
(e.g. via the Storage Access Framework). Logic and UI can be reused as they are.
