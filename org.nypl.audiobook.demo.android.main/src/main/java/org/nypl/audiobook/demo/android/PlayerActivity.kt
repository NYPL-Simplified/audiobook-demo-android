package org.nypl.audiobook.demo.android

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.telecom.VideoProfile.isPaused
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
import android.widget.SeekBar
import android.widget.TextView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.joda.time.Duration
import org.joda.time.format.PeriodFormatter
import org.joda.time.format.PeriodFormatterBuilder
import org.nypl.audiobook.demo.android.PlayerActivity.PlayerSpineElementPlayingStatus.*
import org.nypl.audiobook.demo.android.PlayerActivity.PlayerState.PlayerStateConfigured
import org.nypl.audiobook.demo.android.PlayerActivity.PlayerState.PlayerStateReceivedManifest
import org.nypl.audiobook.demo.android.PlayerActivity.PlayerState.PlayerStateReceivedResponse
import org.nypl.audiobook.demo.android.PlayerActivity.PlayerState.PlayerStateWaitingForManifest
import org.nypl.audiobook.demo.android.api.PlayerAudioBookType
import org.nypl.audiobook.demo.android.api.PlayerEvent
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventChapterCompleted
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventPlaybackBuffering
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventPlaybackPaused
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventPlaybackProgressUpdate
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventPlaybackStarted
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventPlaybackStopped
import org.nypl.audiobook.demo.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloadFailed
import org.nypl.audiobook.demo.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloaded
import org.nypl.audiobook.demo.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloading
import org.nypl.audiobook.demo.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementNotDownloaded
import org.nypl.audiobook.demo.android.api.PlayerSpineElementType
import org.nypl.audiobook.demo.android.findaway.PlayerFindawayAudioBook
import org.nypl.audiobook.demo.android.findaway.PlayerFindawayManifest
import org.nypl.audiobook.demo.android.main.R
import org.slf4j.LoggerFactory
import rx.Subscription
import java.io.IOException
import java.util.concurrent.TimeUnit

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
  private lateinit var player_time: TextView
  private lateinit var player_time_maximum: TextView
  private lateinit var player_bar: ProgressBar

  private lateinit var state: PlayerState
  private var spine_element_subscription: Subscription? = null
  private var player_event_subscription: Subscription? = null

  override fun onCreate(state: Bundle?) {
    this.log.debug("onCreate")

    super.onCreate(state)

    this.state = PlayerStateWaitingForManifest(
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
    this.player_time =
      this.player_view.findViewById(R.id.player_time)
    this.player_time_maximum =
      this.player_view.findViewById(R.id.player_time_maximum)
    this.player_bar =
      this.player_view.findViewById(R.id.player_seek_bar)

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
      is PlayerStateWaitingForManifest -> {
        this.fetch_view.visibility = View.VISIBLE
        this.player_view.visibility = View.GONE
        this.setContentView(this.fetch_view)
        this.fetch_text.text = state.message
      }

      is PlayerStateReceivedResponse -> {
        this.fetch_view.visibility = View.VISIBLE
        this.player_view.visibility = View.GONE
        this.setContentView(this.fetch_view)
        this.fetch_text.text = state.message
      }

      is PlayerStateReceivedManifest -> {
        this.fetch_view.visibility = View.VISIBLE
        this.player_view.visibility = View.GONE
        this.setContentView(this.fetch_view)
        this.fetch_text.text = state.message
      }

      is PlayerStateConfigured -> {
        this.fetch_view.visibility = View.GONE
        this.player_view.visibility = View.VISIBLE
        this.setContentView(this.player_view)
        this.player_title.text = state.book.title
        this.player_toc_adapter = PlayerSpineElementArrayAdapter(this, state.book.spine)
        this.player_toc.adapter = this.player_toc_adapter

        /*
         * Configure a double-click listener in order to switch to spine items.
         */

        this.player_toc.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        this.player_toc.onItemClickListener = object: AdapterView.OnItemClickListener{
          private var last_position = -1
          private var last_time = 0L

          override fun onItemClick(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long) {

            val time_now = SystemClock.elapsedRealtime()
            val time_diff = time_now - this.last_time

            this@PlayerActivity.log.debug(
              "clicked: {} at {} (diff {})", position, time_now, time_diff)

            if (position == this.last_position && time_diff < 250L) {
              val item = state.book.spine[position]
              this@PlayerActivity.log.debug("clicked: triggering item {}", item.index)
              state.book.player.playAtLocation(item.position)
            }

            this.last_position = position
            this.last_time = time_now
            this@PlayerActivity.onSpineElementStatusChanged()
          }
        }

        this.player_skip_forward.setOnClickListener({
          state.book.player.skipToNextChapter()
        })

        this.player_skip_backward.setOnClickListener({
          state.book.player.skipToPreviousChapter()
        })

        this.player_play.setOnClickListener({
          val player = state.book.player
          if (player.isPlaying) {
            player.pause()
          } else {
            player.play()
          }
        })

        state.book.player.events.subscribe(
          { event -> this.onPlayerEvent(event!!) },
          { error -> this.onPlayerError(error!!) })
      }
    }
  }

  private fun onPlayerError(error: Throwable) {
    this.log.error("onPlayerError: ", error)
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    this.log.debug("onPlayerEvent: {}", event)

    return when (event) {
      is PlayerEventPlaybackStarted -> this.onPlayerEventPlaybackStarted(event)
      is PlayerEventPlaybackProgressUpdate -> this.onPlayerEventProgressUpdate(event)
      is PlayerEventChapterCompleted -> this.onPlayerEventChapterCompleted(event)
      is PlayerEventPlaybackStopped -> this.onPlayerEventPlaybackStopped(event)
      is PlayerEventPlaybackPaused -> this.onPlayerEventPlaybackPaused(event)
      is PlayerEventPlaybackBuffering -> this.onPlayerEventPlaybackBuffering(event)
    }
  }

  private fun onPlayerEventPlaybackBuffering(event: PlayerEventPlaybackBuffering) {
    this.log.debug("onPlayerEventPlaybackBuffering")

    UIThread.runOnUIThread(Runnable {
      this.player_toc_adapter.setItemPlayingStatus(PlayerSpineElementBuffering(event.spineElement))
      this.onConfigurePlayerControls(event.spineElement, event.offsetMilliseconds, playing = true)
      this.onSpineElementStatusChanged()
    })
  }

  private fun onPlayerEventPlaybackPaused(event: PlayerEventPlaybackPaused) {
    this.log.debug("onPlayerEventPlaybackPaused")

    UIThread.runOnUIThread(Runnable {
      this.player_toc_adapter.setItemPlayingStatus(PlayerSpineElementPaused(event.spineElement))
      this.onConfigurePlayerControls(event.spineElement, event.offsetMilliseconds, playing = false)
      this.onSpineElementStatusChanged()
    })
  }

  private fun onPlayerEventPlaybackStarted(event: PlayerEventPlaybackStarted) {
    this.log.debug("onPlayerEventPlaybackStarted")

    UIThread.runOnUIThread(Runnable {
      this.player_toc_adapter.setItemPlayingStatus(PlayerSpineElementPlaying(event.spineElement))
      this.onConfigurePlayerControls(event.spineElement, event.offsetMilliseconds, playing = true)
      this.onSpineElementStatusChanged()
    })
  }

  private fun onPlayerEventProgressUpdate(event: PlayerEventPlaybackProgressUpdate) {
    this.log.debug("onPlayerEventProgressUpdate")

    UIThread.runOnUIThread(Runnable {
      this.player_toc_adapter.setItemPlayingStatus(PlayerSpineElementPlaying(event.spineElement))
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
      this.player_toc_adapter.setItemPlayingStatus(PlayerSpineElementNotPlaying)
      this.onConfigurePlayerControls(event.spineElement, event.offsetMilliseconds, playing = false)
      this.onSpineElementStatusChanged()
    })
  }

  private fun onConfigurePlayerControls(
    spineElement: PlayerSpineElementType,
    offsetMilliseconds: Int,
    playing: Boolean) {

    if (playing) {
      this.player_play.setText(R.string.player_pause)
    } else {
      this.player_play.setText(R.string.player_play)
    }

    this.player_time.setText(this.hmsTextFromMilliseconds(offsetMilliseconds))
    this.player_time_maximum.setText(this.hmsTextFromDuration(spineElement.duration))

    this.player_bar.max =
      spineElement.duration.standardSeconds.toInt()
    this.player_bar.progress =
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

    this.player_skip_backward.isClickable = false
    this.player_skip_forward.isClickable = false

    if (spineElement.index > 0) {
      this.player_skip_backward.isClickable = true
    }
    if (spineElement.index < spineElement.book.spine.size - 1) {
      this.player_skip_forward.isClickable = true
    }
  }

  /**
   * A reconfigurable view that shows the downloadStatus of a spine element.
   */

  private class PlayerSpineElementView(
    val context: Activity,
    attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private var view_group: ViewGroup
    private var view_group_download_status: ImageView
    private var view_group_play_status: ImageView
    private var view_group_title: TextView
    private var view_group_operation: ImageView

    private lateinit var item: PlayerSpineElementType

    private val color_playing: Int
    private val color_neutral: Int
    private val color_selected: Int

    init {
      this.context.layoutInflater.inflate(R.layout.player_toc_entry, this, true)

      this.view_group =
        this.findViewById(R.id.player_toc_entry)
      this.view_group_title =
        this.view_group.findViewById(R.id.player_toc_entry_title)
      this.view_group_download_status =
        this.view_group.findViewById(R.id.player_toc_entry_download_status)
      this.view_group_play_status =
        this.view_group.findViewById(R.id.player_toc_entry_play_status)
      this.view_group_operation =
        this.view_group.findViewById(R.id.player_toc_entry_op)

      this.color_playing = this.context.resources.getColor(R.color.background_playing)
      this.color_neutral = this.context.resources.getColor(R.color.background_neutral)
      this.color_selected = this.context.resources.getColor(R.color.background_selected)
    }

    fun viewConfigure(
      item: PlayerSpineElementType,
      selected: Boolean,
      item_playing_status: PlayerSpineElementPlayingStatus) {
      UIThread.checkIsUIThread()

      this.item = item
      this.view_group_title.text =
        String.format("%03d.    %s", item.index, item.title)

      this.view_group.setBackgroundColor(this.color_neutral)
      this.view_group_title.setTypeface(null, Typeface.NORMAL)
      this.view_group_play_status.visibility = View.INVISIBLE

      when (item.downloadStatus) {
        PlayerSpineElementNotDownloaded -> {
          this.view_group_download_status.setImageResource(R.drawable.empty)
          this.view_group_operation.setImageResource(R.drawable.download)
          this.view_group_operation.setOnClickListener({ item.downloadTask.fetch() })
        }

        is PlayerSpineElementDownloading -> {
          this.view_group_download_status.setImageResource(R.drawable.download)
          this.view_group_operation.setImageResource(R.drawable.stop)
          this.view_group_operation.setOnClickListener({ this.showDownloadCancelConfirm(item) })
        }

        PlayerSpineElementDownloaded -> {
          this.view_group_download_status.setImageResource(R.drawable.book)
          this.view_group_operation.setImageResource(R.drawable.delete)
          this.view_group_operation.setOnClickListener({ this.showDeleteConfirm(item) })
        }

        is PlayerSpineElementDownloadFailed -> {
          this.view_group_download_status.setImageResource(R.drawable.error)
          this.view_group_operation.setImageResource(R.drawable.reload)
          this.view_group_operation.setOnClickListener({ item.downloadTask.delete() })
        }
      }

      if (selected) {
        this.view_group.setBackgroundColor(this.color_selected)
      }

      /*
       * Configure the UI components based on the status of the playing item. Note that the
       * playing item may not be the currently configured item.
       */

      when (item_playing_status) {
        PlayerSpineElementNotPlaying -> {

        }

        is PlayerSpineElementPlaying -> {
          if (item_playing_status.element == item) {
            this.view_group.setBackgroundColor(this.color_playing)
            this.view_group_play_status.visibility = View.VISIBLE
            this.view_group_play_status.setImageResource(R.drawable.pause)
            this.view_group_title.setTypeface(null, Typeface.BOLD)
          }
        }

        is PlayerSpineElementBuffering -> {
          if (item_playing_status.element == item) {
            this.view_group.setBackgroundColor(this.color_playing)
            this.view_group_play_status.visibility = View.VISIBLE
            this.view_group_play_status.setImageResource(R.drawable.buffering)
            this.view_group_title.setTypeface(null, Typeface.BOLD)
          }
        }

        is PlayerSpineElementPaused -> {
          if (item_playing_status.element == item) {
            this.view_group.setBackgroundColor(this.color_playing)
            this.view_group_play_status.visibility = View.VISIBLE
            this.view_group_play_status.setImageResource(R.drawable.playing)
            this.view_group_title.setTypeface(null, Typeface.BOLD)
          }
        }
      }
    }

    private fun showDeleteConfirm(item: PlayerSpineElementType) {
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

  private sealed class PlayerSpineElementPlayingStatus {

    object PlayerSpineElementNotPlaying
      : PlayerSpineElementPlayingStatus()

    data class PlayerSpineElementPlaying(
      val element : PlayerSpineElementType)
      : PlayerSpineElementPlayingStatus()

    data class PlayerSpineElementBuffering(
      val element : PlayerSpineElementType)
      : PlayerSpineElementPlayingStatus()

    data class PlayerSpineElementPaused(
      val element : PlayerSpineElementType)
      : PlayerSpineElementPlayingStatus()

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
      view.viewConfigure(
        item = item,
        selected = position == this.item_selected,
        item_playing_status = this.item_playing)
      return view
    }

    private var item_selected: Int = -1
    private var item_playing: PlayerSpineElementPlayingStatus = PlayerSpineElementNotPlaying

    fun setItemSelected(position: Int) {
      this.item_selected = position
    }

    fun setItemPlayingStatus(status: PlayerSpineElementPlayingStatus) {
      this.item_playing = status
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
      val state = PlayerStateReceivedResponse(text)
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
      this.state = PlayerStateReceivedManifest(
        this.getString(R.string.fetch_received_other_manifest), manifest)
      this.configurePlayerViewFromState(this.state)
    })
  }

  private fun onProcessManifestIsFindaway(manifest: RawManifest) {
    this.log.debug("onProcessManifestIsFindaway")

    UIThread.runOnUIThread(Runnable {
      this.state = PlayerStateReceivedManifest(
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
         * Create the audio book.
         */

        val book = PlayerFindawayAudioBook.create(this, manifest_result.result)

        /*
         * Subscribe to spine element downloadStatus updates.
         */

        this.spine_element_subscription = book.spineElementDownloadStatus.subscribe(
          { event -> this.onSpineElementStatusChanged() },
          { error -> this.onSpineElementStatusError(error!!) })
        this.player_event_subscription = book.player.events.subscribe(
          { event -> this.onPlayerEvent(event) },
          { error -> this.onPlayerError(error) })

        /*
         * Configure the view state.
         */

        UIThread.runOnUIThread(Runnable {
          this.state = PlayerStateConfigured(book)
          this.configurePlayerViewFromState(this.state)
        })

        this.log.debug("onProcessManifestIsFindaway: finished")

        /*
         * The book has been opened, downloadStatus updates have been issued for all of the parts. Now
         * tell the UI that everything has been updated.
         */

        this.onSpineElementStatusChanged()
      }
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
      this.player_toc_adapter.notifyDataSetChanged()
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
}
