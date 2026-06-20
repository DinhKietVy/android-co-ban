package com.example.filemanagementapp.login.google

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.auth.model.AuthProvider
import com.example.filemanagementapp.data.auth.model.LoginUser
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class GoogleAuthUiClient(
    private val context: Context,
    private val credentialManager: CredentialManager = CredentialManager.create(context),
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    suspend fun signIn(): Result<LoginUser> {
        val serverClientId = resolveServerClientId()
            ?: return Result.failure(
                IllegalStateException("Google Sign-In chưa được cấu hình. Thiếu OAuth web client id trong Firebase.")
            )

        return runCatching {
            var result: GetCredentialResponse? = null
            try {
                result = credentialManager.getCredential(
                    context = context,
                    request = buildRequest(serverClientId)
                )
            } catch (e: NoCredentialException) {
                // Known issue: first invocation might fail if state is unclear. Retry once.
                result = credentialManager.getCredential(
                    context = context,
                    request = buildRequest(serverClientId)
                )
            }
            
            val googleIdTokenCredential = extractGoogleIdTokenCredential(result!!)
            val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = firebaseAuth.signInWithCredential(authCredential).await()
            val firebaseUser = authResult.user
                ?: throw IllegalStateException("Firebase không trả về thông tin người dùng")

            LoginUser(
                id = firebaseUser.uid,
                username = firebaseUser.displayName ?: firebaseUser.email ?: "google_user",
                displayName = firebaseUser.displayName,
                email = firebaseUser.email,
                provider = AuthProvider.GOOGLE,
                avatarUrl = firebaseUser.photoUrl?.toString()
            )
        }.recoverCatching { throwable ->
            throw Exception(toReadableError(throwable))
        }
    }

    private fun resolveServerClientId(): String? {
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resourceId == 0) return null
        return context.getString(resourceId).takeIf { it.isNotBlank() }
    }

    private fun buildRequest(serverClientId: String): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()

        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    private fun extractGoogleIdTokenCredential(result: GetCredentialResponse): GoogleIdTokenCredential {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                return GoogleIdTokenCredential.createFrom(credential.data)
            } catch (exception: GoogleIdTokenParsingException) {
                throw IllegalStateException(
                    context.getString(R.string.error_auth_google_sign_in_failed),
                    exception
                )
            }
        }

        throw IllegalStateException(context.getString(R.string.error_auth_google_sign_in_failed))
    }

    private fun toReadableError(throwable: Throwable): String {
        return throwable.message ?: context.getString(R.string.error_auth_google_sign_in_failed)
    }
}
