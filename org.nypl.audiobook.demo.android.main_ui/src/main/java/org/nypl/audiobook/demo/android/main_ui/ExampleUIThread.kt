package org.nypl.audiobook.demo.android.main_ui

import android.os.Handler
import android.os.Looper

/**
 * Utilities for working with the Android UI thread.
 */

class ExampleUIThread {

  companion object {

    /**
     * Run the given Runnable on the UI thread.
     *
     * @param r The runnable
     */

    fun runOnUIThread(r: Runnable) {
      val looper = Looper.getMainLooper()
      val h = Handler(looper)
      h.post(r)
    }
  }
}