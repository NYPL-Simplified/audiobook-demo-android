package org.nypl.audiobook.demo.android

import android.app.Activity
import android.os.Bundle
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

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)

    this.setContentView(R.layout.fetch_view)

    val args = this.intent.extras
    if (args == null) {
      throw IllegalStateException("No arguments passed to activity")
    }

    val parameters: FetchParameters =
      args.getSerializable(FetchActivity.FETCH_PARAMETERS_ID) as FetchParameters

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

  private fun onURIFetchSuccess(response: Response) {
    LOG.debug("onURIFetchSuccess: {}", response)

    if (response.isSuccessful) {

    }
  }

  private fun onURIFetchFailure(e: IOException?) {
    ErrorDialogUtilities.showErrorWithRunnable(
      this@FetchActivity,
      LOG,
      "Failed to fetch URI",
      e,
      Runnable {
        this.finish()
      })
  }
}
