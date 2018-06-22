package com.jakewharton.retrofit2.adapter.kotlin.coroutines.experimental

import java.util.concurrent.Executor

class MockExecutor : Executor {
  override fun execute(command: Runnable) = command.run()
}