package com.sky.wallapp

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Google sign-in and sync of saved data with /users/{uid}/saved, behaving exactly like the
 * website's `savedSync.js`:
 * - on sign-in, device and account data are merged once (nothing is lost);
 * - then local changes are written (debounced) and other devices' changes replace local;
 * - on sign-out, syncing stops first (flushing a pending write), then the device copy is cleared.
 */
object SavedSync {

    enum class Status { IDLE, SYNCING, SYNCED, ERROR }

    /** [known] is false until Firebase Auth has reported the initial user. */
    data class Account(val user: FirebaseUser? = null, val known: Boolean = false, val status: Status = Status.IDLE)

    private const val TAG = "SavedSync"
    private const val WRITE_DELAY_MS = 800L

    private val _account = MutableStateFlow(Account())
    val account: StateFlow<Account> = _account

    private val handler = Handler(Looper.getMainLooper())
    private var syncingUid: String? = null
    private var stopCurrent: (() -> Unit)? = null
    private var initialized = false

    private fun setStatus(status: Status) {
        _account.value = _account.value.copy(status = status)
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        SavedRepository.init(context)
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            _account.value = Account(user, known = true, status = if (user != null) _account.value.status else Status.IDLE)
            if (user != null) start(user.uid) else stop()
        }
    }

    private fun start(uid: String) {
        if (syncingUid == uid) return
        stop()
        syncingUid = uid
        val ref = FirebaseDatabase.getInstance().getReference("users").child(uid).child("saved")
        var ready = false
        var lastJson: String? = null
        var pending: Runnable? = null

        fun write(state: SavedStore.State) {
            val json = SavedStore.toJson(state)
            if (json == lastJson) return
            lastJson = json
            setStatus(Status.SYNCING)
            ref.setValue(mapOf("json" to json, "updatedAt" to ServerValue.TIMESTAMP))
                .addOnSuccessListener { setStatus(Status.SYNCED) }
                .addOnFailureListener {
                    Log.w(TAG, "Sync write failed: ${it.message}")
                    setStatus(Status.ERROR)
                }
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val remote = snapshot.child("json").value as? String
                if (!ready) {
                    ready = true
                    val local = SavedRepository.state.value
                    val merged = if (remote != null) SavedStore.mergeStates(local, SavedStore.parse(remote)) else local
                    SavedRepository.replace(merged)
                    lastJson = remote
                    write(merged)
                    if (_account.value.status != Status.SYNCING) setStatus(Status.SYNCED)
                    return
                }
                if (remote != null && remote != lastJson) { // changed on another device
                    lastJson = remote
                    SavedRepository.replace(SavedStore.parse(remote))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Sync listener failed: ${error.message}")
                setStatus(Status.ERROR)
            }
        }
        ref.addValueEventListener(listener)

        SavedRepository.onLocalChange = {
            if (ready) {
                pending?.let(handler::removeCallbacks)
                pending = Runnable {
                    pending = null
                    write(SavedRepository.state.value)
                }.also { handler.postDelayed(it, WRITE_DELAY_MS) }
            }
        }

        setStatus(Status.SYNCING)
        stopCurrent = {
            // Flush a pending local change before stopping
            pending?.let {
                handler.removeCallbacks(it)
                if (ready) write(SavedRepository.state.value)
            }
            ref.removeEventListener(listener)
            SavedRepository.onLocalChange = null
        }
    }

    private fun stop() {
        stopCurrent?.invoke()
        stopCurrent = null
        syncingUid = null
    }

    /** Shows the Google account picker and signs in; returns an error message or null. */
    suspend fun signInWithGoogle(activity: Activity): String? {
        return try {
            val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential

            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return activity.getString(R.string.sign_in_failed)
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            AnalyticsTracker(FirebaseAnalytics.getInstance(activity)).logEvent("login", mapOf("method" to "Google"))
            null
        } catch (e: GetCredentialCancellationException) {
            // Also what Google returns when the app's SHA fingerprint isn't registered in Firebase
            Log.w(TAG, "Sign-in cancelled: ${e.type} ${e.message}")
            null
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No credential: ${e.message}")
            activity.getString(R.string.sign_in_no_account)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Sign-in failed: ${e.type} ${e.message}")
            activity.getString(R.string.sign_in_failed)
        } catch (e: Exception) {
            Log.w(TAG, "Sign-in failed: ${e.message}")
            activity.getString(R.string.sign_in_failed)
        }
    }

    /** Signs out and clears this device's copy (it's safe in the account). */
    suspend fun signOut(context: Context) {
        stop()
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        SavedRepository.replace(SavedStore.emptyState())
    }
}
