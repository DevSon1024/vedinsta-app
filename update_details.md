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
  1. Removed raw `@username` and internal filenames from all notification titles  replaced with branded `VedInsta · Downloading` across `DownloadService`, `EnhancedDownloadManager`, and `VedInstaNotificationManager`.</p>
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
  1. **Where session files are stored**  instagram_cookies.txt lives in the app-level internal storage sandbox (context.filesDir ? /data/data/com.devson.vedinsta/files/), enforced by Android's Linux kernel permissions. Not accessible by any other app, file manager, or ADB on non-rooted devices. NOT in the user-visible Android/data/ external storage.
  2. **Uninstall behavior**  Android auto-deletes the entire app sandbox (/data/data/<package>/) on uninstall, wiping the cookie file, EncryptedSharedPreferences, and Room DB.
  3. **"Log Out Session" vs. Instagram account logout**  Tapping the button only removes local cookies inside VedInsta. It does NOT revoke the session on Instagram's servers; the account stays active on other devices and the Instagram app.
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
  1. **Layout Modes**  Toggle between a clean Grid View and a newly-implemented List View.
  2. **Grid Size Slider**  Adjust grid size between 2 to 4 columns via a premium slider in the bottom sheet.
  3. **Pinch-to-Adjust Grid**  Implemented standard gallery pinch gesture (detectTransformGestures) to dynamically adjust the grid column count between 2 and 4 by pinching on the screen.
  4. **List View Layout**  Shows clipped thumbnails with top-left badges indicating media type (video/carousel) and elegant details (bold username title on the first line, and truncated description on the second line ending with '...').
  5. **Hamburger Menu Toggle**  The top right corner action button now invokes the new premium ViewSettingBottomSheet.

---

- **Type of Details:** UI Enhancement / Gestures
- **Description:** Optimized Grid View details in HistoryScreen.kt and FavoritesScreen.kt:
  1. **Hide Icons in 4-column Grid**: When grid is adjusted to 4 columns, all overlay badges (video/carousel) and the favorite heart button are hidden. Only the media thumbnail is visible.
  2. **Silky Smooth Pinch-to-Adjust Gesture**: Rewrote pinch-to-adjust gesture detection to use PixChive's robust gesture system (waitEachGesture, waitFirstDown, calculateZoom) for seamless, native scaling transitions between 2 to 4 grid columns.

---

- **Type of Details:** UI Refactor / Media View
- **Description:** Updated VideoPlayer layout inside PostViewScreen.kt:
  - Replaced the hardcoded horizontal 16:9 aspect ratio modifier on the VideoView's AndroidView with fillMaxSize(). This allows vertical reels, IGTVs, and standard videos to expand to their full layout size and naturally fill the immersive post viewport.

---

- **Type of Details:** UI Enhancement / Settings
- **Description:** Updated PostViewScreen.kt and SettingsScreen.kt:
  - **Large Username Title**: Increased the username font size from 16.sp to 22.sp in the Post Viewer top app bar to match other screens' titles.
  - **Transparent Bottom Navbar**: Added 	ransparent_navbar toggle preference in SettingsManager.kt. When enabled via the newly added "Transparent Bottom Bar" switch item in SettingsScreen.kt, the bottom bar container (Surface) in PostViewScreen.kt becomes completely transparent and omits the extra navigation bar padding, creating a modern edge-to-edge transparent layout where the media extends all the way down.

---

- **Type of Details:** UI Refactor / Appearance Settings / Theme Integration
- **Description:** Successfully integrated the premium dynamic theme selection system and edge-to-edge transparency:
  1. **Top Bar Font Size**: Unified the PostViewScreen.kt username title formatting to use the standard large page title (22.sp) by passing it directly to the standard title field of VedInstaTopAppBar.
  2. **Appearance settings Integration**: Added Screen.Appearance definition to MainAppScreen.kt, mapping it to render the new AppearanceSettingsScreen, and connected it directly to SettingsScreen.kt's "App Theme" menu selection.
  3. **ViewModel Integration**: Refactored AppearanceSettingsScreen.kt to receive and use the shared settingsViewModel instance supplied by MainActivity.kt and MainAppScreen.kt.
  4. **System Navigation Bar edge-to-edge Support**: Set the main scaffold's navigation bar padding to 0.dp to allow layout drawing behind the transparent system navigation bar, and integrated navigationBarsPadding() directly inside HomeScreen.kt, HistoryScreen.kt (List and Grid layouts), SessionsScreen.kt, SettingsScreen.kt, AboutScreen.kt, and NotificationsScreen.kt.

---

- **Type of Details:** UI Enhancement / Layout Fix
- **Description:** Resolved a bottom padding issue in HistoryScreen.kt and FavoritesScreen.kt when drawing edge-to-edge content:
  - Removed .navigationBarsPadding() from the LazyColumn and LazyVerticalGrid modifiers to allow the viewport scroll region to expand fully behind the transparent system navigation bar area.
  - Utilized the contentPadding parameter inside LazyColumn and LazyVerticalGrid to cleanly pad the list and grid items bottom margin by the system navigation bar's inset height plus 80.dp. This prevents the FloatingActionButton and navigation bar from overlapping or blocking any grid card row when scrolled to the very bottom, creating a premium seamless scroll experience.

---

