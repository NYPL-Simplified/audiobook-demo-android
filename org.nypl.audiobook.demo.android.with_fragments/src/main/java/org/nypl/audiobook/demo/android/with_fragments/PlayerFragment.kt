package org.nypl.audiobook.demo.android.with_fragments

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import org.joda.time.Duration
import org.joda.time.format.PeriodFormatter
import org.joda.time.format.PeriodFormatterBuilder
import org.nypl.audiobook.android.api.PlayerAudioBookType
import org.nypl.audiobook.android.api.PlayerEvent
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventChapterCompleted
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventChapterWaiting
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventPlaybackBuffering
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventPlaybackPaused
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventPlaybackProgressUpdate
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventPlaybackStarted
import org.nypl.audiobook.android.api.PlayerEvent.PlayerEventPlaybackStopped
import org.nypl.audiobook.android.api.PlayerSpineElementType
import org.nypl.audiobook.android.api.PlayerType
import org.slf4j.LoggerFactory
import rx.Subscription
import java.util.concurrent.TimeUnit

class PlayerFragment : android.support.v4.app.Fragment() {

  companion object {
    @JvmStatic
    fun newInstance(parameters: PlayerFragmentParameters): PlayerFragment {
      val args = Bundle()
      args.putSerializable("org.nypl.audiobook.demo.android.with_fragments.parameters", parameters)
      val fragment = PlayerFragment()
      fragment.arguments = args
      return fragment
    }
  }

  private lateinit var listener: PlayerFragmentListenerType
  private lateinit var player: PlayerType
  private lateinit var book: PlayerAudioBookType
  private lateinit var coverView: ImageView
  private lateinit var playPauseButton: ImageView
  private lateinit var playerPosition: ProgressBar
  private lateinit var playerTimeCurrent: TextView
  private lateinit var playerTimeMaximum: TextView
  private lateinit var playerSpineElement: TextView
  private lateinit var menuPlaybackRate: MenuItem
  private lateinit var menuSleep: MenuItem
  private lateinit var menuTOC: MenuItem
  private lateinit var parameters: PlayerFragmentParameters

  private var playerEventMostRecent: PlayerEvent? = null
  private var playerEventSubscription: Subscription? = null
  private var viewsExist = false

  private val log = LoggerFactory.getLogger(PlayerFragment::class.java)

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

  override fun onCreate(state: Bundle?) {
    this.log.debug("onCreate")

    super.onCreate(state)

    this.parameters =
      this.arguments!!.getSerializable("org.nypl.audiobook.demo.android.with_fragments.parameters")
        as PlayerFragmentParameters

    /*
     * This fragment wants an options menu.
     */

    this.setHasOptionsMenu(true)
  }

  override fun onAttach(context: Context) {
    this.log.debug("onAttach")
    super.onAttach(context)

    if (context is PlayerFragmentListenerType) {
      this.listener = context
      this.player = this.listener.onPlayerWantsPlayer()
      this.book = this.listener.onPlayerTOCWantsBook()
    } else {
      throw ClassCastException(
        StringBuilder(64)
          .append("The activity hosting this fragment must implement one or more listener interfaces.\n")
          .append("  Activity: ")
          .append(context::class.java.canonicalName)
          .append('\n')
          .append("  Required interface: ")
          .append(PlayerFragmentListenerType::class.java.canonicalName)
          .append('\n')
          .toString())
    }
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    this.log.debug("onCreateOptionsMenu")

    super.onCreateOptionsMenu(menu, inflater)

    inflater.inflate(R.menu.player_menu, menu)

    this.menuPlaybackRate = menu.findItem(R.id.player_menu_playback_rate)
    this.menuPlaybackRate.actionView.setOnClickListener {
      this.onMenuPlaybackRateSelected()
    }

    this.menuSleep = menu.findItem(R.id.player_menu_sleep)
    this.menuSleep.setOnMenuItemClickListener {
      this.onMenuSleepSelected()
    }

    this.menuTOC = menu.findItem(R.id.player_menu_toc)
    this.menuTOC.setOnMenuItemClickListener {
      this.onMenuTOCSelected()
    }
  }

  private fun onMenuTOCSelected(): Boolean {
    this.listener.onPlayerTOCShouldOpen()
    return true
  }

  private fun onMenuSleepSelected(): Boolean {
    val dialog =
      AlertDialog.Builder(this.activity)
        .setCancelable(true)
        .setMessage("Not yet implemented!")
        .setNegativeButton(
          "OK",
          { _: DialogInterface, _: Int -> })
        .create()
    dialog.show()
    return true
  }

  private fun onMenuPlaybackRateSelected() {
    val dialog =
      AlertDialog.Builder(this.activity)
        .setCancelable(true)
        .setMessage("Not yet implemented!")
        .setNegativeButton(
          "OK",
          { _: DialogInterface, _: Int -> })
        .create()
    dialog.show()
  }

  override fun onDestroy() {
    this.log.debug("onDestroy")
    super.onDestroy()
    this.playerEventSubscription?.unsubscribe()
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    state: Bundle?): View? {
    this.log.debug("onCreateView")
    return inflater.inflate(R.layout.player_view, container, false)
  }

  override fun onDestroyView() {
    this.log.debug("onDestroyView")
    super.onDestroyView()
    this.viewsExist = false
  }

