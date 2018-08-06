package org.nypl.audiobook.demo.android.with_api

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.joda.time.Duration
import org.joda.time.format.PeriodFormatter
import org.joda.time.format.PeriodFormatterBuilder
import org.nypl.audiobook.android.api.PlayerAudioBookType
import org.nypl.audiobook.android.api.PlayerAudioEngineRequest
import org.nypl.audiobook.android.api.PlayerAudioEngines
import org.nypl.audiobook.android.api.PlayerEvent
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventChapterCompleted
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventChapterWaiting
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventPlaybackBuffering
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventPlaybackPaused
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventPlaybackProgressUpdate
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventPlaybackStarted
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventPlaybackStopped
import org.nypl.audiobook.android.api.PlayerManifest
import org.nypl.audiobook.android.api.PlayerManifests
import org.nypl.audiobook.android.api.PlayerResult
import org.nypl.audiobook.android.api.PlayerSpineElementDownloadStatus
import org.nypl.audiobook.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloaded
import org.nypl.audiobook.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementNotDownloaded
import org.nypl.audiobook.android.api.PlayerSpineElementType
import org.nypl.audiobook.android.api.PlayerType
import org.nypl.audiobook.demo.android.with_api.PlayerActivity.PlayerSpineElementPlayingStatus.PlayerSpineElementBuffering
import org.nypl.audiobook.demo.android.with_api.PlayerActivity.PlayerSpineElementPlayingStatus.PlayerSpineElementNotPlaying
import org.nypl.audiobook.demo.android.with_api.PlayerActivity.PlayerSpineElementPlayingStatus.PlayerSpineElementPaused
import org.nypl.audiobook.demo.android.with_api.PlayerActivity.PlayerSpineElementPlayingStatus.PlayerSpineElementPlaying
import org.nypl.audiobook.demo.android.with_api.PlayerActivity.PlayerState.PlayerStateConfigured
import org.nypl.audiobook.demo.android.with_api.PlayerActivity.PlayerState.PlayerStateReceivedManifest
import org.nypl.audiobook.demo.android.with_api.PlayerActivity.PlayerState.PlayerStateReceivedResponse
import org.nypl.audiobook.demo.android.with_api.PlayerActivity.PlayerState.PlayerStateWaitingForManifest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Subscription
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A player activity.
 */

class PlayerActivity : Activity() {

  private val log = LoggerFactory.getLogger(PlayerActivity::class.java)

  companion object {
    const val FETCH_PARAMETERS_ID =
      "org.nypl.audiobook.demo.android.with_api.PlayerActivity.PARAMETERS_ID"
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
      val manifest: PlayerManifest) : PlayerState()

    /**
     * The player has configured the audio engine.
     */

