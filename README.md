# VedInsta

VedInsta is a premium, native Android media downloader designed for high-performance downloading from Instagram. The application is built entirely using Kotlin, Jetpack Compose, and Material Design 3, with a Python engine integrated via Chaquopy.

## Project Architecture

VedInsta strictly adheres to the Model-View-ViewModel (MVVM) architecture pattern:

- **UI Layer (`ui/`):** Built with Jetpack Compose for a fully declarative, fluid UI experience. Handled stateless components with state hoisting to keep composables lightweight.
- **ViewModel Layer (`viewmodel/`):** Manages UI states and exposes reactively updated StateFlows. Handles logic coordination without touching the view directly.
- **Repository Layer (`repository/`):** Acts as the single source of truth for the application. Resolves data fetching from local storage, Room databases, and network API endpoints.
- **Database Layer (`database/`):** Utilizes Room Database containing DAOs and entities (e.g., DownloadedPost, NotificationEntity) for local caching and tracking.
- **Service Layer (`service/`):** Manages lifecycle-sensitive background processing, including link handling and foreground downloading.
- **Notification Layer (`notification/`):** Coordinates native progress updates, system alerts, and notification badge states.

## Key Features

- **Frosted Glass App Bars:** Implements true background blurs on both the Top App Bar and Bottom Navigation Bar using the Haze library, providing a high-performance glassmorphism effect.
- **Dynamic Styling Sliders:** Allows users to adjust the blur tint opacity and blur intensity dynamically in settings.
- **AMOLED Dark Mode:** Features a true-black theme option to save battery and look premium on AMOLED displays.
- **Edge-to-Edge Experience:** Features transparent system navigation buttons so that scrolling lists flow seamlessly underneath the app bars.
- **WhatsApp Status Saver:** Preserves WhatsApp status media in a dedicated tab with an automated 7-day cleanup worker (WAPreserver) to auto-purge stale files.
- **Expressive Home Carousel:** Displays recent downloads in a compact Material 3 Horizontal Uncontained Carousel.

## Security & Data Handling

VedInsta prioritizes user privacy and data security through industry-standard practices:

### 1. Secure Cookie Management
- **Interception:** Authentication cookies (`sessionid`, `csrftoken`, `ds_user_id`) are securely intercepted via a custom WebViewClient during login.
- **Encryption:** Sensitive session tokens are never stored in plain text. They are written to EncryptedSharedPreferences (`androidx.security.crypto`), isolating them from unauthorized reading by other apps.
- **Netscape Cookie Output:** The Python extraction script requires cookies in the Netscape format. These are generated on the fly and kept in the application's isolated internal storage (`filesDir`), protecting them from external reads.

### 2. Media Handling and MediaStore
- **Physical Downloads:** Handled via resilient services that support background downloading for large video files without connection timeout.
- **MediaStore Indexing:** Immediately indexes downloaded images and videos into the Android MediaStore. This ensures files are visible in native gallery apps as soon as they are finished, avoiding plain-text exposure or hidden directories.
- **Permissions:** Requests permission scope dynamically, avoiding wider storage access requirements on newer Android versions.

### 3. Lifecycle Security
- **Service Termination:** Services are configured to stop immediately once their queued coroutine processes complete (preventing battery drain and process leaks).
- **WorkManager Purging:** Automatic cleanup tasks execute inside WorkManager to purge temporary cache files and old statuses securely.

## Working Process

1. **Link Share:** The user shares an Instagram post URL to the application.
2. **Intent Processing:** The app captures the URL via a background service.
3. **Extraction:** The Python engine reads the cookies from secure storage and extracts media sources.
4. **Interactive UI:** The user is either prompted with a carousel selector (for multi-media posts) or the download is automatically queued.
5. **Foreground Download:** The files download through a foreground service, updating progress in real-time.
6. **MediaStore Update:** Once complete, the database registers the post, indexes it, and triggers a completion notification with retry-on-failure actions.