- **Type of Details:** New Update / UI Component
- **Description:** Implemented a premium Material You Carousel selection screen inside MediaSelectionScreen.kt:
  - **Horizontal Pager Carousel**: Added a custom HorizontalPager with horizontal peeking edges (contentPadding = 48.dp, pageSpacing = 16.dp) and an immersive scale/alpha transformation animation driven by scroll offset.
  - **Conditionally Adaptive Top Bar**: Displays the center-aligned username and a select-all square checkbox option at the top right of the page when extraction succeeds. The back button resets the view back to the input form.
  - **Interactive Selection & Quality Selectors**: Placed individual square checkboxes at the top-right corner of each media card and a resolution selection pill at the bottom-center of the card driven by a DropdownMenu quality selector.
  - **Animated Indicators & Custom Download Button**: Included pager indicator dots and a rounded Download button matching the selected color theme.
  - **Changelog**: Maintained change entries inside update_details.md.

---

- **Type of Details:** UI Enhancement / Carousel Refinement
- **Description:** Enhanced and polished the media extraction carousel screen inside MediaSelectionScreen.kt:
  - **Animated Pager Indicators**: Upgraded the pager indicator dots row to smoothly animate dot size and color based on active swipe scroll offsets.
  - **Mockup-Matched Quality Selector**: Standardized the resolution text inside the dropdown pill to display in user-marked PX format (e.g. 1080 PX, 720 PX) dynamically calculated from the shorter side of the selected media.
  - **Smart Fallback Download Button**: Kept the primary filled download button always enabled visually. Implemented intelligent fallback behavior where clicking the button when no elements are selected automatically downloads the currently viewed carousel card.
  - **Compilation Verification**: Validated full compilation of the app module against the updated layout without errors.

---

- **Type of Details:** UI Enhancement / TopBar and Preview Refinements
- **Description:** Excluded the duplicate top bar from the downloader screen and polished selection details:
  - **Duplicate TopBar Removal**: Added exclusion check inside MainAppScreen.kt to hide the global scaffolding top app bar when Downloader is visible, avoiding header duplication.
  - **Arrow Back Navigation**: Added back arrow button to the input form's top app bar linking to navigation back action.
  - **Medium Quality Preview**: Upgraded card media preview loader to load intermediate resolution (medium quality) image/video URLs rather than the lowest quality, improving image clarity.
  - **Raw Resolution Dropdowns**: Modified dropdown menu options to show the raw resolution heightxwidth (e.g. 720x1080) for detailed dimension inspection.
  - **Unknown Username Fallback**: Substituted "epic_top_comments_india" default fallback with "Unknown" if username is blank or missing.

---

- **Type of Details:** Error Solving / UI Polish / Notification Fix
- **Description:** Resolved inner Scaffold double-padding and fixed in-app notifications:
  - **Top Padding Solution**: Configured contentWindowInsets = WindowInsets(0, 0, 0, 0) on Scaffold and removed a Spacer inside MediaSelectionScreen.kt to completely eliminate unnecessary top status bar insets double-padding.
  - **In-App Notifications Logging**: Integrated custom notification inserts via addCustomNotification in DownloadService.kt. Now, all successful and failed single/batch downloads are properly stored in the local Room database and instantly visible on the application's internal notifications screen.

---

- **Type of Details:** Performance Improvement / Refactor / Cache Optimization
- **Description:** Optimized and refactored the image loading and download caching architecture to minimize the internal disk footprint of the app:
  - **Coil Disk Cache Disabling**: Updated global `ImageLoader` in `VedInstaApplication.kt` and all `AsyncImage` instances across `PostViewScreen.kt`, `MediaSelectionScreen.kt`, `HomeScreen.kt`, and `HistoryScreen.kt` to disable disk caching while keeping memory caching enabled to preserve UI scrolling performance.
  - **Prioritize Local URIs**: Implemented a reactive database query using Compose `produceState` on `Dispatchers.IO` in `MediaSelectionScreen.kt`. It scans if the post has already been downloaded, and if so, retrieves the local `File` paths and serves them to `AsyncImage` instead of the Instagram network URLs.
  - **Failsafe Cache Clearer**: Implemented a recursive cache clearer utility in `VedInstaApplication` that runs on app start and after download completions. It deletes all files in the cache directory, explicitly skipping any paths containing `"python"` or `"chaquopy"` to protect the Python environment.
  - **Download & Stream Cleanup**: Refactored the HTTP download loop in `DownloadService.kt` to ensure failed or incomplete downloads are deleted on `Dispatchers.IO`, and calls the application cache clearer when all active downloads in the service are finished.

---

- **Type of Details:** UI Enhancement / Session Management / Security
- **Description:** Added a logout confirmation dialog in SessionsScreen.kt:
  - **Confirmation Dialog**: Implemented a custom Material 3 `AlertDialog` triggered by the "Log Out Session" button to verify logout intent before clearing saved cookies and credentials.
  - **State Management**: Integrated `showLogoutDialog` Compose state for dialog visibility.

---

- **Type of Details:** Refactor / Build Configuration
- **Description:** Separated application names between Beta (debug) and Release versions:
  - **Dynamic App Name String**: Removed the hardcoded `app_name` string from `strings.xml`.
  - **Gradle resValue Configuration**: Added `resValue("string", "app_name", "VedInsta Beta")` inside `debug` build type and `resValue("string", "app_name", "VedInsta")` inside `release` build type in `app/build.gradle.kts`.

