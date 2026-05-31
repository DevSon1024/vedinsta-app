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
- **End Marker:** ---

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
- **End Marker:** ---

- **Type of Details:** Error Solving & Refactor
- **Description:** 
  1. Resolved the `SystemError: frame does not exist` exception during post details extraction by refactoring [MediaFetcherRepository.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/repository/MediaFetcherRepository.kt) to invoke the Python method `mo3.get_media_urls` directly from Kotlin and parse the JSON string response via Gson.
  2. Implemented profile username fetching by introducing `get_logged_in_username(cookie_file)` in [mo3.py](file:///c:/Android/vedinsta/app/src/main/python/mo3.py) to query Instagram's user info API using authenticated session cookies.
  3. Added secure caching for `ig_username` in [SecurePreferences.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/repository/SecurePreferences.kt).
  4. Updated `InstagramAuthState` and [InstagramAuthViewModel.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/viewmodel/InstagramAuthViewModel.kt) to retrieve, cache, and publish the alphanumeric username in the background.
  5. Refactored the Session Status card in [MediaSelectionScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/MediaSelectionScreen.kt) and active session header in [SessionsScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/SessionsScreen.kt) to show the alphanumeric username rather than the numeric `dsUserId`.
  6. Verified the project compiles successfully using `.\gradlew assembleDebug`.
- **End Marker:** ---

- **Type of Details:** Error Solving & Refactor
- **Description:** 
  1. Resolved concurrent downloads not starting by implementing an active download task counter (`AtomicInteger`) in [DownloadService.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/service/DownloadService.kt). This prevents premature service termination or foreground status removal while downloads are still running.
  2. Added dynamic SDK-level checks and passed `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` on Android 10+ (API 29+) to ensure compatibility with Android 13/14+ foreground service requirements.
  3. Integrated MediaStore indexing in [DownloadService.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/service/DownloadService.kt) using `MediaScannerConnection.scanFile` immediately upon download completion, ensuring downloaded files appear in the device gallery/photo library immediately.
  4. Redesigned [MediaSelectionScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/MediaSelectionScreen.kt) to make the entire layout scrollable using a single `LazyVerticalGrid` and `GridItemSpan` (success state) and a scrollable `Column` (other states), resolving viewport constraints and layout overflows.
  5. Enhanced the quality selection dropdown in [MediaSelectionScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/MediaSelectionScreen.kt) to display `"Original/High"` for the highest resolution quality.
  6. Verified the project compiles successfully using `.\gradlew.bat assembleDebug`.
- **End Marker:** ---

- **Type of Details:** New Update & Refactor
- **Description:** 
  1. Implemented in-app Snackbar notification support in [MediaSelectionScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/MediaSelectionScreen.kt) using a Compose `SnackbarHostState` inside the screen Scaffold.
  2. Added a reactive `LaunchedEffect` that automatically displays `"Media extracted"` when post details load successfully into the UI cards.
  3. Configured the download button's click handler to post a snackbar message specifying either `"Started Reels Downloading"` (if the download includes reels or videos) or `"{count} Images Download Started"` as the download queues.
  4. Resolved build issues by importing `kotlinx.coroutines.launch` in [MediaSelectionScreen.kt](file:///c:/Android/vedinsta/app/src/main/java/com/devson/vedinsta/ui/MediaSelectionScreen.kt).
  5. Verified the project compiles successfully using `.\gradlew.bat assembleDebug`.
- **End Marker:** ---
