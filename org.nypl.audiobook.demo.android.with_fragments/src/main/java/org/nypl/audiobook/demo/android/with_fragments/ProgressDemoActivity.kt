package org.nypl.audiobook.demo.android.with_fragments

import android.app.Activity
import android.os.Bundle
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * A demo activity for the progress indicator.
 */

class ProgressDemoActivity : Activity() {

  private val LOG = LoggerFactory.getLogger(ProgressDemoActivity::class.java)

  private lateinit var executor: ScheduledExecutorService
  private lateinit var progress: PlayerCircularProgressView
  private lateinit var task: ScheduledFuture<*>
  private var currentProgress: Float = 1.0f

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)

    this.setContentView(R.layout.progress_demo_view)

    this.executor = Executors.newSingleThreadScheduledExecutor()
    this.progress = this.findViewById(R.id.progress_demo_progress_view)
    this.progress.progress = 1.0f

    this.task = this.executor.scheduleAtFixedRate({
      UIThread.runOnUIThread(Runnable {
        this.currentProgress = (this.currentProgress + 0.01f) % 1.0f
        this.progress.progress = this.currentProgress
      })
    }, 0L, 250L, TimeUnit.MILLISECONDS)
  }

  override fun onDestroy() {
    super.onDestroy()

    this.setContentView(R.layout.progress_demo_view)

    this.task.cancel(true)
    this.executor.shutdown()
  }
}