---

- **Type of Details:** Release Build Optimization & Refactor
- **Description:**
  1. Removed the deprecated `productFlavors` architecture-splitting setup from `app/build.gradle.kts` which re-compiled the codebase multiple times.
  2. Implemented native Gradle `splits` block for `arm64-v8a` and `x86_64` ABIs, running R8 optimizations only once globally and packaging highly optimized, separate APKs for each architecture.
  3. Set target ABIs to 64-bit (`arm64-v8a` and `x86_64`) to align with Python 3.13 / Chaquopy limitations (which dropped 32-bit `armeabi-v7a` and `x86` support).
  4. Added dynamic single-ABI debug filters (`arm64-v8a`) to speed up debug compilation times to seconds.
  5. Configured robust release-signing configuration supporting local `keystore.properties` loading with fallback to system environment variables for CI/CD pipelines.
  6. Enabled Gradle parallel build execution (`org.gradle.parallel=true`) and caching (`org.gradle.caching=true`) in `gradle.properties`.
  7. Enabled R8 Full Mode (`android.enableR8.fullMode=true`) for aggressive runtime optimizations and smaller APK packaging size.
  8. Configured extensive optimization keep rules in `app/proguard-rules.pro` for Gson, Room, Chaquopy Python integrations, Coil, OkHttp, Coroutines, and the application's models and database entity classes to prevent reflection failures or class stripping under R8.
  9. Successfully verified the build compiles using `.\gradlew assembleDebug` (1m 49s) and `.\gradlew assembleRelease` (2m 44s), producing split and universal release APKs (reducing arm64 download size to 17.48 MB).

---

- **Type of Details:** UI Refactor & New Update
- **Description:**
  1. Exchanged the placement of the Settings and Notifications action buttons in the top app bar for `Screen.Home` only. The Notifications badge icon is now placed first, and the Settings icon is placed second (at the end).
  2. Applied an end padding of 8.dp to the Settings icon on the Home screen to ensure correct layout spacing.
  3. Added the Settings action icon to the end of the top app bar on History and Favorites screens next to the View Settings (Tune) action button.
  4. Verified the project compiles successfully using `.\gradlew assembleDebug`.

---

- **Type of Details:** Performance Improvement & Error Solving
- **Description:**
  1. Resolved app cold start lag by initializing the Chaquopy Python interpreter asynchronously on a background IO thread (`Dispatchers.IO`) in `VedInstaApplication.kt` instead of synchronously blocking the Main Thread in `onCreate()`.
  2. Implemented check-and-wait polling loops (up to 5 seconds with 100ms delays) in `MediaFetcherRepository.kt`, `InstagramAuthViewModel.kt`, and `SharedLinkProcessingService.kt` to safely suspend operations and wait for background Python startup, ensuring no errors or crashes if the user interacts immediately on app boot.
  3. Fixed severe overall device and application lagging caused by recursive cache clearing on startup and download completion by restricting `clearAppCache` to ONLY clear the temporary `download_cache` folder, preventing disk I/O thrashing and conflicts with Chaquopy's internal Python assets.
  4. Verified the project compiles successfully using `.\gradlew assembleDebug`.

---

- **Type of Details:** UI Enhancement / Bug Fix
- **Description:**
  1. Updated the Resolution Selection Pill in `MediaSelectionScreen.kt` to use a capsule shape (`RoundedCornerShape(16.dp)`).
  2. Applied `.clip(RoundedCornerShape(16.dp))` before the `.clickable` modifier to restrict the click ripple animation to the rounded boundaries of the pill, eliminating the rectangular/square selection feedback.
  3. Verified the project compiles successfully using `.\gradlew clean assembleDebug`.

---

- **Type of Details:** Refactor & New Update
- **Description:**
  1. Split the single-page media downloader interface into two distinct pages: input page (`MediaSelectionScreen.kt`) and a separate media selection and download details page (`MediaSelectionDetailScreen.kt`).
  2. Simplified `MediaSelectionScreen.kt` to display only the Instagram URL paste input form and action button, along with idle, loading, and error state indicators.
  3. Integrated a dynamic transition in `MediaSelectionScreen` using `LaunchedEffect(extractionState)` to automatically navigate to the details page upon successful media extraction.
  4. Implemented `MediaSelectionDetailScreen.kt` using a horizontal carousel, selection checkboxes, quality selection dropdowns, page indicator dots, and a primary download action button.
  5. Added `lastExtractedUrl` state in `MediaExtractionViewModel` to store the target URL during extraction.
  6. Configured `MainAppScreen.kt` navigation graph to support `Screen.DownloaderDetails`, hiding default app bars and the floating action button on this screen, and resetting extraction state to `Idle` on back navigation.
  7. Adjusted top padding in `MainAppScreen.kt` to exclude both `Screen.Downloader` and `Screen.DownloaderDetails` from standard app bar padding, allowing both header layouts to extend properly edge-to-edge under the status bar.
  8. Verified the project compiles successfully using `.\gradlew assembleDebug`.

---

