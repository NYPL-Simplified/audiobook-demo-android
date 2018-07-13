package org.nypl.audiobook.demo.android.findaway

import io.audioengine.mobile.AudioEngine
import io.audioengine.mobile.PlaybackEvent
import io.audioengine.mobile.PlaybackEvent.CHAPTER_PLAYBACK_COMPLETED
import io.audioengine.mobile.PlaybackEvent.ERROR_PREPARING_PLAYER
import io.audioengine.mobile.PlaybackEvent.NO_CURRENT_CHAPTER
import io.audioengine.mobile.PlaybackEvent.NO_CURRENT_CONTENT
import io.audioengine.mobile.PlaybackEvent.PLAYBACK_BUFFERING_ENDED
import io.audioengine.mobile.PlaybackEvent.PLAYBACK_BUFFERING_STARTED
import io.audioengine.mobile.PlaybackEvent.PLAYBACK_ENDED
import io.audioengine.mobile.PlaybackEvent.PLAYBACK_PAUSED
import io.audioengine.mobile.PlaybackEvent.PLAYBACK_PREPARING
import io.audioengine.mobile.PlaybackEvent.PLAYBACK_PROGRESS_UPDATE
import io.audioengine.mobile.PlaybackEvent.PLAYBACK_STARTED
import io.audioengine.mobile.PlaybackEvent.PLAYBACK_STOPPED
import io.audioengine.mobile.PlaybackEvent.SEEK_COMPLETE
import io.audioengine.mobile.PlaybackEvent.UNABLE_TO_AQUIRE_AUDIO_FOCUS
import io.audioengine.mobile.PlaybackEvent.UNKNOWN_PLAYBACK_ERROR
import io.audioengine.mobile.play.PlayerState
import org.nypl.audiobook.demo.android.api.PlayerEvent
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventChapterCompleted
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventPlaybackBuffering
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventPlaybackPaused
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventPlaybackProgressUpdate
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventPlaybackStarted
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventPlaybackStopped
import org.nypl.audiobook.demo.android.api.PlayerPlaybackRate
import org.nypl.audiobook.demo.android.api.PlayerPosition
import org.nypl.audiobook.demo.android.api.PlayerType
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject

