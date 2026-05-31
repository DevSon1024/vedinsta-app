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
  ***
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

  ***

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
- **End Marker:** ---
