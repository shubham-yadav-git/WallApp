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
import com.google.android.gms.tasks.Task
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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

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
    private const val FLUSH_TIMEOUT_MS = 5_000L

    private val _account = MutableStateFlow(Account())
    val account: StateFlow<Account> = _account

    private val handler = Handler(Looper.getMainLooper())
    private var syncingUid: String? = null
    /** Stops the current sync; returns the flushed write, if a local change was still pending. */
    private var stopCurrent: (() -> Task<Void>?)? = null
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

        fun write(state: SavedStore.State): Task<Void>? {
            val json = SavedStore.toJson(state)
            if (json == lastJson) return null
            lastJson = json
            setStatus(Status.SYNCING)
            return ref.setValue(mapOf("json" to json, "updatedAt" to ServerValue.TIMESTAMP))
                .addOnSuccessListener { setStatus(Status.SYNCED) }
                .addOnFailureListener {
                    Log.w(TAG, "Sync write failed: ${it.message}")
                    // Forget it was sent, so the next change (or flush) tries again
                    if (lastJson == json) lastJson = null
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
                    val incoming = SavedStore.parse(remote)
                    // A local change still waiting to be written would be lost by a plain replace:
                    // merge instead, and the pending write then sends the combined state
                    SavedRepository.replace(
                        if (pending != null) SavedStore.mergeStates(SavedRepository.state.value, incoming) else incoming
                    )
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
            val flush = pending?.let {
                handler.removeCallbacks(it)
                pending = null
                if (ready) write(SavedRepository.state.value) else null
            }
            ref.removeEventListener(listener)
            SavedRepository.onLocalChange = null
            flush
        }
    }

    private fun stop(): Task<Void>? {
        val flush = stopCurrent?.invoke()
        stopCurrent = null
        syncingUid = null
        return flush
    }

    /** Outcome of [signInWithGoogle]; [Failed.detail] is the raw error, shown behind "Details". */
    sealed interface SignInResult {
        data class Success(val name: String?) : SignInResult
        data class Failed(val message: String, val detail: String?) : SignInResult
    }

    /** Shows the Google account picker and signs in. */
    suspend fun signInWithGoogle(activity: Activity): SignInResult {
        fun failed(messageRes: Int, reason: String, detail: String?): SignInResult {
            Log.w(TAG, "Sign-in failed ($reason): $detail")
            AnalyticsTracker(FirebaseAnalytics.getInstance(activity))
                .logEvent("login_failed", mapOf("reason" to reason, "detail" to detail?.take(100)))
            return SignInResult.Failed(activity.getString(messageRes), detail)
        }
        return try {
            val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential

            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return failed(R.string.sign_in_failed, "credential_type", credential.type)
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val user = FirebaseAuth.getInstance()
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
            AnalyticsTracker(FirebaseAnalytics.getInstance(activity)).logEvent("login", mapOf("method" to "Google"))
            SignInResult.Success(user?.displayName ?: user?.email)
        } catch (e: GetCredentialCancellationException) {
            // Also what Google returns when the app's SHA fingerprint isn't registered in Firebase
            failed(R.string.sign_in_cancelled, "cancelled", "${e.type}: ${e.message}")
        } catch (e: NoCredentialException) {
            failed(R.string.sign_in_no_account, "no_credential", "${e.type}: ${e.message}")
        } catch (e: GetCredentialException) {
            failed(R.string.sign_in_failed, "credential", "${e.type}: ${e.message}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(R.string.sign_in_failed, "firebase", "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Signs out and clears this device's copy (it's safe in the account). */
    suspend fun signOut(context: Context) {
        // Let the flushed write land while still signed in; the rules reject it afterwards
        stop()?.let { flush ->
            runCatching { withTimeoutOrNull(FLUSH_TIMEOUT_MS) { flush.await() } }
                .onFailure { if (it is CancellationException) throw it }
        }
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        SavedRepository.replace(SavedStore.emptyState())
    }
}