class PlayerFindaway(
  private val book: PlayerFindawayAudioBook,
  private val engine: AudioEngine)
  : PlayerType {

  override val isPlaying: Boolean
    get() = synchronized(this.stateLock, { this.state.isPlaying })

  override val events: Observable<PlayerEvent>
    get() = this.eventSource

  private val log = LoggerFactory.getLogger(PlayerFindaway::class.java)
  private val eventSource: PublishSubject<PlayerEvent> = PublishSubject.create()
  private var chapterCompleting: Boolean = false

  private val stateLock: Any = Object()
  @net.jcip.annotations.GuardedBy("stateLock")
  private val state: State

  /**
   * Internal player state. Access to the fields of this structure MUST be protected via
   * synchronization on the lock field.
   */

  private data class State(
    var rate: PlayerPlaybackRate = PlayerPlaybackRate.NORMAL_TIME,
    var isPlaying: Boolean,
    var spineItem: PlayerFindawaySpineElement,
    var playhead: PlayerPosition)

  /**
   * The "constructor" for the class.
   */

  init {
    this.state =
      State(
        rate = PlayerPlaybackRate.NORMAL_TIME,
        isPlaying = false,
        spineItem = this.findSpineItemInitial(this.book),
        playhead = this.findSpineItemInitial(this.book).position)

    this.engine.playbackEngine.events().subscribe(
      { event -> this.onPlaybackEvent(event) },
      { error -> this.onPlaybackError(error) })

    this.engine.playbackEngine.state.subscribe(
      { state -> this.onPlaybackState(state) },
      { error -> this.onPlaybackError(error) })
  }

  override var playbackRate: PlayerPlaybackRate
    get() =
      synchronized(this.stateLock, { this.state.rate })
    set(value) {
      synchronized(this.stateLock) {
        this.state.rate = value
      }
      this.engine.playbackEngine.speed = value.speed.toFloat()
    }

  /**
   * Find the book's initial spine item.
   */

  private fun findSpineItemInitial(book: PlayerFindawayAudioBook): PlayerFindawaySpineElement {
    if (book.spine.isNotEmpty()) {
      return book.spine[0]
    } else {
      throw IllegalStateException("Book has no spine items")
    }
  }

  private fun enginePlay(playhead: PlayerPosition) {
    this.log.debug("enginePlay: {}", playhead)
    this.engine.playbackEngine.play(
      this.book.manifest.licenseId,
      this.book.manifest.fulfillmentId,
      playhead.part,
      playhead.chapter,
      playhead.offsetMilliseconds)
  }

  private fun engineSeek(position: Long) {
    this.log.debug("engineSeek: {}", position)
    this.engine.playbackEngine.seekTo(position)
  }

  private fun enginePause() {
    this.log.debug("enginePause")
    this.engine.playbackEngine.pause()
  }

  private fun onPlaybackState(state: PlayerState?) {
    this.log.debug("onPlaybackState: {}: {}",
      synchronized(this.stateLock, { this.state.playhead }), state)
    this.updatePlayheadFromEngine()
  }

  private fun onPlaybackError(error: Throwable?) {
    if (error == null) {
      return;
    }

    this.log.error("onPlaybackError: ", error)
  }

  private fun onPlaybackEvent(event: PlaybackEvent?) {
    if (event == null) {
      return;
    }

    when (event.code) {
      PLAYBACK_STARTED -> this.onPlaybackEventPlaybackStarted()
      CHAPTER_PLAYBACK_COMPLETED -> this.onPlaybackEventChapterCompleted()
      PLAYBACK_ENDED -> this.onPlaybackEventPlaybackStopped()
      PLAYBACK_PAUSED -> this.onPlaybackEventPlaybackPaused()
      PLAYBACK_STOPPED -> this.onPlaybackEventPlaybackStopped()
      PLAYBACK_PREPARING -> this.onPlaybackEventPreparing()
      PLAYBACK_BUFFERING_STARTED -> this.onPlaybackEventBufferingStarted()
      PLAYBACK_BUFFERING_ENDED -> this.onPlaybackEventBufferingEnded()

      SEEK_COMPLETE -> {
        this.log.debug("onPlaybackEvent: seek complete")
      }

      PLAYBACK_PROGRESS_UPDATE -> this.onPlaybackEventPlaybackProgressUpdate()

      UNKNOWN_PLAYBACK_ERROR -> {
        this.log.debug("onPlaybackEvent: unknown playback error")
      }

      UNABLE_TO_AQUIRE_AUDIO_FOCUS -> {
        this.log.debug("onPlaybackEvent: unable to acquire audio focus")
      }

      ERROR_PREPARING_PLAYER -> {
        this.log.debug("onPlaybackEvent: error preparing player")
      }

      NO_CURRENT_CONTENT -> {
        this.log.debug("onPlaybackEvent: no current content")
      }

      NO_CURRENT_CHAPTER -> {
        this.log.debug("onPlaybackEvent: no current chapter")
      }

      else -> {
        this.log.debug("onPlaybackEvent: unrecognized playback event code: {}", event.code)
      }
    }
  }

  private fun onPlaybackEventPreparing() {
    this.log.debug("onPlaybackEvent: playback preparing")
  }

  private fun onPlaybackEventBufferingEnded() {
    this.log.debug("onPlaybackEvent: playback buffering ended")
  }

  private fun onPlaybackEventBufferingStarted() {
    this.log.debug("onPlaybackEvent: playback buffering started")

    val new_playhead = this.updatePlayheadFromEngine()

    this.eventSource.onNext(
      synchronized(this.stateLock, {
        PlayerEventPlaybackBuffering(
          spineElement = new_playhead.spineItem,
          offsetMilliseconds = new_playhead.position.offsetMilliseconds)
      }))
  }

  private fun onPlaybackEventPlaybackStarted() {
    this.log.debug("onPlaybackEventPlaybackStarted")

    val new_playhead = this.updatePlayheadFromEngine()

    this.eventSource.onNext(
      synchronized(this.stateLock, {
        PlayerEventPlaybackStarted(
          spineElement = new_playhead.spineItem,
          offsetMilliseconds = new_playhead.position.offsetMilliseconds)
      }))
  }

  private fun onPlaybackEventPlaybackPaused() {
    this.log.debug("onPlaybackEventPlaybackPaused")

    val new_playhead = this.updatePlayheadFromEngine()

    this.eventSource.onNext(
      synchronized(this.stateLock, {
        PlayerEventPlaybackPaused(
          spineElement = new_playhead.spineItem,
          offsetMilliseconds = new_playhead.position.offsetMilliseconds)
      }))
  }

  private fun onPlaybackEventPlaybackStopped() {
    this.log.debug("onPlaybackEventPlaybackStopped")

    val new_playhead = this.updatePlayheadFromEngine()
    this.chapterCompleting = false

    this.eventSource.onNext(
      synchronized(this.stateLock, {
        PlayerEventPlaybackStopped(
          spineElement = new_playhead.spineItem,
          offsetMilliseconds = new_playhead.position.offsetMilliseconds)
      }))
  }

  private fun onPlaybackEventPlaybackProgressUpdate() {
    this.log.debug("onPlaybackEventPlaybackProgressUpdate")

    val new_playhead = this.updatePlayheadFromEngine()
    this.chapterCompleting = false

    this.eventSource.onNext(
      synchronized(this.stateLock, {
        PlayerEventPlaybackProgressUpdate(
          spineElement = new_playhead.spineItem,
          offsetMilliseconds = new_playhead.position.offsetMilliseconds)
      }))
  }

  private fun onPlaybackEventChapterCompleted() {
    this.log.debug("onPlaybackEventChapterCompleted")

    if (!this.chapterCompleting) {
      this.eventSource.onNext(
        synchronized(this.stateLock, {
          PlayerEventChapterCompleted(
            spineElement = this.state.spineItem,
            offsetMilliseconds = this.state.playhead.offsetMilliseconds)
        }))
    }

    this.chapterCompleting = true
  }

  private data class SpineItemAndPosition(
    val spineItem: PlayerFindawaySpineElement,
    val position: PlayerPosition)

  /**
   * Pull the current playhead position from the playback engine.
   */

  private fun updatePlayheadFromEngine(): SpineItemAndPosition {
    val chapter = this.engine.playbackEngine.chapter
    if (chapter == null) {
      return synchronized(this.stateLock, {
        SpineItemAndPosition(
          spineItem = this.state.spineItem,
          position = this.state.playhead)
      })
    }

    val new_playhead = PlayerPosition(
      chapter.friendlyName(),
      chapter.part(),
      chapter.chapter(),
      this.engine.playbackEngine.position.toInt())

    val element =
      this.book.spineElementForPartAndChapter(
        part = chapter.part(),
        chapter = chapter.chapter())

    if (element == null) {
      throw IllegalStateException(
        "Could not find spine element for part ${chapter.chapter()} and part ${chapter.part()}")
    }

    val playing = this.engine.playbackEngine.isPlaying
    synchronized(this.stateLock, {
      this.state.playhead = new_playhead
      this.state.isPlaying = playing
    })

    this.log.trace("now at {} (playing: {})", new_playhead, playing)
    return SpineItemAndPosition(
      spineItem = element as PlayerFindawaySpineElement,
      position = new_playhead)
  }

  override fun play() {
    this.log.debug("playing")
    this.enginePlay(kotlin.synchronized(this.stateLock, { this.state.playhead }))
  }

  override fun pause() {
    this.log.debug("pausing")
    this.enginePause()
  }

  override fun skipForward() {
    val playhead = this.updatePlayheadFromEngine()
    this.engine.playbackEngine.seekTo(playhead.position.offsetMilliseconds + 15_000L)
  }

  override fun skipBack() {
    val playhead = this.updatePlayheadFromEngine()
    this.engine.playbackEngine.seekTo(Math.max(0L, playhead.position.offsetMilliseconds - 15_000L))
  }

  override fun skipToNextChapter() {
    this.engine.playbackEngine.nextChapter()
  }

  override fun skipToPreviousChapter() {
    this.engine.playbackEngine.previousChapter()
  }

  override fun playAtLocation(location: PlayerPosition) {
    this.enginePlay(location)
  }

  override fun movePlayheadToLocation(location: PlayerPosition) {
    this.enginePlay(location)
    this.enginePause()
  }

  override val key: String
    get() = TODO("not implemented")

}