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

---