- **Type of Details:** UI Enhancement / Refactor
- **Description:**
  1. Added a blurred background image effect for images in [PostViewScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/PostViewScreen.kt) to replace the default letterbox/pillarbox black strips.
  2. Applied a 45% opacity dark overlay on the blurred background image to maintain readability and separation with the primary foreground image.
  3. Integrated Coil's built-in image `crossfade` animation on both background and foreground image loaders to create smooth fade-in transitions.
  4. Added a premium page-swipe transition using Compose `graphicsLayer` to animate `alpha` and `scale` dynamically based on pager scroll offsets.
  5. Verified the project compiles successfully using `.\gradlew compileDebugKotlin`.

---

- **Type of Details:** Performance Improvement
- **Description:**
  1. **Coil VideoFrameDecoder disk cache enabled** — `newImageLoader()` in `VedInstaApplication.kt` was changed from `CachePolicy.DISABLED` to `CachePolicy.ENABLED` for the disk cache. A dedicated `DiskCache.Builder()` instance is now configured to write decoded video-frame thumbnails to `cacheDir/coil_video_frames/` with a hard cap of **20 MB** (`20L * 1024 * 1024`). This eliminates the constant CPU re-extraction of frames that caused the "Skipped 97 frames" jank and high background CPU time during list scroll.
  2. **`getPostInfo()` moved off the main thread** — Refactored the previously blocking `fun getPostInfo(url: String)` to a `suspend fun getPostInfo(url: String): JSONObject?` that wraps its entire body in `withContext(Dispatchers.IO)`. All Chaquopy Python initialisation (`Python.getInstance()`), script execution (`module.callAttr`), and JSON parsing (`JSONObject(resultString)`) now execute strictly on an IO background thread, resolving the primary source of GC churn and main-thread overload visible in Logcat.

---

- **Type of Details:** Performance Improvement
- **Description:**
  1. **Thumbnail Downsampling Optimization** — Resolved massive memory consumption and scrolling stuttering in `HistoryScreen.kt` and `HomeScreen.kt` by replacing Coil's `.size(Size.ORIGINAL)` parameter with `.size(300, 300)` on all local downloaded post thumbnail requests.
  2. **Eliminated Out-Of-Memory Risks** — By downsampling high-resolution images and video frames to a light $300 \times 300$ px preview boundary, memory footprint drops by ~99% per thumbnail, drastically reducing garbage collection thrashing and delivering a buttery-smooth 60/120fps scrolling experience.

- **Type of Details:** New Update & Refactor
- **Description:**
  1. **Built Native Privacy & Policy Screen** — Designed and created `PrivacyPolicyScreen.kt` using standard Material Design 3 guidelines. It features elegant cards outlining data security guarantees, on-device sandboxed execution via Chaquopy Python, secure EncryptedSharedPreferences session cookie storage, zero tracking logs policy, and localized public file-writing logic.
  2. **Integrated Navigation & Settings Actions** — Added the `PrivacyPolicy` destination to the `Screen` sealed class inside `MainAppScreen.kt`, mapped its top app bar header title, and integrated it into the navigation host. Refactored `SettingsScreen.kt` to accept the `onNavigateToPrivacyPolicy: () -> Unit` callback, substituting the external GitHub repository browser intent with smooth in-app horizontal screen transitions.

- **Type of Details:** Performance Improvement
- **Description:**
  1. **Sequential Download Queue Pipeline** — Implemented a coroutine `Channel` sequential download request queue in `DownloadService.kt`. The service now serializes file downloads instead of initiating them concurrently in parallel.
  2. **Eliminated System UI Freezes** — By processing downloads sequentially on `Dispatchers.IO`, random-write disk I/O lockups and network bandwidth saturation are entirely resolved, ensuring the app remains perfectly responsive and fluid during active downloads.

---

- **Type of Details:** Performance Improvement / New Update
- **Description:**
  1. **Integrated Batch Intent Processing inside Queue** — Updated `DownloadService.kt` to natively parse string array list extras (`download_urls_list`, `file_paths_list`, `file_names_list`, `media_types_list`). The service now processes these list-based extras dynamically, ensuring all media elements are sent to the sequential coroutine `Channel` queue.
  2. **Eliminated Multiple Service Start Churn** — Avoided service recreation and repetitive `startForeground()` binder transactions by sending the entire batch list in a single intent. This keeps the service alive dynamically for the duration of all sequential downloads and reduces UI lag down to zero.
- **Type of Details:** Error Solving & Build Verification
- **Description:**
  1. Resolved the release build task `:app:installReleasePythonRequirements` failure by manually triggering requirements installation to successfully resolve DNS, download, and install necessary packages (`requests`, `pillow`, `beautifulsoup4`) into the Gradle Python release environment.
  2. Verified that the full release compilation pipeline compiles and packages successfully by executing `./gradlew assembleRelease`, which completed successfully (3m 47s) and generated all native split ABI and universal release APKs.

- **Type of Details:** Error Solving & Build Optimization
- **Description:**
  1. Resolved the Windows-specific R8 file-locking error (`java.nio.file.FileSystemException: classes.dex: The process cannot access the file because it is being used by another process`) by terminating all conflicting background Gradle Daemons using `gradlew --stop`.
  2. Purged the intermediate build directories using `gradlew clean` to release all open file handles, ensuring a clean state for subsequent compilations.

---

