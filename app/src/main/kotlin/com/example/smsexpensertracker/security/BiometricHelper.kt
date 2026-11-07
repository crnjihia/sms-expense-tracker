// path: app/src/main/kotlin/com/example/smsexpensertracker/security/BiometricHelper.kt
package com.example.smsexpensertracker.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Biometric capability status on the host device.
 */
enum class BiometricStatus {
    AVAILABLE,
    NOT_ENROLLED,
    NO_HARDWARE,
    UNAVAILABLE
}

/**
 * Result of a biometric authentication attempt.
 */
sealed interface BiometricPromptResult {
    data object Success : BiometricPromptResult
    data class Failure(val errorCode: Int, val errString: CharSequence) : BiometricPromptResult
    data object Cancelled : BiometricPromptResult
}

/**
 * Manages biometric authentication state, preferences, and prompt presentation.
 */
@Singleton
class BiometricHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "security_prefs"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_lock_enabled"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var isSessionAuthenticated: Boolean = false

    /**
     * Checks whether biometric hardware and enrollment are ready on this device.
     */
    fun checkBiometricStatus(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        if (!enabled) {
            isSessionAuthenticated = true
        }
    }

    fun isAppUnlocked(): Boolean {
        if (!isBiometricEnabled()) return true
        return isSessionAuthenticated
    }

    fun markSessionAuthenticated(authenticated: Boolean) {
        isSessionAuthenticated = authenticated
    }

    /**
     * Displays the system BiometricPrompt dialog.
     */
    fun promptAuthentication(
        activity: FragmentActivity,
        title: String = "Unlock SMS Expense Tracker",
        subtitle: String = "Authenticate to access your Kenyan financial records",
        onResult: (BiometricPromptResult) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isSessionAuthenticated = true
                    onResult(BiometricPromptResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onResult(BiometricPromptResult.Cancelled)
                    } else {
                        onResult(BiometricPromptResult.Failure(errorCode, errString))
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Intermediate failure (e.g. fingerprint unrecognised); prompt remains open
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}

