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
