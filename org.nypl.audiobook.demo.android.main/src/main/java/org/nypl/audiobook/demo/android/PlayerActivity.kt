package org.nypl.audiobook.demo.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import io.audioengine.mobile.AudioEngine
import io.audioengine.mobile.config.LogLevel
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

class PlayerActivity : Activity() {

  companion object {
    private val LOG = LoggerFactory.getLogger(PlayerActivity::class.java)

    const val FETCH_PARAMETERS_ID = "org.nypl.audiobook.demo.android.PlayerActivity.PARAMETERS_ID"
  }

  /**
   * A runnable that finishes this activity and goes back to the initial one.
   */

  private val GO_BACK_TO_INITIAL_ACTIVITY = Runnable {
    val intent = Intent(this@PlayerActivity, InitialActivity::class.java)
    this.startActivity(intent)
    this.finish()
  }

  /**
   * The state of the player.
   */

  sealed class PlayerState {

    /**
     * The player is waiting for a manifest from the server. This is the initial state.
     */

    data class PlayerStateWaitingForManifest(
      val message : String) : PlayerState()

    /**
     * The player has received a response from the server but doesn't yet know if the response
     * is usable.
     */

    data class PlayerStateReceivedResponse(
      val message : String) : PlayerState()

    /**
     * The player has received a manifest from the server.
     */

    data class PlayerStateReceivedManifest(
      val manifest : RawManifest) : PlayerState()

  }

  private lateinit var fetch_view: View
  private lateinit var fetch_progress: ProgressBar
  private lateinit var fetch_text: TextView

  private lateinit var player_view: View
  private lateinit var player_play: Button
  private lateinit var player_skip_backward: Button
  private lateinit var player_skip_forward: Button
  private lateinit var player_toc: ListView
  private lateinit var player_title: TextView

  private lateinit var state: PlayerState

  override fun onCreate(state: Bundle?) {
    org.nypl.audiobook.demo.android.PlayerActivity.Companion.LOG.debug("onCreate")

    super.onCreate(state)

    this.state = PlayerState.PlayerStateWaitingForManifest(
      this.getString(R.string.fetch_requesting_manifest))

    this.fetch_view =
      this.layoutInflater.inflate(R.layout.fetch_view, null)
    this.fetch_progress =
      this.fetch_view.findViewById(R.id.fetch_progress_bar)
    this.fetch_text =
      this.fetch_view.findViewById(R.id.fetch_text)

    this.player_view =
      this.layoutInflater.inflate(R.layout.player_view, null)

    this.player_play =
      this.player_view.findViewById(R.id.player_play)
    this.player_skip_backward =
      this.player_view.findViewById(R.id.player_skip_backward)
    this.player_skip_forward =
      this.player_view.findViewById(R.id.player_skip_forward)
    this.player_toc =
      this.player_view.findViewById(R.id.player_toc)
    this.player_title =
      this.player_view.findViewById(R.id.player_title)

    this.setContentView(this.fetch_view)

    val args = this.intent.extras
    if (args == null) {
      throw IllegalStateException("No arguments passed to activity")
    }

    val parameters: PlayerParameters =
      args.getSerializable(PlayerActivity.FETCH_PARAMETERS_ID) as PlayerParameters

    this.doInitialManifestRequest(parameters)
  }

  override fun onResume() {
    org.nypl.audiobook.demo.android.PlayerActivity.Companion.LOG.debug("onResume")

    super.onResume()
    this.configurePlayerViewFromState(this.state)
  }

  private fun configurePlayerViewFromState(state : PlayerState)
  {
    UIThread.checkIsUIThread()

    when (state) {

      is PlayerState.PlayerStateWaitingForManifest -> {
        this.fetch_view.visibility = View.VISIBLE
        this.player_view.visibility = View.GONE
        this.setContentView(this.fetch_view)
        this.fetch_text.text = state.message
      }

      is PlayerState.PlayerStateReceivedResponse -> {
        this.fetch_view.visibility = View.VISIBLE
        this.player_view.visibility = View.GONE
        this.setContentView(this.fetch_view)
        this.fetch_text.text = state.message
      }

      is PlayerState.PlayerStateReceivedManifest -> {
        this.fetch_view.visibility = View.GONE
        this.player_view.visibility = View.VISIBLE
        this.setContentView(this.player_view)
        this.player_title.text = state.manifest.metadata.title
        this.player_toc.adapter =
          PlayerTOCArrayAdapter(
            this,
            state.manifest.spine.map { spine_item -> PlayerTOCEntry(spine_item) })
      }
    }
  }

