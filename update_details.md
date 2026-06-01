- **Type of Details:** New Update & Refactor
- **Description:**
  1. Removed the `instaloader` dependency completely from the Gradle configurations.
  2. Consolidated all media extraction under the requests-based `mo3.py` script.
  3. Integrated a dynamic theme switching system (System Default, Light, Dark) utilizing custom Material 3 palettes (Indigo, Teal, Rose).
  4. Updated `SettingsScreen.kt` to include an "App Theme" settings card with an interactive chooser dialog.
  5. Cleaned up all hardcoded dark backgrounds and texts across all screens to support dynamic theming.
  6. Refactored `PostViewScreen.kt` to make headers, navigation, and options theme-aware while keeping the media viewport black for immersion.
  7. Resolved compilation issues by adding missing imports for `BorderStroke` in `MainActivity.kt` and `File` in `SharedLinkProcessingService.kt`.
  8. Verified the project compiles successfully using `.\gradlew assembleDebug`.

  ***

- **Type of Details:** Error Solving & Refactor
- **Description:**
  1. Fixed the Instagram WebView login screen not loading or failing to create login sessions.
  2. Labeled and scoped the `WebView` instance in `InstagramLoginScreen.kt` to ensure `CookieManager.setAcceptThirdPartyCookies` gets the correct `WebView` argument.
  3. Configured `CookieManager` to accept all cookies and third-party cookies, which are required for Instagram session creation.
  4. Labeled and verified `removeAllCookies` runs asynchronously before `loadUrl` to avoid race conditions.
  5. Configured the User Agent to a modern Desktop Chrome string (`Chrome/120.0.0.0 Safari/537.36`) and set cache mode to `LOAD_NO_CACHE` to bypass mobile redirect challenges and resolve blank page crashes after the splash screen is shown.
  6. Refactored navigation in `MainActivity.kt` to declare `Screen.Login` as a top-level route in the navigation stack, ensuring back clicks pop the screen correctly and successful login pops the screen automatically.
  7. Adjusted default TopAppBar visibility in `MainActivity.kt` to prevent rendering over the login screen's app bar.
  8. Verified the project compiles successfully using `.\gradlew assembleDebug`.

  ***

