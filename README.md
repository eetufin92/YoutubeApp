# YoutubeApp

A modern, feature-rich YouTube client for Android built with Jetpack Compose. This app leverages the power of WebView with custom script injections to provide an enhanced YouTube experience, including SponsorBlock integration and native Android features like Picture-in-Picture.

## Features

-   **Jetpack Compose UI**: Built entirely with modern Android UI toolkit.
-   **SponsorBlock Integration**: Automatically skip sponsorships, intros, outros, and other annoying segments.
-   **Picture-in-Picture (PiP)**: Continue watching your videos while using other apps.
-   **Adaptive Layout**: Supports different screen sizes and orientations using Material 3 Adaptive components.
-   **Enhanced WebView Player**:
    -   Fullscreen support with auto-orientation.
    -   Custom script injection for feature enhancement.
    -   JavaScript bridge for seamless communication between the web player and Android.
-   **Modern Navigation**: Powered by Navigation 3 for a robust and flexible navigation experience.
-   **Deep Link Support**: Handles YouTube URLs shared from other apps.
-   **Settings Customization**:
    -   Configure SponsorBlock skip categories.
    -   Custom User Agent string for the internal browser.

## Tech Stack

-   **Language**: [Kotlin](https://kotlinlang.org/)
-   **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
-   **Design System**: [Material 3](https://m3.material.io/)
-   **Navigation**: [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)
-   **Local Storage**: [Room](https://developer.android.com/training/data-storage/room)
-   **Preferences**: [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
-   **Network**: [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
-   **JSON Parsing**: [Moshi](https://github.com/square/moshi)

## Getting Started

### Prerequisites

-   Android Studio Jellyfish or newer.
-   Android SDK 26 (Android 8.0) or higher.

### Building

1.  Clone the repository:
    ```bash
    git clone https://github.com/yourusername/YoutubeApp.git
    ```
2.  Open the project in Android Studio.
3.  Sync the project with Gradle files.
4.  Run the `app` module on your device or emulator.

## Project Structure

-   `ui/`: Contains all Compose-based UI components, screens, and themes.
-   `data/`: Handles data management, including SponsorBlock logic and local storage.
-   `bridge/`: Contains the JavaScript bridge for WebView communication.
-   `navigation/`: Defines the app's navigation structure and destinations.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