- **Type of Details:** Major Refactor & Performance Improvement
- **Description: Complete Python/Chaquopy Removal — Native Kotlin Media Extractor Migration**
  1. **Created `InstagramNativeExtractor.kt`** — A pure Kotlin `object` singleton replacing the entire `mo3.py` Python script. Uses only `java.net.HttpURLConnection` and `org.json` (zero new external dependencies). Implements:
     - Mathematical `shortcodeToId()` conversion using `BigInteger` (identical algorithm to the Python implementation).
     - Netscape/Mozilla cookie file parser (`parseCookies()`) supporting `instagram.com` domain filtering.
     - Authenticated `performGetRequest()` with correct `User-Agent`, `X-IG-App-ID`, `X-CSRFToken`, and `Cookie` headers.
     - Full carousel/video/image media parsing via `parseItems()` and `parseBest()`.
     - Graceful error handling for HTTP 401/403 (`login_required`), 404 (`not_found`), and general exceptions.
  2. **Purged Chaquopy & Python runtime** — Removed `chaquopy` plugin from `build.gradle.kts` (app) and `build.gradle.kts` (project), deleted `app/src/main/python/mo3.py` and the entire `app/src/main/python/` directory. Stripped all `Python.isStarted()` / `Python.getInstance()` references from `VedInstaApplication.kt`, `InstagramAuthViewModel.kt`, and `SharedLinkProcessingService.kt`.
  3. **Zero I/O on Main Thread** — All `InstagramNativeExtractor` invocations are wrapped in `withContext(Dispatchers.IO)`: `getPostInfo()` in `VedInstaApplication.kt`, `fetchPostData()` in `SharedLinkProcessingService.kt`, `fetchRealUsernameInBackground()` in `InstagramAuthViewModel.kt`, and `fetchMedia()` in `MediaFetcherRepository.kt`.
  4. **Stale Python guard removed** — Deleted the Python/Chaquopy directory exclusion guard from the `deleteRecursive()` helper in `VedInstaApplication.kt`, which was dead code after Python removal.
  5. **Deprecated API suppressed** — Added `@Suppress("DEPRECATION")` to `databaseEnabled = true` in `InstagramLoginScreen.kt` (WebSQL deprecated in API 26+; retained for WebView compat).
  6. **UI text updates** — Updated `PrivacyPolicyScreen.kt`, `AboutScreen.kt`, and `MediaSelectionScreen.kt` to reflect the native extraction technology.
  7. **Build Verified** — `.\gradlew assembleDebug` completed successfully (`BUILD SUCCESSFUL`) with 0 errors and 3 pre-existing warnings unrelated to this migration.

---

- **Type of Details:** Performance Improvement & New Update
- **Description:**
  1. Optimized preview loading by retrieving lower-resolution media previews instead of high-resolution files or video streams.
  2. Modified the native extraction parser in `InstagramNativeExtractor.kt` to extract all video thumbnail candidates from `image_versions2.candidates` and include them in a new `thumbnail_qualities` JSON array.
  3. Updated `MediaResult.kt` model to map `thumbnail_url` and `thumbnail_qualities` fields.
  4. Implemented low-resolution preview heuristics in `MediaSelectionCarouselScreen.kt` for both images and video thumbnails, selecting the smallest quality option at or above 360px width to ensure visual clarity while saving internet data.
  5. Switched Coil's disk cache policy to `CachePolicy.ENABLED` in `MediaSelectionCarouselScreen.kt` to cache loaded preview thumbnails locally.
  6. Verified that the project builds and compiles successfully via `.\gradlew.bat assembleDebug`.

---

- **Type of Details:** New Update & Refactor
- **Description:**
  1. Updated `MediaSelectionCarouselScreen.kt` download button click handler to navigate back to `MediaSelectionScreen.kt` using `onNavigateBack()` and show a persistent Toast alert feedback immediately upon download trigger.
  2. Implemented active download progress tracking in the background database by introducing `updateProgressInDb` and `removeProgressFromDb` in `DownloadService.kt`. The transient `DOWNLOAD_PROGRESS` notification entity keeps real-time percent updates.
  3. Added progress bar rendering in `NotificationsScreen.kt` using a Material Design 3 `LinearProgressIndicator` bound to active progress notifications, disabling clicks and deletions for active progress rows.
  4. Modified `NotificationDao.kt` to query and delete progress notifications, and updated unread count and `markAllAsRead` logic to accept a type parameter so that active progress notifications do not trigger or modify the notifications number badge on the HomeScreen.
  5. Verified the project compiles successfully using `.\gradlew.bat assembleDebug`.

---

- **Type of Details:** New Update & Refactor
- **Description:**
  1. Updated progress reporting format from percentage ("X%") to count fraction ("X/Y") for batch downloads across `DownloadService.kt` and `NotificationsScreen.kt`.
  2. Added the `onNavigateToNotifications` callback to `MediaSelectionCarouselScreen.kt` to route users directly to the Notifications screen upon starting a download.
  3. Mapped the navigation flow in `MainAppScreen.kt` so that popping the downloader details transitions to the notifications page, while back gestures from the notifications page return users to the input screen.
  4. Restricted Floating Action Button (FAB) visibility in `MainAppScreen.kt` to hide the download button on the Settings and Notifications screens.
  5. Adjusted layout padding in `NotificationsScreen.kt` to allow list items to scroll completely behind the transparent system navigation bar at the bottom.
  6. Verified the project compiles successfully using `.\gradlew.bat assembleDebug`.