- **Type of Details:** Error Solving & Refactor
- **Description:**
  1. Resolved the `SystemError: frame does not exist` exception during post details extraction by refactoring [MediaFetcherRepository.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/repository/MediaFetcherRepository.kt) to invoke the Python method `mo3.get_media_urls` directly from Kotlin and parse the JSON string response via Gson.
  2. Implemented profile username fetching by introducing `get_logged_in_username(cookie_file)` in [mo3.py](file:///c:/Android/vedinsta/app/src/main/python/mo3.py) to query Instagram's user info API using authenticated session cookies.
  3. Added secure caching for `ig_username` in [SecurePreferences.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/repository/SecurePreferences.kt).
  4. Updated `InstagramAuthState` and [InstagramAuthViewModel.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/viewmodel/InstagramAuthViewModel.kt) to retrieve, cache, and publish the alphanumeric username in the background.
  5. Refactored the Session Status card in [MediaSelectionScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/MediaSelectionScreen.kt) and active session header in [SessionsScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/SessionsScreen.kt) to show the alphanumeric username rather than the numeric `dsUserId`.
  6. Verified the project compiles successfully using `.\gradlew assembleDebug`.

  ***

- **Type of Details:** Error Solving & Refactor
- **Description:**
  1. Resolved concurrent downloads not starting by implementing an active download task counter (`AtomicInteger`) in [DownloadService.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/service/DownloadService.kt). This prevents premature service termination or foreground status removal while downloads are still running.
  2. Added dynamic SDK-level checks and passed `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` on Android 10+ (API 29+) to ensure compatibility with Android 13/14+ foreground service requirements.
  3. Integrated MediaStore indexing in [DownloadService.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/service/DownloadService.kt) using `MediaScannerConnection.scanFile` immediately upon download completion, ensuring downloaded files appear in the device gallery/photo library immediately.
  4. Redesigned [MediaSelectionScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/MediaSelectionScreen.kt) to make the entire layout scrollable using a single `LazyVerticalGrid` and `GridItemSpan` (success state) and a scrollable `Column` (other states), resolving viewport constraints and layout overflows.
  5. Enhanced the quality selection dropdown in [MediaSelectionScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/MediaSelectionScreen.kt) to display `"Original/High"` for the highest resolution quality.
  6. Verified the project compiles successfully using `.\gradlew.bat assembleDebug`.

  ***

- **Type of Details:** New Update & Refactor
- **Description:**
  1. Implemented in-app Snackbar notification support in [MediaSelectionScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/MediaSelectionScreen.kt) using a Compose `SnackbarHostState` inside the screen Scaffold.
  2. Added a reactive `LaunchedEffect` that automatically displays `"Media extracted"` when post details load successfully into the UI cards.
  3. Configured the download button's click handler to post a snackbar message specifying either `"Started Reels Downloading"` (if the download includes reels or videos) or `"{count} Images Download Started"` as the download queues.
  4. Resolved build issues by importing `kotlinx.coroutines.launch` in [MediaSelectionScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/MediaSelectionScreen.kt).
  5. Verified the project compiles successfully using `.\gradlew.bat assembleDebug`.
- **Type of Details:** New Update & Refactor
- **Description:**
  1. Fixed the history screen not displaying downloaded posts by passing extraction metadata (postId, postUrl, username, caption, media count, and type) inside Intent Extras from the download queue in [VedInstaApplication.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/VedInstaApplication.kt) down to [DownloadService.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/service/DownloadService.kt).
  2. Implemented `saveDownloadedPostToDb` inside [DownloadService.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/service/DownloadService.kt) using a coroutine-safe `Mutex` lock to serialize writes and persist completed downloads safely into the Room database.
  3. Enforced the filename naming convention `{username}_{timestamp_in_ms}.ext` across all download triggers (selected downloads, single downloads, background downloads) inside [VedInstaApplication.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/VedInstaApplication.kt).
  4. Removed the redundant `SessionStatusCard` from [MediaSelectionScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/MediaSelectionScreen.kt) to ensure active account session status is only displayed on the `SessionsScreen` tab.
  5. Implemented full edge-to-edge system bar rendering by calling `enableEdgeToEdge()` in [MainActivity.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/MainActivity.kt) and setting a dynamic `SideEffect` in [Theme.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/theme/Theme.kt) to handle status/navigation bar transparency and icon color modes.
  6. Wrapped [InstagramLoginScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/InstagramLoginScreen.kt) root layout in a `Scaffold` to automatically apply inset padding for the login screen's app bar.
  7. Verified the project compiles successfully using `.\gradlew.bat clean assembleDebug`.

---

- **Type of Details:** New Update & Refactor
- **Description:**
  1. Created a dedicated landing `HomeScreen.kt` under the `com.devson.vedinsta.ui` package.
  2. Implemented a beautiful gradient-styled welcome header showcasing dynamic time-based greetings (Good Morning/Afternoon/Evening) and active download statistics.
  3. Integrated interactive shortcut navigation cards linking directly to the Downloader and Favorites screens.
  4. Constructed a highly aesthetic uncontained horizontal carousel using `LazyRow` showcasing the top 8 recent downloads, complete with uploader usernames, play overlays for video media, and click redirection to post detail viewers.
  5. Implemented an elegant empty state card when no media has been downloaded yet, displaying a CTA button to get started.
  6. Refactored `MainActivity.kt` to extract the `AboutScreen` and `NotificationsScreen` composables into dedicated, self-contained files under the `ui/` folder, cleaning up activity orchestration.
  7. Updated the sealed class navigation destinations and configured `Screen.Home` as the starting route.
  8. Redesigned the `NavigationBar` bottom tab bar to clean up and display five core screens (Home, Downloader, History, Favorites, and Sessions), and re-routed the Settings screen to a dedicated icon button inside the `TopAppBar`.
  9. Verified the project compiles successfully using `.\gradlew.bat clean assembleDebug` with zero errors.
  ***
- **Type of Details:** New Update & Refactor
- **Description:**
  1. Created a reusable custom `@Composable` top app bar `VedInstaTopAppBar.kt` to unify styling, title types, and actions.
  2. Integrated `VedInstaTopAppBar` into the Scaffold definitions of `MainActivity.kt`, `PostViewScreen.kt`, and `InstagramLoginScreen.kt`.
  3. Re-configured navigation flows so that clicking the back arrow from any sub-screen redirects the user directly back to the Home Screen (`Screen.Home`).
  4. Integrated Compose `BackHandler` on all sub-screens to automatically hook physical back gestures to Home redirection.
  5. Configured the Settings and Notifications badge buttons to display exclusively on the Home Screen's TopAppBar actions block.
  6. Added a global Floating Action Button (FAB) featuring a download icon that routes users instantly to the Downloader screen, visible on all primary screens and automatically hidden on Downloader, Login, and PostView.
  7. Verified the project compiles successfully using `.\gradlew.bat clean assembleDebug` with zero errors.
  ***
- **Type of Details:** Refactor & New Update
- **Description:**
  1. Completely removed the bottom `NavigationBar` from the application.
  2. Extracted the entire app UI, Scaffold, screen stack state, grid selector dialog, and content routing logic from `MainActivity.kt` into a new package-level Composable `MainAppScreen.kt` acting as the central screen and navgraph.
  3. Cleaned up `MainActivity.kt` down to a lightweight activity host of under 50 lines.
  4. Redesigned `VedInstaTopAppBar.kt` to use the standard left-aligned Material 3 `TopAppBar` instead of `CenterAlignedTopAppBar` to align all screen names beautifully in the top-left corner.
  5. Added a new "Sessions" callback parameter to `HomeScreen.kt` and updated the "Quick Actions" panel from two cards to three columns, adding a new dedicated sessions navigation shortcut card with the `AccountBox` icon.
  6. Verified the project compiles successfully using `.\gradlew.bat clean assembleDebug` with zero errors.

---

- **Type of Details:** New Update & Refactor
- **Description:**
  1. Implemented a bottom action and detail section in `PostViewScreen.kt` using Material You design guidelines.
  2. Integrated a Favorite/Like toggle action button in the bottom section, securely linked to `MainAppScreen.kt`'s SharedPreferences favorite system.
  3. Relocated the share action from the top app bar to the bottom section, and enhanced it with a dynamic `DropdownMenu` supporting both "Share Current Media File" and "Share All Media Files" capabilities.
  4. Added a premium sliding `ModalBottomSheet` for the post description/caption with full `SelectionContainer` support for individual text selection.
  5. Implemented dynamic hashtag parsing inside the sheet, rendering interactive hashtag chips that copy specific hashtags on tap, and adding a "Copy All Hashtags" feature.
  6. Refactored `MainAppScreen.kt`'s back navigation to correctly restore origin screens (Home, History, Favorites) when backing out of post details.
  7. Eliminated the bottom and top padding in `PostViewScreen.kt` by bypassing the outer Scaffold content padding inside `MainAppScreen.kt` when `currentScreen is Screen.PostView` or `Screen.Login`, and applying only top padding inside `PostViewScreen.kt`'s root `Column`.
  8. Configured dynamic theme-aware background colors across all `PostViewScreen` layouts while maintaining an immersive black viewport for the image/video media.
  9. Added horizontal slide-transition screen navigation animations (left-to-right on opening new screens, and right-to-left on back navigation/closing screens) by wrapping the screen navigation host `when` block in `AnimatedContent` inside `MainAppScreen.kt`.
  10. Verified the project compiles successfully using `.\gradlew compileUniversalDebugKotlin` with zero errors.
- **Type of Details:** New Update & Refactor
- **Description:**
  1. Added a runtime notification permission request (`POST_NOTIFICATIONS`) at app startup inside `MainActivity.kt` for Android 13+ (API 33+) devices to resolve missing permissions on fresh installs.
  2. Overhauled `VedInstaNotificationManager.kt` to format batch download progress using standard slash notation (`current/total`) with a progress bar, and added `showSingleDownloadProgress` to support percentage progress displays.
  3. Modified `DownloadService.kt` to use a shared `postId.hashCode()` notification ID for batch downloads to prevent drawer spam and title flickering.
  4. Integrated byte-by-byte download stream tracking inside `DownloadService.kt` to update percentage progress in real-time for single downloads.
  5. Updated `EnhancedDownloadManager.kt` to support the `"is_batch"` input flag, suppress individual file notifications for batch tasks, and group background downloads under a shared notification ID.
  6. Refactored `VedInstaApplication.kt` to pass `"is_batch"` flags and update batch progress notifications (`finishedCount/expectedCount`) dynamically on WorkManager updates.
  7. Ensured all active progress notification IDs are cleanly cancelled when tasks reach terminal states.
  8. Verified the project compiles successfully using `.\gradlew compileUniversalDebugKotlin` with zero errors.

---

- **Type of Details:** Refactor / Performance Improvement
- **Description:**
  1. Removed raw `@username` and internal filenames from all notification titles — replaced with branded `VedInsta · Downloading` across `DownloadService`, `EnhancedDownloadManager`, and `VedInstaNotificationManager`.</p>
  2. Batch progress notification now shows `X of Y files (Z%)` in content text and `N files remaining` in subText.
  3. Single-file progress notification now shows file type (`MP4 file` / `JPG file`) as title and `47% downloaded` as content text.
  4. Completion notification upgraded to `PRIORITY_DEFAULT` with `Tap to open` subText.
  5. `showDownloadCompleted(fileName, totalFiles)` overload now shows `N files saved` or `File saved to gallery` instead of the raw filename.

---

- **Type of Details:** Bug Fix
- **Description:**
  1. Video/reel previews were blank in `MediaSelectionScreen` because the video stream URL was used instead of a static cover image. Fixed by adding `thumbnail_url` to `mo3.py`'s video result (from `image_versions2`), mapping it to `MediaResult.thumbnailUrl`, and using it as the preview source for video items.
  2. Post cards in HomeScreen, HistoryScreen, and FavoritesScreen were blank for reels because `AsyncImage` cannot decode video files as static images. Fixed by registering Coil's `VideoFrameDecoder` globally in `VedInstaApplication.onCreate()` and building an `ImageRequest` with `videoFrameMillis(0)` for any `.mp4`/`.mov` thumbnail path.
  3. Build verified: `compileUniversalDebugKotlin` passes with zero errors.

---

- **Type of Details:** Bug Fix
- **Description:**
  1. Root cause: Coil `AsyncImage` cannot decode video (`.mp4`) files as thumbnails without a `VideoFrameDecoder` registered in the `ImageLoader`.</p>
  2. Implemented `ImageLoaderFactory` in `VedInstaApplication` so Coil's global singleton loader includes `VideoFrameDecoder`. This makes video thumbnails render automatically across all screens with zero UI changes.</p>
  3. Updated `AsyncImage` in `HistoryScreen` and `HomeScreen` to use `ImageRequest` with `videoFrameMillis(0L)` to explicitly request the first video frame for thumbnails.</p>
  4. Enabled memory and disk caching on the ImageLoader for fast repeat renders.</p>
  5. `FavoritesScreen` inherits the fix automatically as it delegates to `HistoryScreen`.</p>

---

- **Type of Details:** Error Solving / Build Configuration
- **Description:** Migrated from KAPT to KSP (Kotlin Symbol Processing) to fix the build warning Kapt currently doesn't support language version 2.0+. Falling back to 1.9. The project uses Kotlin 2.0.21, which is incompatible with KAPT. Changes made:
  1. Added ksp = "2.0.21-1.0.28" version entry to gradle/libs.versions.toml.
  2. Added ksp plugin alias to [plugins] in gradle/libs.versions.toml.
  3. Replaced kotlin("kapt") plugin with lias(libs.plugins.ksp) in pp/build.gradle.kts.
  4. Replaced kapt(libs.androidx.room.compiler) with ksp(libs.androidx.room.compiler) in pp/build.gradle.kts.

---

- **Type of Details:** Documentation / UI Enhancement
- **Description:** Added detailed session storage explanation to SessionsScreen.kt. The old generic "Security & Privacy" one-liner card was replaced with a structured three-section info card covering:
  1. **Where session files are stored** — instagram_cookies.txt lives in the app-level internal storage sandbox (context.filesDir ? /data/data/com.devson.vedinsta/files/), enforced by Android's Linux kernel permissions. Not accessible by any other app, file manager, or ADB on non-rooted devices. NOT in the user-visible Android/data/ external storage.
  2. **Uninstall behavior** — Android auto-deletes the entire app sandbox (/data/data/<package>/) on uninstall, wiping the cookie file, EncryptedSharedPreferences, and Room DB.
  3. **"Log Out Session" vs. Instagram account logout** — Tapping the button only removes local cookies inside VedInsta. It does NOT revoke the session on Instagram's servers; the account stays active on other devices and the Instagram app.
  Inline code comments were also added explaining each behaviour for future developers.

---

- **Type of Details:** Refactor / UI Improvement
- **Description:** Improved UI layout and sheet transition animations in PostViewScreen.kt:
  1. Removed duplicate username from caption summary, allowing description to occupy full width.
  2. Moved "Downloaded on [Date]" label from the Top Bar to the bottom section, directly below the action buttons.
  3. Reduced action icon sizes from 28.dp to 22.dp for cleaner look.
  4. Moved rememberModalBottomSheetState declaration to the top level of PostViewScreen to prevent state recreation, resolving the buggy stuttering animation when opening/closing the description bottom sheet.

---

- **Type of Details:** New Update / UI Component
- **Description:** Added ViewSettingBottomSheet component for grid/list customization inside HistoryScreen.kt and FavoritesScreen.kt. Features:
  1. **Layout Modes** — Toggle between a clean Grid View and a newly-implemented List View.
  2. **Grid Size Slider** — Adjust grid size between 2 to 4 columns via a premium slider in the bottom sheet.
  3. **Pinch-to-Adjust Grid** — Implemented standard gallery pinch gesture (detectTransformGestures) to dynamically adjust the grid column count between 2 and 4 by pinching on the screen.
  4. **List View Layout** — Shows clipped thumbnails with top-left badges indicating media type (video/carousel) and elegant details (bold username title on the first line, and truncated description on the second line ending with '...').
  5. **Hamburger Menu Toggle** — The top right corner action button now invokes the new premium ViewSettingBottomSheet.

---
