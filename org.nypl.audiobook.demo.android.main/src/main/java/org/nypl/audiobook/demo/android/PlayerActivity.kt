package org.nypl.audiobook.demo.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.nypl.audiobook.demo.android.api.PlayerAudioBookType
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementDownloadFailed
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementDownloaded
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementDownloading
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementInitial
import org.nypl.audiobook.demo.android.api.PlayerSpineElementType
import org.nypl.audiobook.demo.android.findaway.PlayerFindawayAudioBook
import org.nypl.audiobook.demo.android.findaway.PlayerFindawayManifest
import org.nypl.audiobook.demo.android.main.R
import org.slf4j.LoggerFactory
import rx.Subscription
import java.io.IOException

/**
 * An activity that fetches a book.
 */

class PlayerActivity : Activity() {

  companion object {
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

  private val log = LoggerFactory.getLogger(PlayerActivity::class.java)

  /**
   * The state of the player.
   */

  sealed class PlayerState {

    /**
     * The player is waiting for a manifest from the server. This is the initial state.
     */

    data class PlayerStateWaitingForManifest(
      val message: String) : PlayerState()

    /**
     * The player has received a response from the server but doesn't yet know if the response
     * is usable.
     */

    data class PlayerStateReceivedResponse(
      val message: String) : PlayerState()

    /**
     * The player has received a manifest from the server.
     */

    data class PlayerStateReceivedManifest(
      val message: String,
      val manifest: RawManifest) : PlayerState()

    /**
     * The player has configured the audio engine.
     */

    data class PlayerStateConfigured(
      val book: PlayerAudioBookType) : PlayerState()
  }

  private lateinit var fetch_view: View
  private lateinit var fetch_progress: ProgressBar
  private lateinit var fetch_text: TextView

  private lateinit var player_view: View
  private lateinit var player_play: Button
  private lateinit var player_skip_backward: Button
  private lateinit var player_skip_forward: Button
  private lateinit var player_toc: ListView
  private lateinit var player_toc_adapter: PlayerSpineElementArrayAdapter
  private lateinit var player_title: TextView

  private lateinit var state: PlayerState
  private var spine_element_subscription: Subscription? = null

  override fun onCreate(state: Bundle?) {
    this.log.debug("onCreate")

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
    this.log.debug("onResume")

    super.onResume()
    this.configurePlayerViewFromState(this.state)
  }

  override fun onDestroy() {
    this.log.debug("onDestroy")
    super.onDestroy()

    val sub = this.spine_element_subscription
    if (sub != null) {
      sub.unsubscribe()
    }
  }

  private fun configurePlayerViewFromState(state: PlayerState) {
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
        this.fetch_view.visibility = View.VISIBLE
        this.player_view.visibility = View.GONE
        this.setContentView(this.fetch_view)
        this.fetch_text.text = state.message
      }

      is PlayerState.PlayerStateConfigured -> {
        this.fetch_view.visibility = View.GONE
        this.player_view.visibility = View.VISIBLE
        this.setContentView(this.player_view)
        this.player_title.text = state.book.title
        this.player_toc_adapter = PlayerSpineElementArrayAdapter(this, state.book.spine)
        this.player_toc.adapter = this.player_toc_adapter
      }
    }
  }

  /**
   * A reconfigurable view that shows the status of a spine element.
   */

  private class PlayerSpineElementView(
    context: Activity,
    attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private var view_initial: ViewGroup
    private var view_initial_title: TextView
    private var view_initial_download: Button

    private var view_downloading: ViewGroup
    private var view_downloading_title: TextView
    private var view_downloading_progress: ProgressBar
    private var view_downloading_cancel: Button

    private var view_download_failed: ViewGroup
    private var view_download_failed_text: TextView
    private var view_download_failed_dismiss: Button

    private lateinit var item: PlayerSpineElementType

    init {
      context.layoutInflater.inflate(R.layout.player_toc_entry, this, true)

      this.view_initial =
        this.findViewById(R.id.player_toc_entry_initial)
      this.view_initial_title =
        this.view_initial.findViewById(R.id.player_toc_entry_initial_title)
      this.view_initial_download =
        this.view_initial.findViewById(R.id.player_toc_entry_initial_download)

      this.view_downloading =
        this.findViewById(R.id.player_toc_entry_downloading)
      this.view_downloading_title =
        this.view_downloading.findViewById(R.id.player_toc_entry_downloading_title)
      this.view_downloading_progress =
        this.view_downloading.findViewById(R.id.player_toc_entry_downloading_progress)
      this.view_downloading_cancel =
        this.view_downloading.findViewById(R.id.player_toc_entry_downloading_cancel)

      this.view_download_failed =
        this.findViewById(R.id.player_toc_entry_download_failed)
      this.view_download_failed_text =
        this.view_download_failed.findViewById(R.id.player_toc_entry_download_failed_text)
      this.view_download_failed_dismiss =
        this.view_download_failed.findViewById(R.id.player_toc_entry_download_failed_dismiss)

      this.view_initial.visibility = View.VISIBLE
      this.view_downloading.visibility = View.GONE
      this.view_download_failed.visibility = View.GONE
    }

    fun viewConfigure(item: PlayerSpineElementType) {
      UIThread.checkIsUIThread()

      this.item = item
      this.view_initial_title.text = item.title
      this.view_downloading_title.text = item.title

      val status = item.status
      when (status) {
        is PlayerSpineElementInitial -> {
          this.view_downloading.visibility = View.GONE
          this.view_download_failed.visibility = View.GONE
          this.view_initial.visibility = View.VISIBLE
          this.view_initial_download.setOnClickListener { item.downloadTask.fetch() }
        }
        is PlayerSpineElementDownloadFailed -> {
          this.view_downloading.visibility = View.GONE
          this.view_download_failed.visibility = View.VISIBLE
          this.view_initial.visibility = View.GONE
          this.view_download_failed_text.text = status.message
          this.view_download_failed_dismiss.setOnClickListener { item.downloadTask.delete() }
        }
        is PlayerSpineElementDownloaded -> {
          this.view_downloading.visibility = View.GONE
          this.view_download_failed.visibility = View.GONE
          this.view_initial.visibility = View.VISIBLE
        }
        is PlayerSpineElementDownloading -> {
          this.view_downloading.visibility = View.VISIBLE
          this.view_download_failed.visibility = View.GONE
          this.view_initial.visibility = View.GONE
          this.view_downloading_cancel.setOnClickListener { item.downloadTask.delete() }
        }
      }
    }
  }

  /**
   * An array adapter for instantiating views for spine elements.
   */

  private class PlayerSpineElementArrayAdapter(
    private val context: Activity,
    private val items: List<PlayerSpineElementType>)
    : ArrayAdapter<PlayerSpineElementType>(context, R.layout.player_toc_entry, items) {

    override fun getView(
      position: Int,
      reuse: View?,
      parent: ViewGroup): View {

      val item = this.items.get(position)
      val view = reuse as PlayerSpineElementView? ?: PlayerSpineElementView(this.context, null)
      view.viewConfigure(item)
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

    this.log.debug("fetching {}", parameters.fetchURI)

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
    this.log.debug("onURIFetchSuccess: {}", response)

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
              this.log,
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
        this.log,
        "Server returned a failure message: " + response.code() + " " + response.message(),
        null,
        this.GO_BACK_TO_INITIAL_ACTIVITY)
    }
  }

  private fun onProcessManifest(manifest: RawManifest) {
    this.log.debug("onProcessManifest")

    if (manifest.metadata.encrypted != null) {
      val encrypted = manifest.metadata.encrypted
      if (encrypted.scheme == "http://librarysimplified.org/terms/drm/scheme/FAE") {
        this.onProcessManifestIsFindaway(manifest)
      } else {
        this.onProcessManifestIsOther(manifest)
      }
    }
  }

  private fun onProcessManifestIsOther(manifest: RawManifest) {
    this.log.debug("onProcessManifestIsOther")

    UIThread.runOnUIThread(Runnable {
      this.state = PlayerState.PlayerStateReceivedManifest(
        this.getString(R.string.fetch_received_other_manifest), manifest)
      this.configurePlayerViewFromState(this.state)
    })
  }

  private fun onProcessManifestIsFindaway(manifest: RawManifest) {
    this.log.debug("onProcessManifestIsFindaway")

    UIThread.runOnUIThread(Runnable {
      this.state = PlayerState.PlayerStateReceivedManifest(
        this.getString(R.string.fetch_received_findaway_manifest), manifest)
      this.configurePlayerViewFromState(this.state)
    })

    val manifest_result = PlayerFindawayManifest.transform(manifest)
    when (manifest_result) {
      is Result.Failure -> {
        throw manifest_result.failure
      }

      is Result.Success -> {

        /*
         * Initialize the audio engine and configure the player state.
         */

        val book = PlayerFindawayAudioBook.create(this, manifest_result.result)

        UIThread.runOnUIThread(Runnable {
          this.state = PlayerState.PlayerStateConfigured(book)
          this.configurePlayerViewFromState(this.state)
        })

        /*
         * Subscribe to spine element status updates.
         */

        this.spine_element_subscription = book.spineElementStatusUpdates.subscribe(
          { event -> this.onSpineElementStatusChanged(book, event!!) },
          { event -> this.onSpineElementStatusError(book, event!!) })
      }
    }
  }

  private fun onSpineElementStatusError(
    book: PlayerAudioBookType,
    event: Throwable) {

    this.log.error("onSpineElementStatusError: ", event)
  }

  private fun onSpineElementStatusChanged(
    book: PlayerAudioBookType,
    event: PlayerSpineElementStatus) {

    /*
     * Notify the table of contents that the contents of the list it is displaying has changed.
     * This will cause it to inspect the states of each spine element and will, as a result, display
     * the correct states onscreen.
     */

    UIThread.runOnUIThread(Runnable { this.player_toc_adapter.notifyDataSetChanged() })
  }

  private fun onURIFetchFailure(e: IOException?) {
    ErrorDialogUtilities.showErrorWithRunnable(
      this@PlayerActivity,
      this.log,
      "Failed to fetch URI",
      e,
      this.GO_BACK_TO_INITIAL_ACTIVITY)
  }
}
