package org.nypl.audiobook.demo.android.findaway

import io.audioengine.mobile.AudioEngine
import io.audioengine.mobile.PlaybackEvent
import io.audioengine.mobile.play.PlayerState
import org.nypl.audiobook.demo.android.api.PlayerEvent
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventChapterCompleted
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventPlaybackStarted
import org.nypl.audiobook.demo.android.api.PlayerEvent.PlayerEventPlaybackStopped
import org.nypl.audiobook.demo.android.api.PlayerPlaybackRate
import org.nypl.audiobook.demo.android.api.PlayerPosition
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementDownloadFailed
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementDownloaded
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementDownloading
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementInitial
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.Playing.PAUSED
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.Playing.PLAYING
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.Playing.STOPPED
import org.nypl.audiobook.demo.android.api.PlayerSpineElementType
import org.nypl.audiobook.demo.android.api.PlayerType
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject

class PlayerFindaway(
  private val book: PlayerFindawayAudioBook,
  private val engine: AudioEngine)
  : PlayerType {

  override val isPlaying: Boolean
    get() = synchronized(this.state.lock, { this.state.isPlaying })

  override val events: Observable<PlayerEvent>
    get() = this.event_source

  private val log = LoggerFactory.getLogger(PlayerFindaway::class.java)
  private val event_source: PublishSubject<PlayerEvent> = PublishSubject.create()
  private val state: State

  /**
   * Internal player state. Access to the fields of this structure MUST be protected via
   * synchronization on the lock field.
   */

  private data class State(
    val lock: Any,
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
        lock = Any(),
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
      synchronized(this.state.lock, { this.state.rate })
    set(value) {
      synchronized(this.state.lock) {
        this.state.rate = value
      }
      this.engine.playbackEngine.speed = value.speed.toFloat()
    }

  /**
   * Find the book's initial spine item.
   */

  private fun findSpineItemInitial(book: PlayerFindawayAudioBook): PlayerFindawaySpineElement {
    if (book.spine.size > 0) {
      return book.spine[0]
    } else {
      throw IllegalStateException("Book has no spine items")
    }
  }

  /**
   * Find the next spine item, if one is available. A spine item is available if it both exists
   * and is downloaded.
   */

  private fun findSpineItemNextIfAvailable(item: PlayerSpineElementType): PlayerSpineElementType? {
    val next = item.next
    if (next != null) {
      return when (next.status) {
        is PlayerSpineElementInitial -> null
        is PlayerSpineElementDownloading -> null
        is PlayerSpineElementDownloadFailed -> null
        is PlayerSpineElementDownloaded -> next
      }
    }
    return null
  }

  private fun onPlaybackState(state: PlayerState?) {
    this.log.debug("onPlaybackState: {}: {}",
      synchronized(this.state.lock, { this.state.playhead }), state)
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
      PlaybackEvent.PLAYBACK_STARTED ->
        this.onPlaybackEventPlaybackStarted()

      PlaybackEvent.CHAPTER_PLAYBACK_COMPLETED ->
        this.onPlaybackEventChapterCompleted()

      PlaybackEvent.PLAYBACK_ENDED -> {
        this.log.debug("onPlaybackEvent: playback ended")
        this.updatePlayheadFromEngine()
      }

      PlaybackEvent.PLAYBACK_PAUSED ->
        this.onPlaybackEventPlaybackPaused()

      PlaybackEvent.PLAYBACK_STOPPED ->
        this.onPlaybackEventPlaybackStopped()

      PlaybackEvent.PLAYBACK_PREPARING -> {
        this.log.debug("onPlaybackEvent: playback preparing")
        this.updatePlayheadFromEngine()
      }

      PlaybackEvent.PLAYBACK_BUFFERING_STARTED -> {
        this.log.debug("onPlaybackEvent: playback buffering started")
        this.updatePlayheadFromEngine()
      }

      PlaybackEvent.PLAYBACK_BUFFERING_ENDED -> {
        this.log.debug("onPlaybackEvent: playback buffering ended")
        this.updatePlayheadFromEngine()
      }

      PlaybackEvent.SEEK_COMPLETE -> {
        this.log.debug("onPlaybackEvent: seek complete")
        this.updatePlayheadFromEngine()
      }

      PlaybackEvent.PLAYBACK_PROGRESS_UPDATE ->
        this.onPlaybackEventPlaybackProgressUpdate()

      PlaybackEvent.UNKNOWN_PLAYBACK_ERROR -> {
        this.log.debug("onPlaybackEvent: unknown playback error")
      }

      PlaybackEvent.UNABLE_TO_AQUIRE_AUDIO_FOCUS -> {
        this.log.debug("onPlaybackEvent: unable to acquire audio focus")
      }

      PlaybackEvent.ERROR_PREPARING_PLAYER -> {
        this.log.debug("onPlaybackEvent: error preparing player")
      }

      PlaybackEvent.NO_CURRENT_CONTENT -> {
        this.log.debug("onPlaybackEvent: no current content")
      }

      PlaybackEvent.NO_CURRENT_CHAPTER -> {
        this.log.debug("onPlaybackEvent: no current chapter")
      }

      else -> {
        this.log.debug("onPlaybackEvent: unrecognized playback event code: {}", event.code)
      }
    }
  }

  private fun onPlaybackEventPlaybackProgressUpdate() {
    this.log.debug("onPlaybackEventPlaybackProgressUpdate")
    this.updatePlayheadFromEngine()
  }

  private fun onPlaybackEventPlaybackPaused() {
    this.log.debug("onPlaybackEventPlaybackPaused")
    this.updatePlayheadFromEngine()

    val new_playhead = this.updatePlayheadFromEngine()
    this.publishPlayingState(new_playhead.spineItem, PAUSED)

    this.event_source.onNext(PlayerEventPlaybackStopped(
      new_playhead.spineItem,
      new_playhead.position.offsetMilliseconds))
  }

  private fun onPlaybackEventPlaybackStopped() {
    this.log.debug("onPlaybackEventPlaybackStopped")

    val new_playhead = this.updatePlayheadFromEngine()
    this.publishPlayingState(new_playhead.spineItem, STOPPED)

    this.event_source.onNext(PlayerEventPlaybackStopped(
      new_playhead.spineItem,
      new_playhead.position.offsetMilliseconds))
  }

  private fun publishPlayingState(
    spineItem: PlayerFindawaySpineElement,
    playing: PlayerSpineElementStatus.Playing) {
    val status_now = spineItem.status
    when (status_now) {
      PlayerSpineElementInitial,
      is PlayerSpineElementDownloading,
      is PlayerSpineElementDownloadFailed -> {
        this.log.debug("publishPlayingState: not publishing {}", status_now)
      }
      is PlayerSpineElementDownloaded -> {
        this.log.debug("publishPlayingState: publishing playing = {}", playing)
        spineItem.setStatus(PlayerSpineElementDownloaded(playing = playing))
      }
    }
  }

  private fun onPlaybackEventPlaybackStarted() {
    this.log.debug("onPlaybackEventPlaybackStarted")

    val new_playhead = this.updatePlayheadFromEngine()
    this.publishPlayingState(new_playhead.spineItem, PLAYING)

    this.event_source.onNext(PlayerEventPlaybackStarted(
      new_playhead.spineItem,
      new_playhead.position.offsetMilliseconds))
  }

  private fun onPlaybackEventChapterCompleted() {

    /*
     * The CHAPTER_PLAYBACK_COMPLETED event will, for some reason, be published at both the end
     * of the old chapter and the start of the new one. We only want to publish an event once
     * for the chapter that has just ended, so the condition we use is that the current engine
     * position must be non-zero (in other words, not at the very start of a chapter), and the
     * engine must be playing. An event received in other states is ignored.
     */

    val current_playhead = this.updatePlayheadFromEngine()
    val was_playing = this.engine.playbackEngine.isPlaying
    if (current_playhead.position.offsetMilliseconds != 0 && was_playing) {

      /*
       * Stop playback now. If playback isn't manually stopped, the Findaway player will
       * automatically move to the next chapter. Unfortunately, if the next chapter hasn't been
       * downloaded, it will instead be streamed from the remote server. On connections with
       * limited bandwidth quotas, this is undesirable behaviour.
       */

      this.log.debug("onPlaybackEventChapterCompleted: handling chapter playback completion")
      this.event_source.onNext(PlayerEventChapterCompleted(
        spineElement = current_playhead.spineItem,
        offsetMilliseconds = current_playhead.position.offsetMilliseconds))
      this.publishPlayingState(current_playhead.spineItem, STOPPED)
      this.engine.playbackEngine.pause()

      /*
       * If the next spine item is available, start playing it.
       */

      val next_playhead = this.findSpineItemNextIfAvailable(current_playhead.spineItem)
      if (next_playhead != null) {
        this.log.debug("onPlaybackEventChapterCompleted: next chapter is available")
        this.publishPlayingState(next_playhead as PlayerFindawaySpineElement, PLAYING)
        this.engine.playbackEngine.play(
          this.book.manifest.licenseId,
          this.book.manifest.fulfillmentId,
          next_playhead.position.part,
          next_playhead.position.chapter,
          0)
      } else {
        this.log.debug("onPlaybackEventChapterCompleted: next chapter not available")
      }

      this.log.debug("onPlaybackEventChapterCompleted: handled chapter playback completed")
    } else {
      this.log.debug(
        "onPlaybackEventChapterCompleted: ignoring chapter completion (offset {}, was playing {})",
        current_playhead.position.offsetMilliseconds,
        was_playing)
    }
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
      return synchronized(this.state.lock, {
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
    synchronized(this.state.lock, {
      this.state.playhead = new_playhead
      this.state.isPlaying = playing
    })

    this.log.debug("now at {} (playing: {})", new_playhead, playing)
    return SpineItemAndPosition(
      spineItem = element as PlayerFindawaySpineElement,
      position = new_playhead)
  }

  override fun play() {
    this.log.debug("playing")

    val playhead = synchronized(this.state.lock, { this.state.playhead })

    this.engine.playbackEngine.play(
      this.book.manifest.licenseId,
      this.book.manifest.fulfillmentId,
      playhead.part,
      playhead.chapter,
      playhead.offsetMilliseconds)
  }

  override fun pause() {
    this.log.debug("pausing")

    this.updatePlayheadFromEngine()
    this.engine.playbackEngine.pause()
  }

  override fun skipForward() {
    TODO("not implemented")
  }

  override fun skipBack() {
    TODO("not implemented")
  }

  override fun playAtLocation(location: PlayerPosition) {
    this.movePlayheadToLocation(location)
    this.play()
  }

  override fun movePlayheadToLocation(location: PlayerPosition) {
    this.engine.playbackEngine.play(
      this.book.manifest.licenseId,
      this.book.manifest.fulfillmentId,
      location.part,
      location.chapter,
      location.offsetMilliseconds)
    this.pause()
  }

  override val key: String
    get() = TODO("not implemented")

}