---

- **Type of Details:** New Update & Refactor
- **Description:**
  1. Added a notification limits settings option in `NotificationsScreen.kt` using a header Card and RadioButton selection dialog to prune older notifications immediately.
  2. Persisted notification limits configuration under `max_notifications_limit` in `SettingsManager.kt` (options: 10, 25, 50, 100, or Unrestricted).
  3. Modified the database query in `NotificationDao.kt` to sort active progress indicators to the top, and added the subquery delete operation to clean up history beyond chosen limits.
  4. Configured automatic pruning on entry of the notifications screen via `LaunchedEffect(maxNotificationsLimit)` in `MainAppScreen.kt`.
  5. Implemented `showDownloadStartedPopup` in `VedInstaNotificationManager.kt` and integrated it into `DownloadService.kt` to trigger a system heads-up popup banner when downloads start.
  6. Removed legacy `Toast` popup notification triggers on download initiation from `MediaSelectionCarouselScreen.kt`.
  7. Verified the project compiles successfully using `.\gradlew.bat assembleDebug`.

---

- **Type of Details:** Error Solving & UI Alignment
- **Description:**
  1. Resolved the off-center number placement in the notification badge on the Home Screen top app bar in `MainAppScreen.kt`.
  2. Nested the badge `Box` directly inside the `IconButton` content relative to the 24.dp `Icon` boundaries with a refined offset (`offset(x = 4.dp, y = (-4).dp)`).
  3. Utilized `CircleShape` and set `includeFontPadding = false` within `PlatformTextStyle` to completely eliminate default Android font padding and center single-digit/badge numbers perfectly.
  4. Verified the project compiles successfully using `.\gradlew.bat assembleDebug`.

---

- **Type of Details:** New Update & Refactor
- **Description:**
  1. Updated the default playing state of the video player in `PostViewScreen.kt` to paused.
  2. The video will now load in a paused state showing the play overlay button, and will begin playing only when the user taps/clicks the play overlay icon.

---

- **Type of Details:** New Update & Refactor
- **Description:**
  1. Relocated Notification Settings to a TopAppBar Tune icon action on the Notifications screen, which opens a bottom sheet with an Unrestricted toggle and limit count slider.
  2. Integrated media thumbnail previews in completed notifications utilizing Coil `AsyncImage` to load crop-focused previews of local download files.
  3. Configured direct post-view navigation from notifications list tapping, which resolves the corresponding `DownloadedPost` from the database.
  4. Added a horizontal three-dots `MoreHoriz` IconButton next to the Share button on the post detail viewer screen.
  5. Implemented dynamic backdrop blur on the main view container in `PostViewScreen.kt` when the options bottom sheet is visible.
  6. Added "Open in Instagram" option to launch the official Instagram app targeting the post ID with a safe web browser fallback.
  7. Resolved compilation deprecation warnings for `Launch` icon by migrating to `Icons.AutoMirrored.Filled.Launch`.
  8. Verified the project compiles and runs successfully with zero Kotlin compilation errors.

---

- **Type of Details:** Major UI Refactor / New Update
- **Description:** Refactored `PostViewScreen.kt` for premium native immersive media presentation:
  1. **Removed Scaffold + VedInstaTopAppBar** — Replaced with a raw `Box` root container for a fully immersive, chromeless layout.
  2. **Edge-to-Edge Media Carousel** — Media viewport now extends under the system status bar with zero top padding, maximizing visual immersion.
  3. **Floating Back Button** — Added a semi-transparent circular back button (`Icons.AutoMirrored.Filled.ArrowBack`) overlaid on the media surface at `Alignment.TopStart` with `statusBarsPadding()` to avoid hardware notch clipping.
  4. **Carousel Pager Dot Indicators** — Replaced the old "1/N" text bubble with native horizontally centered dot indicators. Active dots use `MaterialTheme.colorScheme.primary` at 8.dp; inactive dots use muted colors at 6.dp. Only visible when media count > 1.
  5. **Integrated Username + Caption Typography** — Combined `@username` (bold, primary color) and post description into a single semantic `Text` element using `buildAnnotatedString` with `SpanStyle`. Clickable to open the full description bottom sheet.
  6. **Bottom Utilities Action Bar** — Sleek horizontal action dock at the bottom edge with `navigationBarsPadding()`. Contains Delete (left), Menu with circular outlined border (center), and Share with dropdown (right) actions spaced with `Arrangement.SpaceEvenly`.
  7. **Animated Contextual Backdrop Blur** — Replaced the hard-toggle blur with smooth `animateDpAsState` (300ms `tween` with `FastOutSlowInEasing`). Added `Build.VERSION.SDK_INT` check: uses `Modifier.blur()` on API 31+ and a semi-transparent dark overlay (`animateFloatAsState`) as fallback on older devices.
  8. **Relocated Actions** — Moved Copy Link and Favorite toggle into the More Options `ModalBottomSheet`, cleaning up the old top app bar.
  9. Verified the project compiles successfully using `.\gradlew assembleDebug` with zero errors.

---

