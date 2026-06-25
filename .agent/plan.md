# Project Plan

Notif Mirror: A phone to wear os companion that sends phone notification and media status with icons to wear os from bluetooth without phone being the paired phone to the wear.

## Project Brief

# Project Brief: NotifMirror

NotifMirror is a specialized companion application designed to bridge the gap between an Android smartphone and a Wear OS device. It enables a secondary phone to transmit notifications and media status directly to a watch via Bluetooth, even if that phone is not the primary device paired with the wearable.

## Features

*   **Interactive Notification Sync**: Mirrors system notifications with app icons and supports two-way synchronization, allowing users to dismiss alerts or respond to messages (e.g., chat-style replies) directly from the watch.
*   **Media Status Bridging**: Real-time transmission of media metadata and playback controls (Play/Pause/Skip) to the wearable via an independent Bluetooth link.
*   **Connectivity & Reconnect Management**: Robust connection handling with user-configurable settings for auto-reconnect timeouts and link stability.
*   **Resilient Notification Caching**: A synchronization layer that caches notifications for offline storage, ensuring data consistency and delivery once a connection is re-established.

## High-Level Tech Stack

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose with Material 3
*   **Navigation**: Jetpack Navigation 3 (State-driven architecture)
*   **Adaptive Strategy**: Compose Material Adaptive library for responsive layouts across device types.
*   **Concurrency**: Kotlin Coroutines and Flow for reactive data handling and asynchronous communication.
*   **Persistence**: Room Database (specifically for the notification caching and sync engine).
*   **Connectivity**: Play Services Wearable API and Bluetooth RFCOMM for device-to-device communication.
*   **System Integration**: `NotificationListenerService` for alert interception and `MediaSessionManager` for playback tracking.

## Implementation Steps
**Total Duration:** 10m 46s

### Task_1_CoreServices: Implement phone-side NotificationListenerService to intercept system notifications and MediaSessionManager to monitor active media sessions.
- **Status:** COMPLETED
- **Updates:** Implemented NotifMirrorService (NotificationListenerService) and MediaSessionManager integration. Created MirrorRepository to expose data via Flow. Updated Manifest with permissions. Ready for cross-device communication.
- **Acceptance Criteria:**
  - NotificationListenerService is correctly declared in Manifest with required permissions
  - Media session metadata and playback states are successfully captured
  - Captured data is exposed via a repository or stream (Flow)
- **Duration:** 10m 46s

### Task_2_SyncAndCommLayer: Establish the synchronization and communication layer, including Room database for notification caching and the two-way sync engine for dismissal/replies.
- **Status:** COMPLETED
- **Acceptance Criteria:**
  - Room database correctly caches notifications for offline storage
  - Two-way synchronization for notification dismissal and replies is implemented via Wearable Data Layer/RFCOMM
  - Reconnection and sync logic handles link stability as per project brief
  - Communication bridge establishes direct link to Wear OS nodes
- **StartTime:** 2026-05-20 19:39:18 TRT

### Task_3_PhoneAppUI: Build the phone application's UI using Jetpack Compose, Material 3, and Navigation 3, including adaptive layouts and reconnection settings.
- **Status:** COMPLETED
- **Acceptance Criteria:**
  - UI follows Material Design 3 with adaptive layouts
  - Navigation 3 handles state-driven routing
  - User-configurable settings for reconnection timeouts are implemented
  - Full Edge-to-Edge display is enabled

### Task_4_WearOSAppUI: Develop the Wear OS application's UI to display mirrored notifications and provide interactive media and notification controls.
- **Status:** COMPLETED
- **Acceptance Criteria:**
  - Notifications are displayed with icons and text
  - Interactive replies and dismissal from the watch are functional
  - Media controls successfully trigger actions on the phone
  - UI is optimized for Wear OS screen shapes

### Task_5_FinalRunAndVerify: Finalize the app with an adaptive icon, Material 3 theme refinement, and perform a full system verification and stability check.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Adaptive app icon and Material 3 color scheme are implemented
  - Build passes, app does not crash, and all features align with the brief
  - Critic_agent confirms stability and UI fidelity

