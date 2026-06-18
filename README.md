# Saffron Android (Kotlin/Compose)

Android app built with Jetpack Compose. Uses the same Firebase project (`saffron-498311`) as the iOS app — fully data-compatible with identical Firestore paths, field names, and Cloud Functions.

## Requirements
- **Android Studio Hedgehog** or newer
- JDK 17+
- Android SDK 34

## Setup
1. Download **`google-services.json`** from the Firebase console (project `saffron-498311` → Android app, package `com.zephron.app`) and place it at `app/google-services.json`.
2. Add your API keys to `local.properties`:
   ```
   GEMINI_API_KEY=your_key_here
   PEXELS_API_KEY=your_key_here
   BACKEND_URL=https://saffron-backend-zxqb.onrender.com
   ```
3. Open the project in Android Studio and run on a device or emulator.

## Architecture
| Layer | Technology |
|---|---|
| UI | Jetpack Compose |
| State | ViewModel + StateFlow |
| Local DB | Room |
| Remote | Firebase Firestore + Storage + FCM |
| AI | Gemini API |

Firestore paths are **identical** to the iOS app:
`users/{uid}/recipes`, `/craveSwipes`, `/inbox`, `/fcmTokens`, `mealPlans/{pairId}/entries`.
Recipe document ID = Base64-URL of the recipe URL.

## Releases
APKs are published as [GitHub Releases](../../releases). The app checks for updates automatically on launch.