- **Type of Details:** UI Polish / Performance Improvement
- **Description:** Follow-up fixes to `PostViewScreen.kt` and `MainAppScreen.kt`:
  1. **Light status bar icons** — Added `DisposableEffect` using `WindowCompat.getInsetsController` to force white status bar icons over dark media content, restored on screen exit.
  2. **Dynamic media sizing** — Computed `mediaAspectRatio` from the first image file via `BitmapFactory.decodeFile(inJustDecodeBounds)`. Media viewport now uses `aspectRatio(ratio)` instead of `weight(1f)`, so the container wraps the actual content dimensions (clamped to 0.5–2.0 ratio range).
  3. **ContentScale.Fit** — Images are no longer cropped; they fit within the viewport preserving their native aspect ratio.
  4. **Scrollable content** — Media, dots, and caption are wrapped in a scrollable `Column` with `weight(1f)` so the bottom action bar stays pinned while tall content can scroll.
  5. **Page counter** — Added `"X/Y"` text next to the dot indicators for explicit page numbering.
  6. **Removed blurred background** — Eliminated the triple-layer blur (blurred bg + dimming overlay + foreground fit) from media rendering. Single `AsyncImage` with `ContentScale.Fit` replaces it.
  7. **Instant blur close** — Blur dismissal uses `snap()` (instant) instead of `tween(300ms)` for responsive sheet close feel.
  8. **Navigation transition fix** — Changed `MainAppScreen.kt` `AnimatedContent.transitionSpec` to use smooth 250ms `fadeIn`/`fadeOut` for PostView transitions, eliminating the jarring "ditch effect" from the edge-to-edge layout sliding against padded screens.
  9. Verified build with `.\gradlew assembleDebug` — BUILD SUCCESSFUL with zero errors.

---

- **Type of Details:** Feature Update / UI Polish
- **Description:** Immersive UI improvements on `PostViewScreen.kt`:
  1. **Disabled device top status bar** — Added status bar hiding using `WindowInsetsCompat.Type.statusBars()` within `DisposableEffect` so the device's top status bar is disabled completely on this screen, making it clean, and restored on exit.
  2. **TextureView-based VideoPlayer** — Replaced `VideoView` with `TextureView` and `MediaPlayer` inside `VideoPlayer` so that background videos are affected by Compose's backdrop blur modifier when the More Options bottom sheet is opened.
  3. **Back button padding** — Fine-tuned the floating back button's padding (top = 16.dp, start = 16.dp) to adapt cleanly to full-screen immersive view when the status bar is disabled.

---

- **Type of Details:** Feature Update / UI & UX Polish
- **Description:** Interactive features and display fixes on `PostViewScreen.kt`:
  1. **Delete Confirmation Dialog** — Introduced an `AlertDialog` to prompt for deletion confirmation before triggering post deletion.
  2. **Pinch-to-Zoom (Instagram Style)** — Integrated a custom pointer input gesture tracker using Compose `Animatable` states. Users can pinch to zoom images or videos up to 5x. Pager horizontal swiping is automatically locked during zoom and everything snaps back with a 200ms animation on release.
  3. **Video Aspect Ratio Fix** — Integrated `MediaMetadataRetriever` to fetch the video's exact width, height, and rotation orientation. Calculated the corrected aspect ratio dynamically for both the parent layout and the internal player to fully resolve video stretching.

  4. **Compilation Fix** — Added missing `kotlinx.coroutines.launch` import to resolve the unresolved reference compilation errors in the pinch-to-zoom gesture coroutine blocks.
  5. **Swipe-lock Bypass** — Prevented 1-finger swipes from being consumed when scale is 1f, allowing default page transitions of `HorizontalPager` to work normally unless pinching or actively zoomed in.
  6. **Video Auto-play & Previews** — Configured `VideoPlayer` to sync with the active carousel page, auto-playing only when active, and seeking to `1` millisecond when paused/inactive so that a preview frame renders on the `TextureView` immediately instead of showing a blank screen.

---

- **Type of Details:** Refactor & Performance Improvement
- **Description:**
  1. Enabled Coil disk caching (`CachePolicy.ENABLED`) for video and image thumbnails in both `HomeScreen.kt` and `HistoryScreen.kt`, eliminating CPU spikes and scroll lag.
  2. Replaced the custom leaky `LiveData.observeAsState` implementation in `MainAppScreen.kt` with the official Compose LiveData dependency (`runtime-livedata`) and updated dependencies in `build.gradle.kts` and `libs.versions.toml`.
  3. Optimized navigation and recomposition by pushing the posts database observation down from `MainAppScreen.kt` directly to `HomeScreen.kt`, `HistoryScreen.kt`, and `FavoritesScreen.kt`.
  4. Refactored favorites state tracking in `SettingsManager.kt` from a manual update counter to a reactive `StateFlow<Set<String>>` to prevent global recompositions.
  5. Solved UI freezing during pinch-to-zoom by wrapping SharedPreferences grid columns writes in `MainAppScreen.kt` in asynchronous `Dispatchers.IO` coroutine blocks.
  6. Optimized media fetching in `MediaFetcherRepository.kt` and `MediaResult.kt` to parse the raw JSON string into a top-level `InstagramResponse` data class wrapper in a single pass, eliminating double-parsing and redundant serializations.
  7. Verified the project compiles successfully using `.\gradlew.bat compileDebugKotlin` with zero errors.

