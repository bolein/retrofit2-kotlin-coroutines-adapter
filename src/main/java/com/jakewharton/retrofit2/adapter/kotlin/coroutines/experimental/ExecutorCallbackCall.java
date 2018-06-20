package com.jakewharton.retrofit2.adapter.kotlin.coroutines.experimental;

import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.util.concurrent.Executor;

import static kotlin.jvm.internal.Intrinsics.checkNotNull;

/**
 * Copied from Retrofit sources
 */
final class ExecutorCallbackCall<T> implements Call<T> {
    final Executor callbackExecutor;
    final Call<T> delegate;

    ExecutorCallbackCall(Executor callbackExecutor, Call<T> delegate) {
        this.callbackExecutor = callbackExecutor;
        this.delegate = delegate;
    }

    @Override public void enqueue(final Callback<T> callback) {
        checkNotNull(callback, "callback == null");

        delegate.enqueue(new Callback<T>() {
            @Override public void onResponse(Call<T> call, final Response<T> response) {
                callbackExecutor.execute(new Runnable() {
                    @Override public void run() {
                        if (delegate.isCanceled()) {
                            // Emulate OkHttp's behavior of throwing/delivering an IOException on cancellation.
                            callback.onFailure(ExecutorCallbackCall.this, new IOException("Canceled"));
                        } else {
                            callback.onResponse(ExecutorCallbackCall.this, response);
                        }
                    }
                });
            }

            @Override public void onFailure(Call<T> call, final Throwable t) {
                callbackExecutor.execute(new Runnable() {
                    @Override public void run() {
                        callback.onFailure(ExecutorCallbackCall.this, t);
                    }
                });
            }
        });
    }

    @Override public boolean isExecuted() {
        return delegate.isExecuted();
    }

    @Override public Response<T> execute() throws IOException {
        return delegate.execute();
    }

    @Override public void cancel() {
        delegate.cancel();
    }

    @Override public boolean isCanceled() {
        return delegate.isCanceled();
    }

    @SuppressWarnings("CloneDoesntCallSuperClone") // Performing deep clone.
    @Override public Call<T> clone() {
        return new ExecutorCallbackCall<T>(callbackExecutor, delegate.clone());
    }

    @Override public Request request() {
        return delegate.request();
    }
}