  override fun onViewCreated(view: View, state: Bundle?) {
    this.log.debug("onViewCreated")
    super.onViewCreated(view, state)

    this.viewsExist = true
    this.coverView = view.findViewById(R.id.player_cover)!!

    this.playPauseButton = view.findViewById(R.id.player_play_button)!!
    this.playPauseButton.setOnClickListener({ this.player.play() })

    this.playerPosition = view.findViewById(R.id.player_progress)!!
    this.playerTimeCurrent = view.findViewById(R.id.player_time)!!
    this.playerTimeMaximum = view.findViewById(R.id.player_time_maximum)!!
    this.playerSpineElement = view.findViewById(R.id.player_spine_element)!!
    this.playerSpineElement.text = this.spineElementText(this.book.spine.first())

    this.listener.onPlayerWantsCoverImage(this.coverView)

    this.playerEventSubscription =
      this.player.events.subscribe(
        { event -> this.onPlayerEvent(event) },
        { error -> this.onPlayerError(error) },
        { this.onPlayerEventsCompleted() })

    /*
     * The fragment will keep receiving events after the views are destroyed. The subscription
     * keeps track of the last event received so that the event can be replayed when the views
     * are recreated (this will happen when the fragment comes back to the foreground after being
     * on the back stack).
     *
     * Replaying the event is necessary because events come at a slow rate (one per second,
     * typically), and that's ample time for the user to see an out-of-date UI state before the
     * next event arrives.
     */

    val event = this.playerEventMostRecent
    if (event != null) {
      this.log.debug("replaying event {}", event)
      this.playerEventMostRecent = null
      this.onPlayerEvent(event)
    }
  }

  private fun hmsTextFromMilliseconds(milliseconds: Int): String {
    return this.periodFormatter.print(Duration.millis(milliseconds.toLong()).toPeriod())
  }

  private fun hmsTextFromDuration(duration: Duration): String {
    return this.periodFormatter.print(duration.toPeriod())
  }

  private fun onPlayerEventsCompleted() {

  }

  private fun onPlayerError(error: Throwable) {

  }

  private fun onPlayerEvent(event: PlayerEvent) {
    this.playerEventMostRecent = event

    return when (event) {
      is PlayerEventPlaybackStarted ->
        this.onPlayerEventPlaybackStarted(event)

      is PlayerEventPlaybackBuffering -> {

      }

      is PlayerEventPlaybackProgressUpdate ->
        this.onPlayerEventPlaybackProgressUpdate(event)

      is PlayerEventChapterCompleted -> {

      }

      is PlayerEventChapterWaiting -> {

      }

      is PlayerEventPlaybackPaused ->
        this.onPlayerEventPlaybackPaused(event)
      is PlayerEventPlaybackStopped ->
        this.onPlayerEventPlaybackStopped(event)
    }
  }

  private fun onPlayerEventPlaybackStopped(event: PlayerEventPlaybackStopped) {
    UIThread.runOnUIThread(Runnable {
      if (this.viewsExist) {
        this.playPauseButton.setImageResource(R.drawable.play_icon)
        this.playPauseButton.setOnClickListener({ this.player.play() })
        this.playerSpineElement.text = this.spineElementText(event.spineElement)
        this.onEventUpdateTimeRelatedUI(event.spineElement, event.offsetMilliseconds)
      }
    })
  }

  private fun spineElementText(spineElement: PlayerSpineElementType): String {
    return this.getString(
      R.string.player_spine_element,
      spineElement.index + 1,
      spineElement.book.spine.size)
  }

  private fun onPlayerEventPlaybackPaused(event: PlayerEventPlaybackPaused) {
    UIThread.runOnUIThread(Runnable {
      if (this.viewsExist) {
        this.playPauseButton.setImageResource(R.drawable.play_icon)
        this.playPauseButton.setOnClickListener({ this.player.play() })
        this.playerSpineElement.text = this.spineElementText(event.spineElement)
        this.onEventUpdateTimeRelatedUI(event.spineElement, event.offsetMilliseconds)
      }
    })
  }

  private fun onPlayerEventPlaybackProgressUpdate(event: PlayerEventPlaybackProgressUpdate) {
    UIThread.runOnUIThread(Runnable {
      if (this.viewsExist) {
        this.playPauseButton.setImageResource(R.drawable.pause_icon)
        this.playPauseButton.setOnClickListener({ this.player.pause() })
        this.onEventUpdateTimeRelatedUI(event.spineElement, event.offsetMilliseconds)
      }
    })
  }

  private fun onPlayerEventPlaybackStarted(event: PlayerEventPlaybackStarted) {
    UIThread.runOnUIThread(Runnable {
      if (this.viewsExist) {
        this.playPauseButton.setImageResource(R.drawable.pause_icon)
        this.playPauseButton.setOnClickListener({ this.player.pause() })
        this.playerSpineElement.text = this.spineElementText(event.spineElement)
        this.onEventUpdateTimeRelatedUI(event.spineElement, event.offsetMilliseconds)
      }
    })
  }

  private fun onEventUpdateTimeRelatedUI(
    spineElement: PlayerSpineElementType,
    offsetMilliseconds: Int) {
    this.playerPosition.max =
      spineElement.duration.standardSeconds.toInt()
    this.playerPosition.progress =
      TimeUnit.MILLISECONDS.toSeconds(offsetMilliseconds.toLong()).toInt()
    this.playerTimeMaximum.text =
      this.hmsTextFromDuration(spineElement.duration)
    this.playerTimeCurrent.text =
      this.hmsTextFromMilliseconds(offsetMilliseconds)
    this.playerSpineElement.text =
      this.spineElementText(spineElement)
  }
}
