package com.jakewharton.retrofit2.adapter.kotlin.coroutines.experimental

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.Executor
import kotlin.jvm.internal.Intrinsics.checkNotNull

/**
 * Inspired by Retrofit sources
 */
internal class ExecutorCallbackCall<T>(
        val callbackExecutor: Executor,
        val delegate: Call<T>
) : Call<T> by delegate {

    override fun enqueue(callback: Callback<T>) {
        checkNotNull(callback, "callback == null")

        delegate.enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                callbackExecutor.execute {
                    if (delegate.isCanceled) {
                        // Emulate OkHttp's behavior of throwing/delivering an IOException on cancellation.
                        callback.onFailure(this@ExecutorCallbackCall, IOException("Canceled"))
                    } else {
                        callback.onResponse(this@ExecutorCallbackCall, response)
                    }
                }
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                callbackExecutor.execute {
                    callback.onFailure(this@ExecutorCallbackCall, t)
                }
            }
        })
    }

    override// Performing deep clone.
    fun clone(): Call<T> {
        return ExecutorCallbackCall(callbackExecutor, delegate.clone())
    }
}
