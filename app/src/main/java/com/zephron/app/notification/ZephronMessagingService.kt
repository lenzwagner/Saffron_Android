package com.zephron.app.notification

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM pushes (friend requests, accept/decline, matches) sent by the
 * Cloud Function so notifications arrive even when the app is closed.
 *
 * The Cloud Function sends a "notification" payload, so:
 * - App in background / killed → the system displays it automatically.
 * - App in foreground → the in-app Firestore listener already shows it, so we
 *   intentionally do nothing here to avoid duplicate notifications.
 *
 * The main job of this service is keeping the device's FCM token up to date.
 */
class ZephronMessagingService : FirebaseMessagingService() {

    private val tag = "ZephronMessaging"

    override fun onNewToken(token: String) {
        Log.d(tag, "New FCM token")
        uploadToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Foreground delivery only. Handled by the in-app listener → no-op.
    }

    private fun uploadToken(token: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection("users").document(uid)
            .collection("fcmTokens").document(token)
            .set(
                mapOf(
                    "token" to token,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { e -> Log.e(tag, "Failed to upload FCM token", e) }
    }
}