    data class PlayerStateConfigured(
      val manifest: PlayerManifest,
      val book: PlayerAudioBookType,
      val player: PlayerType) : PlayerState()
  }

  private val periodFormatter: PeriodFormatter =
    PeriodFormatterBuilder()
      .printZeroAlways()
      .minimumPrintedDigits(2)
      .appendHours()
      .appendLiteral(":")
      .appendMinutes()
      .appendLiteral(":")
      .appendSeconds()
      .toFormatter()

  private lateinit var fetchView: View
  private lateinit var fetchProgress: ProgressBar
  private lateinit var fetchText: TextView

  private lateinit var playerView: View
  private lateinit var playerPlay: Button
  private lateinit var playerSkipBackward: Button
  private lateinit var playerSkipForward: Button
  private lateinit var playerSkipPrevious: Button
  private lateinit var playerSkipNext: Button
  private lateinit var playerToc: ListView
  private lateinit var playerTitle: TextView
  private lateinit var playerTime: TextView
  private lateinit var playerTimeMaximum: TextView
  private lateinit var playerBar: ProgressBar
  private lateinit var playerTocAdapter: PlayerSpineElementArrayAdapter

  private val stateLock: Any = Object()
  @net.jcip.annotations.GuardedBy("stateLock")
  private lateinit var state: PlayerState

  private var spineElementSubscription: Subscription? = null
  private var playerEventSubscription: Subscription? = null

  private lateinit var downloadExecutor: ListeningExecutorService

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)

    this.setContentView(R.layout.player_view)

    synchronized(this.stateLock, {
      this.state = PlayerStateWaitingForManifest(this.getString(R.string.fetch_requesting_manifest))
    })

    /*
     * Create an executor for download threads. Each thread is assigned a useful name
     * for correct blame assignment during debugging.
     */

    this.downloadExecutor =
      MoreExecutors.listeningDecorator(
        Executors.newFixedThreadPool(4, { r: Runnable? ->
          val thread = Thread(r)
          thread.name = "org.nypl.audiobook.demo.android.with_api.downloader-${thread.id}"
          thread
        }))

    this.fetchView =
      this.layoutInflater.inflate(R.layout.fetch_view, null)
    this.fetchProgress =
      this.fetchView.findViewById(R.id.fetch_progress_bar)
    this.fetchText =
      this.fetchView.findViewById(R.id.fetch_text)

    this.playerView =
      this.layoutInflater.inflate(R.layout.player_view, null)

    this.playerPlay =
      this.playerView.findViewById(R.id.player_play)
    this.playerSkipBackward =
      this.playerView.findViewById(R.id.player_skip_backward)
    this.playerSkipForward =
      this.playerView.findViewById(R.id.player_skip_forward)
    this.playerSkipPrevious =
      this.playerView.findViewById(R.id.player_skip_previous)
    this.playerSkipNext =
      this.playerView.findViewById(R.id.player_skip_next)
    this.playerToc =
      this.playerView.findViewById(R.id.player_toc)
    this.playerTitle =
      this.playerView.findViewById(R.id.player_title)
    this.playerTime =
      this.playerView.findViewById(R.id.player_time)
    this.playerTimeMaximum =
      this.playerView.findViewById(R.id.player_time_maximum)
    this.playerBar =
      this.playerView.findViewById(R.id.player_seek_bar)

    this.setContentView(this.fetchView)

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

    this.spineElementSubscription?.unsubscribe()
    this.playerEventSubscription?.unsubscribe()

    /*
     * Close the player if it is open.
     */

    val currentState = this.state
    when (currentState) {
      is PlayerStateWaitingForManifest,
      is PlayerStateReceivedResponse,
      is PlayerStateReceivedManifest -> Unit
      is PlayerStateConfigured -> currentState.player.close()
    }
  }

  /**
   * Fetch the manifest from the remote server.
   */

  private fun doInitialManifestRequest(parameters: PlayerParameters) {
    val client = OkHttpClient()

    val request_builder =
      Request.Builder()
        .url(parameters.fetchURI)

    /*
     * Use basic auth if a username and password were given.
     */

    val credentials = parameters.credentials
    if (credentials != null) {
      request_builder.header(
        "Authorization",
        Credentials.basic(credentials.user, credentials.password))
    }

    val request = request_builder.build()

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
   * Fetching the manifest was successful.
   */

  private fun onURIFetchSuccess(response: Response) {
    this.log.debug("onURIFetchSuccess: {}", response)

    /**
     * Update the visual state of the player to indicate that a manifest is being processed.
     */

    UIThread.runOnUIThread(Runnable {
      val id = R.string.fetch_processing_manifest
      val text = this.getString(id)
      val state = PlayerStateReceivedResponse(text)
      this.configurePlayerViewFromState(state)
    })

    /*
     * If the manifest can be parsed, parse it and update the player. Otherwise fail loudly.
     */

    if (response.isSuccessful) {
      val stream = response.body().byteStream()
      stream.use { _ ->
        val result = PlayerManifests.parse(stream)
        when (result) {
          is PlayerResult.Success -> {
            this.onProcessManifest(result.result)
          }
          is PlayerResult.Failure -> {
            ErrorDialogUtilities.showErrorWithRunnable(
              this@PlayerActivity,
              this.log,
              "Failed to parse manifest",
              result.failure,
              this.GO_BACK_TO_INITIAL_ACTIVITY)
          }
        }
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

  /*
   * A manifest has been parsed. Now give it to the API to get an audio engine for playback.
   */

  private fun onProcessManifest(manifest: PlayerManifest) {
    this.log.debug("onProcessManifest")

    /*
     * Ask the API for the best audio engine available that can handle the given manifest.
     */

    val engine = PlayerAudioEngines.findBestFor(
      PlayerAudioEngineRequest(
        manifest = manifest,
        filter = { true },
        downloadProvider = ExampleDownloadProvider(this.downloadExecutor)))

    if (engine == null) {
      ErrorDialogUtilities.showErrorWithRunnable(
        this@PlayerActivity,
        this.log,
        "No audio engine available to handle the given book",
        null,
        this.GO_BACK_TO_INITIAL_ACTIVITY)
      return
    }

    /*
     * Create the audio book.
     */

    val bookResult = engine.bookProvider.create(this)
    if (bookResult is PlayerResult.Failure) {
      ErrorDialogUtilities.showErrorWithRunnable(
        this@PlayerActivity,
        this.log,
        "Error parsing manifest",
        bookResult.failure,
        this.GO_BACK_TO_INITIAL_ACTIVITY)
      return
    }

    val book = (bookResult as PlayerResult.Success).result

    /*
     * Subscribe to spine element status updates.
     */

    this.spineElementSubscription = book.spineElementDownloadStatus.subscribe(
      { _ -> this.onSpineElementStatusChanged() },
      { error -> this.onSpineElementStatusError(error!!) })

    val player = book.createPlayer()

    this.playerEventSubscription = player.events.subscribe(
      { event -> this.onPlayerEvent(event) },
      { error -> this.onPlayerError(error) })

    /*
     * Configure the view state.
     */

    UIThread.runOnUIThread(Runnable {
      this.state = PlayerStateConfigured(manifest, book, player)
      this.configurePlayerViewFromState(this.state)
    })

    this.log.debug("onProcessManifest: finished")

    /*
     * The book has been opened, status updates have been issued for all of the parts. Now
     * tell the UI that everything has been updated.
     */

    this.onSpineElementStatusChanged()
  }

  private fun onPlayerError(error: Throwable) {
    this.log.error("onPlayerError: ", error)
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    this.log.debug("onPlayerEvent: {}", event)

    return when (event) {
      is PlayerEventChapterCompleted -> this.onPlayerEventChapterCompleted(event)
      is PlayerEventChapterWaiting -> this.onPlayerEventChapterWaiting(event)
      is PlayerEventPlaybackBuffering -> this.onPlayerEventPlaybackBuffering(event)
      is PlayerEventPlaybackPaused -> this.onPlayerEventPlaybackPaused(event)
      is PlayerEventPlaybackProgressUpdate -> this.onPlayerEventProgressUpdate(event)
      is PlayerEventPlaybackStarted -> this.onPlayerEventPlaybackStarted(event)
      is PlayerEventPlaybackStopped -> this.onPlayerEventPlaybackStopped(event)
    }
  }

  private fun onPlayerEventChapterWaiting(event: PlayerEventChapterWaiting) {
    this.log.debug("onPlayerEventChapterWaiting")

    /*
     * XXX: Not sure what the UI should display here. For now, it just acts as if the track
     * is playing, but it obviously isn't.
     */

    UIThread.runOnUIThread(Runnable {
      this.playerTocAdapter.setItemPlayingStatus(PlayerSpineElementPlaying(event.spineElement))
      this.onConfigurePlayerControls(event.spineElement, 0, playing = true)
      this.onSpineElementStatusChanged()
    })
  }

  private fun onPlayerEventPlaybackBuffering(event: PlayerEvent.PlayerEventPlaybackBuffering) {
    this.log.debug("onPlayerEventPlaybackBuffering")

    UIThread.runOnUIThread(Runnable {
      this.playerTocAdapter.setItemPlayingStatus(PlayerSpineElementBuffering(event.spineElement))
      this.onConfigurePlayerControls(event.spineElement, event.offsetMilliseconds, playing = true)
      this.onSpineElementStatusChanged()
    })
  }

  private fun onPlayerEventPlaybackPaused(event: PlayerEventPlaybackPaused) {
    this.log.debug("onPlayerEventPlaybackPaused")

    UIThread.runOnUIThread(Runnable {
      this.playerTocAdapter.setItemPlayingStatus(PlayerSpineElementPaused(event.spineElement))
      this.onConfigurePlayerControls(event.spineElement, event.offsetMilliseconds, playing = false)
      this.onSpineElementStatusChanged()
    })
  }

  private fun onPlayerEventPlaybackStarted(event: PlayerEventPlaybackStarted) {
    this.log.debug("onPlayerEventPlaybackStarted")

    UIThread.runOnUIThread(Runnable {
      this.playerTocAdapter.setItemPlayingStatus(PlayerSpineElementPlaying(event.spineElement))
      this.onConfigurePlayerControls(event.spineElement, event.offsetMilliseconds, playing = true)
      this.onSpineElementStatusChanged()
    })
  }

  private fun onPlayerEventProgressUpdate(event: PlayerEventPlaybackProgressUpdate) {
    this.log.debug("onPlayerEventProgressUpdate")

    UIThread.runOnUIThread(Runnable {
      this.playerTocAdapter.setItemPlayingStatus(PlayerSpineElementPlaying(event.spineElement))
      this.onSpineElementStatusChanged()
      this.onConfigurePlayerControls(event.spineElement, event.offsetMilliseconds, playing = true)
    })
  }

  private fun onPlayerEventChapterCompleted(event: PlayerEventChapterCompleted) {
    this.log.debug("onPlayerEventChapterCompleted")

    UIThread.runOnUIThread(Runnable {
      this.onChapterButtonsConfigure(event.spineElement)
      this.onSpineElementStatusChanged()
    })
  }

  private fun onPlayerEventPlaybackStopped(event: PlayerEventPlaybackStopped) {
    this.log.debug("onPlayerEventPlaybackStopped")

    UIThread.runOnUIThread(Runnable {
      this.playerTocAdapter.setItemPlayingStatus(PlayerSpineElementNotPlaying)
      this.onConfigurePlayerControls(event.spineElement, event.offsetMilliseconds, playing = false)
      this.onSpineElementStatusChanged()
    })
  }

  private fun onConfigurePlayerControls(
    spineElement: PlayerSpineElementType,
    offsetMilliseconds: Int,
    playing: Boolean) {

    if (playing) {
      this.playerPlay.setText(R.string.player_pause)
    } else {
      this.playerPlay.setText(R.string.player_play)
    }

    this.playerTime.text = this.hmsTextFromMilliseconds(offsetMilliseconds)
    this.playerTimeMaximum.text = this.hmsTextFromDuration(spineElement.duration)

    this.playerBar.max =
      spineElement.duration.standardSeconds.toInt()
    this.playerBar.progress =
      TimeUnit.MILLISECONDS.toSeconds(offsetMilliseconds.toLong()).toInt()

    this.onChapterButtonsConfigure(spineElement)
  }

  private fun hmsTextFromMilliseconds(milliseconds: Int): String {
    return this.periodFormatter.print(Duration.millis(milliseconds.toLong()).toPeriod())
  }

  private fun hmsTextFromDuration(duration: Duration): String {
    return this.periodFormatter.print(duration.toPeriod())
  }

  private fun onChapterButtonsConfigure(spineElement: PlayerSpineElementType) {
    UIThread.checkIsUIThread()

    this.playerSkipBackward.isClickable = false
    this.playerSkipForward.isClickable = false

    if (spineElement.index > 0) {
      this.playerSkipBackward.isClickable = true
    }
    if (spineElement.index < spineElement.book.spine.size - 1) {
      this.playerSkipForward.isClickable = true
    }
  }

  private fun onSpineElementStatusError(event: Throwable) {
    this.log.error("onSpineElementStatusError: ", event)
  }

  private fun onSpineElementStatusChanged() {

    /*
     * Notify the table of contents that the contents of the list it is displaying has changed.
     * This will cause it to inspect the states of each spine element and will, as a result, display
     * the correct states onscreen.
     */

    UIThread.runOnUIThread(Runnable {
      this.playerTocAdapter.notifyDataSetChanged()
    })
  }

  private fun onURIFetchFailure(e: IOException?) {
    ErrorDialogUtilities.showErrorWithRunnable(
      this@PlayerActivity,
      this.log,
      "Failed to fetch URI",
      e,
      this.GO_BACK_TO_INITIAL_ACTIVITY)
  }

  /**
   * An array adapter for instantiating views for spine elements.
   */

  private class PlayerSpineElementArrayAdapter(
    private val log: Logger,
    private val context: Activity,
    private val items: List<PlayerSpineElementType>)
    : ArrayAdapter<PlayerSpineElementType>(context, R.layout.player_toc_entry, items) {

    override fun getView(
      position: Int,
      reuse: View?,
      parent: ViewGroup): View {

      val item = this.items.get(position)
      val view = reuse as PlayerSpineElementView?
        ?: PlayerSpineElementView(this.log, this.context, null)
      view.viewConfigure(
        item = item,
        selected = position == this.itemSelected,
        itemPlayingStatus = this.itemPlaying)
      return view
    }

    private var itemSelected: Int = -1
    private var itemPlaying: PlayerSpineElementPlayingStatus = PlayerSpineElementNotPlaying

    fun setItemSelected(position: Int) {
      this.itemSelected = position
    }

    fun setItemPlayingStatus(status: PlayerSpineElementPlayingStatus) {
      this.itemPlaying = status
    }
  }

  private sealed class PlayerSpineElementPlayingStatus {

    object PlayerSpineElementNotPlaying
      : PlayerSpineElementPlayingStatus()

    data class PlayerSpineElementPlaying(
      val element: PlayerSpineElementType)
      : PlayerSpineElementPlayingStatus()

    data class PlayerSpineElementBuffering(
      val element: PlayerSpineElementType)
      : PlayerSpineElementPlayingStatus()

    data class PlayerSpineElementPaused(
      val element: PlayerSpineElementType)
      : PlayerSpineElementPlayingStatus()

  }

  /**
   * A reconfigurable view that shows the status of a spine element.
   */

  private class PlayerSpineElementView(
    private val log: Logger,
    val context: Activity,
    attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private var viewGroup: ViewGroup
    private var viewGroupDownloadStatus: ImageView
    private var viewGroupPlayStatus: ImageView
    private var viewGroupTitle: TextView
    private var viewGroupOperation: ImageView

    private lateinit var item: PlayerSpineElementType

    private val colorPlaying: Int
    private val colorNeutral: Int
    private val colorSelected: Int

    init {
      this.context.layoutInflater.inflate(R.layout.player_toc_entry, this, true)

      this.viewGroup =
        this.findViewById(R.id.player_toc_entry)
      this.viewGroupTitle =
        this.viewGroup.findViewById(R.id.player_toc_entry_title)
      this.viewGroupDownloadStatus =
        this.viewGroup.findViewById(R.id.player_toc_entry_download_status)
      this.viewGroupPlayStatus =
        this.viewGroup.findViewById(R.id.player_toc_entry_play_status)
      this.viewGroupOperation =
        this.viewGroup.findViewById(R.id.player_toc_entry_op)

      this.colorPlaying = this.context.resources.getColor(R.color.background_playing)
      this.colorNeutral = this.context.resources.getColor(R.color.background_neutral)
      this.colorSelected = this.context.resources.getColor(R.color.background_selected)
    }

    fun viewConfigure(
      item: PlayerSpineElementType,
      selected: Boolean,
      itemPlayingStatus: PlayerSpineElementPlayingStatus) {
      UIThread.checkIsUIThread()

      this.item = item
      this.viewGroupTitle.text =
        String.format("%03d.    %s", item.index, item.title)

      this.viewGroup.setBackgroundColor(this.colorNeutral)
      this.viewGroupTitle.setTypeface(null, Typeface.NORMAL)
      this.viewGroupPlayStatus.visibility = View.INVISIBLE

      when (item.downloadStatus) {
        is PlayerSpineElementNotDownloaded -> {
          this.viewGroupDownloadStatus.setImageResource(R.drawable.empty)
          this.viewGroupOperation.setImageResource(R.drawable.download)
          this.viewGroupOperation.setOnClickListener({ item.downloadTask.fetch() })
        }

        is PlayerSpineElementDownloadStatus.PlayerSpineElementDownloading -> {
          this.viewGroupDownloadStatus.setImageResource(R.drawable.download)
          this.viewGroupOperation.setImageResource(R.drawable.stop)
          this.viewGroupOperation.setOnClickListener({ this.showDownloadCancelConfirm(item) })
        }

        is PlayerSpineElementDownloaded -> {
          this.viewGroupDownloadStatus.setImageResource(R.drawable.book)
          this.viewGroupOperation.setImageResource(R.drawable.delete)
          this.viewGroupOperation.setOnClickListener({ this.showDeleteConfirm(item) })
        }

        is PlayerSpineElementDownloadStatus.PlayerSpineElementDownloadFailed -> {
          this.viewGroupDownloadStatus.setImageResource(R.drawable.error)
          this.viewGroupOperation.setImageResource(R.drawable.reload)
          this.viewGroupOperation.setOnClickListener({ item.downloadTask.delete() })
        }
      }

      if (selected) {
        this.viewGroup.setBackgroundColor(this.colorSelected)
      }

      /*
       * Configure the UI components based on the status of the playing item. Note that the
       * playing item may not be the currently configured item.
       */

      when (itemPlayingStatus) {
        PlayerSpineElementNotPlaying -> {

        }

        is PlayerSpineElementPlayingStatus.PlayerSpineElementPlaying -> {
          if (itemPlayingStatus.element == item) {
            this.viewGroup.setBackgroundColor(this.colorPlaying)
            this.viewGroupPlayStatus.visibility = View.VISIBLE
            this.viewGroupPlayStatus.setImageResource(R.drawable.pause)
            this.viewGroupTitle.setTypeface(null, Typeface.BOLD)
          }
        }

        is PlayerSpineElementPlayingStatus.PlayerSpineElementBuffering -> {
          if (itemPlayingStatus.element == item) {
            this.viewGroup.setBackgroundColor(this.colorPlaying)
            this.viewGroupPlayStatus.visibility = View.VISIBLE
            this.viewGroupPlayStatus.setImageResource(R.drawable.buffering)
            this.viewGroupTitle.setTypeface(null, Typeface.BOLD)
          }
        }

        is PlayerSpineElementPlayingStatus.PlayerSpineElementPaused -> {
          if (itemPlayingStatus.element == item) {
            this.viewGroup.setBackgroundColor(this.colorPlaying)
            this.viewGroupPlayStatus.visibility = View.VISIBLE
            this.viewGroupPlayStatus.setImageResource(R.drawable.playing)
            this.viewGroupTitle.setTypeface(null, Typeface.BOLD)
          }
        }
      }
    }

    private fun showDeleteConfirm(item: PlayerSpineElementType) {
      this.log.debug("asking for part deletion confirmation")

      val dialog =
        AlertDialog.Builder(this.context)
          .setCancelable(true)
          .setMessage(R.string.book_delete_confirm)
          .setPositiveButton(
            R.string.book_delete,
            { _: DialogInterface, _: Int -> item.downloadTask.delete() })
          .setNegativeButton(
            R.string.book_delete_keep,
            { _: DialogInterface, _: Int -> })
          .create()
      dialog.show()
    }

    private fun showDownloadCancelConfirm(item: PlayerSpineElementType) {
      this.log.debug("asking for download cancellation confirmation")

      val dialog =
        AlertDialog.Builder(this.context)
          .setCancelable(true)
          .setMessage(R.string.book_download_stop_confirm)
          .setPositiveButton(
            R.string.book_download_stop,
            { _: DialogInterface, _: Int -> item.downloadTask.delete() })
          .setNegativeButton(
            R.string.book_download_continue,
            { _: DialogInterface, _: Int -> })
          .create()
      dialog.show()
    }
  }

  private fun configurePlayerViewFromState(state: PlayerState) {
    UIThread.checkIsUIThread()

    when (state) {
      is PlayerStateWaitingForManifest -> {
        this.fetchView.visibility = View.VISIBLE
        this.playerView.visibility = View.GONE
        this.setContentView(this.fetchView)
        this.fetchText.text = state.message
      }

      is PlayerStateReceivedResponse -> {
        this.fetchView.visibility = View.VISIBLE
        this.playerView.visibility = View.GONE
        this.setContentView(this.fetchView)
        this.fetchText.text = state.message
      }

      is PlayerStateReceivedManifest -> {
        this.fetchView.visibility = View.VISIBLE
        this.playerView.visibility = View.GONE
        this.setContentView(this.fetchView)
        this.fetchText.text = state.message
      }

      is PlayerStateConfigured -> {
        this.fetchView.visibility = View.GONE
        this.playerView.visibility = View.VISIBLE
        this.setContentView(this.playerView)
        this.playerTitle.text = state.manifest.metadata.title
        this.playerTocAdapter = PlayerSpineElementArrayAdapter(this.log, this, state.book.spine)
        this.playerToc.adapter = this.playerTocAdapter

        /*
         * Configure a double-click listener in order to switch to spine items.
         */

        this.playerToc.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        this.playerToc.onItemClickListener = object : AdapterView.OnItemClickListener {
          private var lastPosition = -1
          private var lastTime = 0L

          override fun onItemClick(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long) {

            val timeNow = SystemClock.elapsedRealtime()
            val timeDelta = timeNow - this.lastTime

            this@PlayerActivity.log.debug(
              "clicked: {} at {} (diff {})", position, timeNow, timeDelta)

            if (position == this.lastPosition && timeDelta < 250L) {
              val item = state.book.spine[position]
              this@PlayerActivity.log.debug("clicked: triggering item {}", item.index)
              state.player.playAtLocation(item.position)
            }

            this.lastPosition = position
            this.lastTime = timeNow
            this@PlayerActivity.onSpineElementStatusChanged()
          }
        }

        this.playerSkipForward.setOnClickListener({
          state.player.skipForward()
        })

        this.playerSkipBackward.setOnClickListener({
          state.player.skipBack()
        })

        this.playerSkipNext.setOnClickListener({
          state.player.skipToNextChapter()
        })

        this.playerSkipPrevious.setOnClickListener({
          state.player.skipToPreviousChapter()
        })

        this.playerPlay.setOnClickListener({
          val player = state.player
          if (player.isPlaying) {
            player.pause()
          } else {
            player.play()
          }
        })

        state.player.events.subscribe(
          { event -> this.onPlayerEvent(event!!) },
          { error -> this.onPlayerError(error!!) })
      }
    }
  }
}
