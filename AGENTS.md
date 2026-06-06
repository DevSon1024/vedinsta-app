# AI Agent Instructions for VedInsta Development

This document serves as the absolute source of truth for any AI agent or LLM assisting with the development of the **VedInsta** project. You must strictly adhere to these rules, architectural guidelines, and development philosophies before generating or modifying any code.

## 1. Core Development Philosophy

- **Goal:** VedInsta is a native Android Instagram media downloader built with Kotlin, Jetpack Compose, and a Python backend (via Chaquopy).
- **Focus:** Prioritize a clean, lag-free, and smooth native user experience.
- **No Hallucinations:** Only use existing APIs, classes, and resources within the project. If you are unsure about an existing implementation, ask the user to fetch the file contents.

## 2. UI / Jetpack Compose Guidelines

- **Framework:** Jetpack Compose is the primary UI framework. Avoid legacy XML layouts entirely for UI screens (XML is only permitted for drawables, vector assets, and basic values).
- **Material Design:** Strictly utilize Material Design 3 (M3) components and styling (`androidx.compose.material3.*`) to ensure a clean, native, and smooth experience.
- **Composables:** Keep composable functions highly focused and modular. Extract reusable UI elements into the appropriate `ui/` or `components/` packages.
- **State Hoisting:** Prefer state hoisting for UI components to keep them stateless and reusable where appropriate. Do not perform heavy O(N) calculations, I/O, or file operations directly within composable functions.

## 3. Code Quality & Performance Optimization

- **Language:** Kotlin is the exclusive programming language.
- **Asynchronous Operations:** Use Kotlin Coroutines and Flows (`StateFlow`/`SharedFlow`) for all asynchronous programming and state observation. Avoid RxJava or traditional callback interfaces.
- **Null Safety:** Handle nullable types safely. **Never** use the not-null assertion operator (`!!`) unless absolutely and undeniably necessary (and accompanied by an explanatory inline comment).
- **Performance:** Prioritize lag-free, 60/120fps performance. Avoid memory thrashing by efficiently caching instances (like formatters) and using optimized data loading strategies (like Paging 3) for large media collections.
- **File & I/O Operations:** Always dispatch database (Room), network, or file writing operations to `Dispatchers.IO`.

## 4. Documentation & Update Tracking

You must actively maintain the project's changelog. After every completed task, error resolution, or feature addition, you must append an entry to the `update_details.md` file.

**Format and Rules for `update_details.md`:**

- Do NOT read or rewrite the whole file every time. Simply append the new data at the very end of the document.
- Include a Date and Time stamp for the update.
  Whenever a fix, optimization, or feature is completed, you MUST document it using the following format:

- **Issue:** (Briefly describe the exact issue or bottleneck that was just solved)
- **Type:** (Specify the category: e.g., Error, Bug, UI, Performance, Architecture, Feature)
- **Solution:** (Explain how the issue was solved. Maximum 10 lines.)
- After the details of the latest update, you must append exactly `---` on a new line to close out that specific session.
- Do not include any conversational filler in the file.

## 5. Version Control (Git) Protocol

- **Do not commit or push** any changes to the repository until explicitly being asked to do so by the developer.

## 6. Project Architecture & Structure

Adhere strictly to the **MVVM (Model-View-ViewModel)** architecture pattern and the existing directory structure:

- **`com.devson.vedinsta.ui`**: Contains all UI components, Fragments, Activities, Dialogs, and Compose Themes. Keep logic out of these files.
- **`com.devson.vedinsta.viewmodel`**: Contains `ViewModel` classes. ViewModels should expose StateFlows to the UI and delegate heavy lifting to repositories.
- **`com.devson.vedinsta.repository`**: The single source of truth for data. Repositories handle fetching from the Python backend, network, or Room Database.
- **`com.devson.vedinsta.database`**: Contains Room Database classes, Entities (`DownloadedPost`, `NotificationEntity`), and DAOs.
- **`com.devson.vedinsta.service`**: Contains Android Services (e.g., `DownloadService`, `SharedLinkProcessingService`). Background operations that must survive app closure belong here.
- **`com.devson.vedinsta.adapters`**: Contains RecyclerView/Compose adapters where applicable (e.g., `MediaCarouselAdapter`, `SessionAdapter`).
- **`com.devson.vedinsta.notification`**: Contains notification managers and initializers.

## 7. Specific Domain Rules (VedInsta Logic)

- **Cookie Management:**
  - Instagram authentication cookies (`sessionid`, `csrftoken`, `ds_user_id`) must be intercepted via `WebViewClient`.
  - Store cookies securely using `EncryptedSharedPreferences` (`androidx.security.crypto`).
  - Output cookies for the Python script strictly in the Netscape cookie file format at `filesDir`.
- **Media Downloading:**
  - All physical file downloading must be handled by robust, resilient methods (e.g., `EnhancedDownloadManager` or Foreground Services) to handle large video files without timing out.
  - Media should be properly indexed into the Android `MediaStore` so it appears in native gallery applications immediately upon download.

## 8. Dependency Management

- **`libs.versions.toml`:** All dependency versions, libraries, and plugins are strictly managed in the version catalog. If an agent adds a new library, it MUST add it to `gradle/libs.versions.toml` and reference it appropriately in `build.gradle.kts` files. Do not hardcode version numbers inside `.gradle.kts` files.

---

**By reading this file, you agree to format your output and structure your code according to these project-specific guardrails.**
