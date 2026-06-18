package com.zephron.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import com.zephron.app.utils.LogStorage
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.zephron.app.R
import com.zephron.app.ui.AppLogo
import com.zephron.app.ui.theme.LocalAppColors
import com.zephron.app.ui.theme.darken
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Web client ID (client_type 3) from the main `oauth_client` block of
// google-services.json — this is the one Firebase/Credential Manager expects.
private const val WEB_CLIENT_ID = "199105728544-9ahkrorkci7ue69ge7b3lihib1hu9k32.apps.googleusercontent.com"

private val BACKGROUND_IMAGES = listOf(
    "https://images.unsplash.com/photo-1555939594-58d7cb561ad1?ixlib=rb-4.1.0&q=85&fm=jpg&crop=entropy&cs=srgb",
    "https://images.unsplash.com/photo-1482049016688-2d3e1b311543?ixlib=rb-4.1.0&q=85&fm=jpg&crop=entropy&cs=srgb",
    "https://images.unsplash.com/photo-1565958011703-44f9829ba187?ixlib=rb-4.1.0&q=85&fm=jpg&crop=entropy&cs=srgb"
)

@Composable
fun LoginScreen(
    onContinueAsGuest: () -> Unit,
    onGoogleSignIn: (name: String, email: String) -> Unit
) {
    val orange = LocalAppColors.current.accent
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    var imageIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10000)
            imageIndex = (imageIndex + 1) % BACKGROUND_IMAGES.size
        }
    }

    LaunchedEffect(Unit) { visible = true }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Background Slideshow ──────────────────────────────────────────────
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Crossfade(
                targetState = BACKGROUND_IMAGES[imageIndex],
                animationSpec = tween(1200),
                label = "background_crossfade"
            ) { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(5.dp)
                )
            }
        }

        // ── Overlays for depth and readability ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            orange.copy(alpha = 0.3f),
                            orange.darken(0.4f).copy(alpha = 0.7f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -40 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppLogo(modifier = Modifier.size(88.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Saffron",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.login_save_from_anywhere),
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(700, 200)) + slideInVertically(tween(700, 200)) { 60 }
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.login_get_started),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        // Google sign-in button
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    performGoogleSignIn(
                                        context = context,
                                        onSuccess = { name, email ->
                                            isLoading = false
                                            Toast.makeText(context, "Anmeldung erfolgreich!", Toast.LENGTH_SHORT).show()
                                            onGoogleSignIn(name, email)
                                        },
                                        onCancel = { isLoading = false },
                                        onError = { msg ->
                                            isLoading = false
                                            errorMessage = msg
                                            Toast.makeText(context, "Anmeldefehler: $msg", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1F1F1F),
                                disabledContainerColor = Color.White.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = orange,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_google),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.login_continue_google),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1F1F1F)
                                )
                            }
                        }

                        errorMessage?.let { msg ->
                            Text(
                                text = msg,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color.White.copy(alpha = 0.3f)
                            )
                            Text(
                                text = "  ${stringResource(R.string.login_or)}  ",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color.White.copy(alpha = 0.3f)
                            )
                        }

                        TextButton(
                            onClick = onContinueAsGuest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.login_continue_guest),
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun performGoogleSignIn(
    context: android.content.Context,
    onSuccess: (String, String) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit
) {
    LogStorage.logToFile(context, "Google Sign-In sequence started")
    try {
        val credentialManager = CredentialManager.create(context)
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()
        
        LogStorage.logToFile(context, "Requesting credentials from Credential Manager")
        val result = credentialManager.getCredential(context, request)
        val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
        
        LogStorage.logToFile(context, "Google Credential obtained for: ${googleCred.id}")
        android.util.Log.d("LoginScreen", "Google Credential obtained for: ${googleCred.id}")
        onSuccess(googleCred.displayName ?: "User", googleCred.id)
    } catch (e: GetCredentialCancellationException) {
        LogStorage.logToFile(context, "Google Sign-In cancelled by user")
        android.util.Log.d("LoginScreen", "Google Sign-In cancelled by user")
        onCancel()
    } catch (e: NoCredentialException) {
        LogStorage.logToFile(context, "No Google credentials found", e)
        android.util.Log.e("LoginScreen", "No Google credentials found", e)
        onError("No Accounts: ${e.type}")
    } catch (e: GetCredentialException) {
        LogStorage.logToFile(context, "Credential Manager failed: ${e.type}", e)
        android.util.Log.e("LoginScreen", "Credential Manager failed: ${e.type}", e)
        onError("Google Error: ${e.type}\n${e.message}")
    } catch (e: Exception) {
        LogStorage.logToFile(context, "Unexpected error during Google Sign-In", e)
        android.util.Log.e("LoginScreen", "Unexpected error during Google Sign-In", e)
        onError("Fehler: ${e.message ?: e.javaClass.simpleName}")
    }
}
