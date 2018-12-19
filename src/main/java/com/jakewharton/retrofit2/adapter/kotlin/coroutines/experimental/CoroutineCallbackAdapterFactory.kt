package com.jakewharton.retrofit2.adapter.kotlin.coroutines.experimental

import retrofit2.Call
import retrofit2.Callback
import retrofit2.CallbackAdapter
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.Executor
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CoroutineCallbackAdapterFactory(
        private val executor: Executor
) : CallbackAdapter.Factory() {

  override fun get(
      parameterType: Type,
      returnType: Type,
      annotations: Array<out Annotation>,
      retrofit: Retrofit
  ): CallbackAdapter<*, *>? {
    if (Continuation::class.java != getRawType(parameterType)) {
      return null
    }
    if (Any::class.java != returnType) {
      return null
    }
    if (parameterType !is ParameterizedType) {
      throw AssertionError("Continuation type must be parameterized. Report this as a bug.")
    }
    val responseType = getParameterLowerBound(0, parameterType)

    val rawResponseType = getRawType(responseType)
    return if (rawResponseType == Response::class.java) {
      if (responseType !is ParameterizedType) {
        throw IllegalStateException("Response must be parameterized as Response<Foo> or Response<out Foo>")
      }
      ResponseCallbackAdapter<Any>(getParameterUpperBound(0, responseType), executor)
    } else {
      BodyCallbackAdapter<Any>(responseType, executor)
    }
  }

  private class BodyCallbackAdapter<T>(
      private val responseType: Type,
      private val executor: Executor
  ) : CallbackAdapter<T, Continuation<T>> {

    override fun responseType() = responseType

    override fun adapt(call: Call<T>, continuation: Continuation<T>): Any? {
      val snapshot = Exception() // used to keep the stack trace
      ExecutorCallbackCall(executor, call).enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
          if (response.isSuccessful) {
            continuation.resume(response.body()!!)
          } else {
            continuation.resumeWithException(snapshot.wrap(HttpException(response)))
          }
        }

        override fun onFailure(call: Call<T>, t: Throwable) {
          continuation.resumeWithException(snapshot.wrap(t))
        }
      })
      return COROUTINE_SUSPENDED
    }
  }

  private class ResponseCallbackAdapter<T>(
      private val responseType: Type,
      private val executor: Executor
  ) : CallbackAdapter<T, Continuation<Response<T>>> {

    override fun responseType() = responseType

    override fun adapt(call: Call<T>, continuation: Continuation<Response<T>>): Any? {
      val snapshot = Exception() // used to keep the stack trace
      ExecutorCallbackCall(executor, call).enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
          continuation.resume(response)
        }

        override fun onFailure(call: Call<T>, t: Throwable) {
          continuation.resumeWithException(snapshot.wrap(t))
        }
      })
      return COROUTINE_SUSPENDED
    }
  }
}

/**
 * Appends stack trace to make the response exception lead to caller's site
 */
internal fun Throwable.wrap(e: Throwable): Throwable {
  e.stackTrace += this.stackTrace.drop(2)
  return e
}