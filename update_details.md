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
