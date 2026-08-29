package com.krystelligence.solipsism.browser.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.krystelligence.antares.protocol.IAntaresEngine
import io.reactivex.rxjava3.core.Completable
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/** Process-wide, crash-aware connection to the separately installed Antares package. */
@Singleton
class AntaresEngineConnection @Inject constructor(context: Context) {
    private val applicationContext = context.applicationContext
    private val enginePackage = AntaresEnginePackage(applicationContext)
    private val waiting = CopyOnWriteArrayList<(Result<IAntaresEngine>) -> Unit>()
    private val stateLock = Any()
    @Volatile private var engine: IAntaresEngine? = null
    @Volatile private var bound = false
    @Volatile private var connecting = false

    private val deathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, "Antares Binder died; waiting for the service binding to reconnect")
        synchronized(stateLock) {
            engine = null
            connecting = bound
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Antares service connected: $name")
            val candidate = IAntaresEngine.Stub.asInterface(binder)
            val result = runCatching {
                requireNotNull(binder) { "Antares returned no Binder" }
                require(candidate.protocolVersion in AntaresProtocol.MIN_COMPATIBLE_VERSION..AntaresProtocol.VERSION) {
                    "Antares protocol ${candidate.protocolVersion} is incompatible with ${AntaresProtocol.MIN_COMPATIBLE_VERSION}-${AntaresProtocol.VERSION}"
                }
                binder.linkToDeath(deathRecipient, 0)
                synchronized(stateLock) {
                    engine = candidate
                    bound = true
                    connecting = false
                }
                candidate
            }.onFailure {
                Log.e(TAG, "Antares service validation failed", it)
                synchronized(stateLock) {
                    engine = null
                    connecting = false
                }
            }
            if (result.isFailure) releaseDeadBinding()
            if (result.isSuccess) Log.d(TAG, "Antares protocol verified")
            drain(result)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Antares service disconnected; Android will reconnect the active binding")
            synchronized(stateLock) {
                engine = null
                connecting = bound
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.w(TAG, "Antares binding died; releasing it so the next request can rebind")
            releaseDeadBinding()
            if (waiting.isNotEmpty()) bind()
        }

        override fun onNullBinding(name: ComponentName?) {
            releaseDeadBinding()
            drain(Result.failure(RemoteException("Antares Engine returned no Binder")))
        }
    }

    fun withEngine(callback: (Result<IAntaresEngine>) -> Unit) {
        engine?.takeIf { it.asBinder().isBinderAlive }?.let { existing ->
            callback(Result.success(existing))
            return
        }
        waiting += callback

        val shouldBind = synchronized(stateLock) {
            if (engine?.asBinder()?.isBinderAlive == true) {
                false
            } else if (bound || connecting) {
                false
            } else {
                connecting = true
                true
            }
        }
        engine?.takeIf { it.asBinder().isBinderAlive }?.let {
            drain(Result.success(it))
            return
        }
        if (!shouldBind) return

        Log.d(TAG, "No live Antares Binder; starting a service binding")
        bind()
    }

    private fun bind() {
        synchronized(stateLock) {
            if (bound) return
            connecting = true
        }

        val status = enginePackage.status()
        if (!status.usable) {
            Log.e(TAG, "Antares package is unusable: ${status.reason}")
            synchronized(stateLock) { connecting = false }
            drain(Result.failure(IllegalStateException(status.reason ?: "Antares Engine is unavailable")))
            return
        }
        val didBind = runCatching {
            applicationContext.bindService(
                enginePackage.serviceIntent(),
                connection,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT,
            )
        }.onFailure { Log.e(TAG, "Unable to bind Antares Engine", it) }
            .getOrDefault(false)
        Log.d(TAG, "Antares bindService returned $didBind")
        if (!didBind) {
            synchronized(stateLock) {
                bound = false
                connecting = false
            }
            drain(Result.failure(IllegalStateException("Unable to bind Antares Engine")))
        } else {
            synchronized(stateLock) { bound = true }
        }
    }

    /** Verifies package trust, service binding, and protocol compatibility before a core switch. */
    fun verify(): Completable = Completable.create { emitter ->
        withEngine { result ->
            if (emitter.isDisposed) return@withEngine
            result.fold(
                onSuccess = { emitter.onComplete() },
                onFailure = emitter::onError,
            )
        }
    }

    /**
     * Releases the bound engine when the global core changes to WebView. Servo's Android runtime
     * cannot be initialised twice in one process, so Antares deliberately starts with a fresh
     * isolated process the next time it is selected.
     */
    fun disconnect() {
        val oldEngine: IAntaresEngine?
        val shouldUnbind: Boolean
        synchronized(stateLock) {
            oldEngine = engine
            engine = null
            connecting = false
            shouldUnbind = bound
            bound = false
        }
        oldEngine?.asBinder()?.unlinkToDeath(deathRecipient, 0)
        if (shouldUnbind) {
            runCatching { applicationContext.unbindService(connection) }
                .onFailure { Log.w(TAG, "Unable to unbind Antares Engine", it) }
        }
        if (waiting.isNotEmpty()) {
            drain(Result.failure(RemoteException("Antares Engine connection was released")))
        }
    }

    private fun drain(result: Result<IAntaresEngine>) {
        val callbacks = waiting.toList()
        waiting.clear()
        callbacks.forEach { it(result) }
    }

    private fun releaseDeadBinding() {
        val shouldUnbind = synchronized(stateLock) {
            engine = null
            connecting = false
            bound.also { bound = false }
        }
        if (shouldUnbind) {
            runCatching { applicationContext.unbindService(connection) }
                .onFailure { Log.w(TAG, "Unable to release dead Antares binding", it) }
        }
    }

    private companion object {
        const val TAG = "AntaresConnection"
    }
}