---

- **Type of Details:** Refactor & Performance Improvement
- **Description:**
  1. Resolved scroll jank in `HistoryScreen` and `FavoritesScreen` by moving favorites tracking state flow entirely from `SettingsManager.kt` to `MainViewModel.kt` to avoid synchronous SharedPreferences reads inside Compose layout passes.
  2. Fixed UI freeze and ANR risks on post deletion by moving physical file deletion logic (`file.delete()`) from the UI layer to `MainViewModel.deleteDownloadedPost` running on `Dispatchers.IO`.
  3. Resolved MVVM architectural violation by adding `getPostById` to `MainViewModel.kt` and querying it in `MainAppScreen.kt`'s notification handler instead of instantiating `AppDatabase` directly from the UI.
  4. Fixed deep link loop on app resume by removing `"POST_URL"` and `"instagram_url"` intent extras immediately after media extraction starts inside `MainAppScreen.kt`.
  5. Verified the project compiles successfully using `.\gradlew.bat compileDebugKotlin` with zero errors.

---

- **Type of Details:** Refactor
- **Description:**
  1. Consolidated settings state architecture by migrating all variables, constants, properties, and helper functions from `SettingsManager.kt` into `SettingsViewModel.kt`.
  2. Deleted the redundant `SettingsManager.kt` file.
  3. Refactored `MainAppScreen.kt` and `SettingsScreen.kt` parameters and references to use `settingsViewModel: SettingsViewModel` instead of `settingsManager: SettingsManager`.
  4. Updated `MainActivity.kt`, `MainViewModel.kt`, `VedInstaApplication.kt`, and `SharedLinkProcessingService.kt` to use `SettingsViewModel` and reference its properties and methods consistently.
  5. Verified successful build and compilation via Gradle (`assembleDebug` and `compileDebugKotlin` SUCCESS).

---

- **Type of Details:** Refactor & New Update
- **Description:**
  1. Auto-dismissed the "Download Started" heads-up notification in `VedInstaNotificationManager.kt` after 3 seconds instead of leaving it in the status bar drawer.
  2. Implemented dynamic notification ID reusing (using `postId.hashCode()` or `notificationId`) in `DownloadService.kt` and `VedInstaApplication.kt` observers so progress notifications seamlessly morph into completed/error states at the exact same tray slot.
  3. Added in-app progress updates and cleanups in the local database (`updateProgressInDb` and `removeProgressFromDb`) during WorkManager single and batch download progress monitoring.
  4. Tagged shared batch download cards with `postId`, `postUrl`, and `thumbnailPath` during DB notification insertion inside `VedInstaApplication.kt` to support media preview rendering and click routing to `PostViewScreen`.
  5. Commented out intermediate `SYSTEM_INFO` ("Shared Link Processed") and `DOWNLOAD_STARTED` ("Shared Link Batch Download") database notifications in `SharedLinkProcessingService.kt` to prevent cluttering the in-app `NotificationScreen`.
  6. Verified the project compiles successfully using `.\gradlew.bat compileDebugKotlin` and `.\gradlew.bat assembleDebug` with zero errors.

---

- **Type of Details:** Performance Improvement & ANR Solving
- **Description:**
  1. Refactored `PostViewScreen.kt` to load media and video aspect ratios asynchronously using `withContext(Dispatchers.IO)` inside `LaunchedEffect` triggers, removing synchronous `MediaMetadataRetriever` and `BitmapFactory` calls from Compose layout/draw passes.
  2. Implemented asynchronous `MediaPlayer` release helper in `VideoPlayer` component inside `PostViewScreen.kt` using `CoroutineScope(Dispatchers.IO).launch` to prevent native player release calls from stalling the Choreographer frame (eliminating Davey rendering freezes).
  3. Converted `getImagePathLabel` and `getVideoPathLabel` in `SettingsViewModel.kt` to suspend functions offloaded to `Dispatchers.IO`.
  4. Refactored `SettingsScreen.kt` to load save folder name labels asynchronously inside a `LaunchedEffect` coroutine instead of executing synchronous `DocumentFile` queries on the main thread during composition.
  5. Moved application startup cache exists check inside `clearAppCache` in `VedInstaApplication.kt` entirely to background coroutines (`Dispatchers.IO`), removing all synchronous I/O operations from application cold start `onCreate()`.
  6. Verified the project compiles successfully using `.\gradlew.bat compileDebugKotlin` and `.\gradlew.bat assembleDebug` with zero errors.

---

- **Type of Details:** Refactor & UX/UI Improvement
- **Description:**
  1. Commented out database notification entry for `"Shared Link Auto-Download"` in `SharedLinkProcessingService.kt` to suppress duplicate cards in the in-app `NotificationsScreen` during single shared downloads.
  2. Implemented dynamic file type check in `DownloadService.kt` and `VedInstaApplication.kt` (resolving endsWith `.mp4`/`.mov`/`.avi`) to categorize single files into `"reel"` (for videos) or `"post"` (for images) inside success notifications.
  3. Replaced `"Saved {fileName} from @username"` with `"Saved reel/post from @username"` for all single downloads.
  4. Verified the project compiles successfully using `.\gradlew.bat compileDebugKotlin` and `.\gradlew.bat assembleDebug` with zero errors.

---
