package org.nypl.audiobook.demo.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.nypl.audiobook.demo.android.main.R
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * An activity that fetches a book.
 */

class FetchActivity : Activity() {

  companion object {
    private val LOG = LoggerFactory.getLogger(FetchActivity::class.java)

    const val FETCH_PARAMETERS_ID = "org.nypl.audiobook.demo.android.FetchActivity.PARAMETERS_ID"
  }

  /**
   * A runnable that finishes this activity and goes back to the initial one.
   */

  private val GO_BACK_TO_INITIAL_ACTIVITY = Runnable {
    val intent = Intent(this@FetchActivity, InitialActivity::class.java)
    this.startActivity(intent)
    this.finish()
  }

  private var fetch_progress: ProgressBar? = null
  private var fetch_text: TextView? = null

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)

    this.setContentView(R.layout.fetch_view)

    this.fetch_progress = this.findViewById(R.id.fetch_progress_bar)
    this.fetch_text = this.findViewById(R.id.fetch_text)

    val args = this.intent.extras
    if (args == null) {
      throw IllegalStateException("No arguments passed to activity")
    }

    val parameters: FetchParameters =
      args.getSerializable(FetchActivity.FETCH_PARAMETERS_ID) as FetchParameters

    this.doInitialManifestRequest(parameters)
  }

  private fun doInitialManifestRequest(parameters: FetchParameters) {
    val client = OkHttpClient()
    val credential = Credentials.basic(parameters.user, parameters.password)

    val request =
      Request.Builder()
        .url(parameters.fetchURI)
        .header("Authorization", credential)
        .build()

    LOG.debug("fetching {}", parameters.fetchURI)

    val call = client.newCall(request)
    call.enqueue(object : Callback {
      override fun onFailure(call: Call?, e: IOException?) {
        this@FetchActivity.onURIFetchFailure(e)
      }

      override fun onResponse(call: Call?, response: Response?) {
        this@FetchActivity.onURIFetchSuccess(response!!)
      }
    })
  }

  /**
   * A response of some description has been returned by the remote server.
   */

  private fun onURIFetchSuccess(response: Response) {
    LOG.debug("onURIFetchSuccess: {}", response)

    this.onURIFetchSuccessUpdateProgressText()

    if (response.isSuccessful) {
      val stream = response.body().byteStream()
      try {
        val result = RawManifest.parse(stream)
        when (result) {
          is Result.Success -> {
            this.onProcessManifest(result.result)
          }
          is Result.Failure -> {
            ErrorDialogUtilities.showErrorWithRunnable(
              this@FetchActivity,
              LOG,
              "Failed to parse manifest",
              result.failure,
              this.GO_BACK_TO_INITIAL_ACTIVITY)
          }
        }
      } finally {
        stream.close()
      }
    } else {
      ErrorDialogUtilities.showErrorWithRunnable(
        this@FetchActivity,
        LOG,
        "Server returned a failure message: " + response.code() + " " + response.message(),
        null,
        this.GO_BACK_TO_INITIAL_ACTIVITY)
    }
  }

  private fun onURIFetchSuccessUpdateProgressText() {
    UIThread.runOnUIThread(Runnable {
      this.fetch_text!!.setText(R.string.fetch_processing_manifest)
    })
  }

  private fun onProcessManifest(result: RawManifest) {
    LOG.debug("onProcessManifest")

    if (result.metadata.encrypted != null) {
      val encrypted = result.metadata.encrypted
      if (encrypted.scheme == "http://librarysimplified.org/terms/drm/scheme/FAE") {
        this.onProcessManifestIsFindaway(result)
      } else {
        this.onProcessManifestIsOther(result)
      }
    }
  }

  private fun onProcessManifestIsOther(result: RawManifest) {
    LOG.debug("onProcessManifestIsOther")
    this.onProcessManifestIsOtherUpdateProgressTest()
  }

  private fun onProcessManifestIsOtherUpdateProgressTest() {
    UIThread.runOnUIThread(Runnable {
      this.fetch_text!!.setText(R.string.fetch_received_other_manifest)
    })
  }

  private fun onProcessManifestIsFindaway(result: RawManifest) {
    LOG.debug("onProcessManifestIsFindaway")
    this.onProcessManifestIsFindawayUpdateProgressText()
  }

  private fun onProcessManifestIsFindawayUpdateProgressText() {
    UIThread.runOnUIThread(Runnable {
      this.fetch_text!!.setText(R.string.fetch_received_findaway_manifest)
    })
  }

  private fun onURIFetchFailure(e: IOException?) {
    ErrorDialogUtilities.showErrorWithRunnable(
      this@FetchActivity,
      LOG,
      "Failed to fetch URI",
      e,
      this.GO_BACK_TO_INITIAL_ACTIVITY)
  }
}
