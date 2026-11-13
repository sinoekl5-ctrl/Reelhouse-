package com.reelhouse.app.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Fingerprint lock using Android's official BiometricPrompt, with the phone PIN / pattern as the fallback.
 */
object AppLock {
    /** Set before opening the player so that watching a movie does not trigger the lock afterwards. */
    @Volatile
    var skipNextRelock = false

    private fun keyguard(ctx: Context) = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    /** True if the phone has a fingerprint or a screen lock that can be used to unlock the app. */
    fun available(ctx: Context): Boolean {
        if (keyguard(ctx).isDeviceSecure) return true
        return BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Intent for the phone's own PIN / pattern / password screen, or null if none is set. */
    fun credentialIntent(ctx: Context): Intent? =
        keyguard(ctx).createConfirmDeviceCredentialIntent("Unlock Reelhouse", null)

    /**
     * Shows the fingerprint prompt. [onFallback] is called when the user taps "Use phone PIN"
     * (or when fingerprint is not usable) and the caller should show the phone credential screen.
     */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFallback: () -> Unit
    ) {
        val canBio = BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        if (!canBio && Build.VERSION.SDK_INT < 30) {
            // No usable fingerprint on this Android version: use the phone PIN directly.
            if (credentialIntent(activity) == null) onSuccess() else onFallback()
            return
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_NO_BIOMETRICS -> onFallback()
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> onSuccess() // lock was removed from the phone
                    else -> Unit // user cancelled: stay on the lock screen
                }
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Reelhouse")
            .setSubtitle("Confirm it's you")
        if (Build.VERSION.SDK_INT >= 30) {
            info.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        } else {
            info.setAllowedAuthenticators(BIOMETRIC_WEAK)
            info.setNegativeButtonText("Use phone PIN")
        }
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info.build())
    }
}
