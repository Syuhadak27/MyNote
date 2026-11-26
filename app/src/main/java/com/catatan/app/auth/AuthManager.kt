package com.catatan.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.catatan.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/* ============================================================
   AuthManager — Google Sign-In + Firebase Auth.
   Tidak pernah dipakai sebagai "gate": aplikasi tetap bisa dipakai
   tanpa akun (mode lokal).
   ============================================================ */

class AuthManager(context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val prefs = context.applicationContext
        .getSharedPreferences("catatan-auth", Context.MODE_PRIVATE)

    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    val currentUid: String? get() = _user.value?.uid
    val isSignedIn: Boolean get() = _user.value != null

    /**
     * uid yang sedang menunggu keputusan user (gabungkan data lokal atau tidak).
     * Disimpan agar dialog muncul lagi kalau app ditutup saat dialog terbuka.
     */
    var pendingUid: String?
        get() = prefs.getString("pending_uid", null)
        set(value) {
            if (value == null) prefs.edit().remove("pending_uid").apply()
            else prefs.edit().putString("pending_uid", value).apply()
        }

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _user.value = firebaseAuth.currentUser
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    /** Buka pemilih akun Google, lalu tukar ID token menjadi kredensial Firebase. */
    suspend fun signIn(activity: Context) {
        withContext(Dispatchers.Main.immediate) {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()

            val result = CredentialManager.create(activity).getCredential(activity, request)
            val credential = result.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                error("Kredensial Google tidak dikenali")
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val signedIn = auth.signInWithCredential(firebaseCredential).await()
            signedIn.user ?: error("Gagal mendapatkan akun Google")
        }
    }

    fun signOut() {
        pendingUid = null
        auth.signOut()
    }
}