  private data class PlayerTOCEntry(
    val spine_item : RawSpineItem,
    var available : Double = 0.0)

  private class PlayerTOCArrayAdapter(
    private val context : Activity,
    private val items : List<PlayerTOCEntry>)
    : ArrayAdapter<PlayerTOCEntry>(context, R.layout.player_toc_entry, items) {

    override fun getView(
      position: Int,
       reuse: View?,
       parent: ViewGroup): View {

      val item = this.items.get(position)
      val inflater = this.context.getLayoutInflater()
      val view = if (reuse != null) {
        reuse
      } else {
        inflater.inflate(R.layout.player_toc_entry, parent, false)
      }

      val title_view = view.findViewById<TextView>(R.id.player_toc_entry_title)
      val percent_view = view.findViewById<TextView>(R.id.player_toc_entry_percent)
      title_view.text = item.spine_item.values["title"].toString()
      percent_view.text = String.format("%.1f%%", item.available)
      return view
    }
  }

  private fun doInitialManifestRequest(parameters: PlayerParameters) {
    val client = OkHttpClient()
    val credential = Credentials.basic(parameters.user, parameters.password)

    val request =
      Request.Builder()
        .url(parameters.fetchURI)
        .header("Authorization", credential)
        .build()

    org.nypl.audiobook.demo.android.PlayerActivity.Companion.LOG.debug("fetching {}", parameters.fetchURI)

    val call = client.newCall(request)
    call.enqueue(object : Callback {
      override fun onFailure(call: Call?, e: IOException?) {
        this@PlayerActivity.onURIFetchFailure(e)
      }

      override fun onResponse(call: Call?, response: Response?) {
        this@PlayerActivity.onURIFetchSuccess(response!!)
      }
    })
  }

  /**
   * A response of some description has been returned by the remote server.
   */

  private fun onURIFetchSuccess(response: Response) {
    org.nypl.audiobook.demo.android.PlayerActivity.Companion.LOG.debug("onURIFetchSuccess: {}", response)

    UIThread.runOnUIThread(Runnable {
      val id = R.string.fetch_processing_manifest
      val text = this.getString(id)
      val state = PlayerState.PlayerStateReceivedResponse(text)
      this.configurePlayerViewFromState(state)
    })

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
              this@PlayerActivity,
              org.nypl.audiobook.demo.android.PlayerActivity.Companion.LOG,
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
        this@PlayerActivity,
        org.nypl.audiobook.demo.android.PlayerActivity.Companion.LOG,
        "Server returned a failure message: " + response.code() + " " + response.message(),
        null,
        this.GO_BACK_TO_INITIAL_ACTIVITY)
    }
  }

  private fun onProcessManifest(result: RawManifest) {
    org.nypl.audiobook.demo.android.PlayerActivity.Companion.LOG.debug("onProcessManifest")

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
    org.nypl.audiobook.demo.android.PlayerActivity.Companion.LOG.debug("onProcessManifestIsOther")

    UIThread.runOnUIThread(Runnable {
      this.state = PlayerState.PlayerStateReceivedManifest(result)
      this.configurePlayerViewFromState(this.state)
    })
  }

  private fun onProcessManifestIsFindaway(result: RawManifest) {
    org.nypl.audiobook.demo.android.PlayerActivity.Companion.LOG.debug("onProcessManifestIsFindaway")

    UIThread.runOnUIThread(Runnable {
      this.state = PlayerState.PlayerStateReceivedManifest(result)
      this.configurePlayerViewFromState(this.state)
    })

    val encrypted = result.metadata.encrypted!!
    val session = encrypted.values["findaway:sessionKey"].toString()

    org.nypl.audiobook.demo.android.PlayerActivity.Companion.LOG.debug("initializing audio engine")
    AudioEngine.init(this, session, LogLevel.VERBOSE)
    org.nypl.audiobook.demo.android.PlayerActivity.Companion.LOG.debug("initialized audio engine")

    val engine = AudioEngine.getInstance()
  }

  private fun onURIFetchFailure(e: IOException?) {
    ErrorDialogUtilities.showErrorWithRunnable(
      this@PlayerActivity,
      org.nypl.audiobook.demo.android.PlayerActivity.Companion.LOG,
      "Failed to fetch URI",
      e,
      this.GO_BACK_TO_INITIAL_ACTIVITY)
  }
}
