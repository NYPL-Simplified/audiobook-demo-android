package org.nypl.audiobook.demo.android.findaway

import io.audioengine.mobile.AudioEngine
import io.audioengine.mobile.PlaybackEvent
import io.audioengine.mobile.play.PlayerState
import io.audioengine.mobile.play.PlayerState.BUFFERING
import io.audioengine.mobile.play.PlayerState.COMPLETED
import io.audioengine.mobile.play.PlayerState.ERROR
import io.audioengine.mobile.play.PlayerState.IDLE
import io.audioengine.mobile.play.PlayerState.INITIALIZED
import io.audioengine.mobile.play.PlayerState.PAUSED
import io.audioengine.mobile.play.PlayerState.PLAYING
import io.audioengine.mobile.play.PlayerState.PREPARED
import io.audioengine.mobile.play.PlayerState.PREPARING
import io.audioengine.mobile.play.PlayerState.RELEASED
import io.audioengine.mobile.play.PlayerState.STOPPED
import org.nypl.audiobook.demo.android.api.PlayerPlaybackRate
import org.nypl.audiobook.demo.android.api.PlayerPosition
import org.nypl.audiobook.demo.android.api.PlayerSpineElementType
import org.nypl.audiobook.demo.android.api.PlayerType
import org.slf4j.LoggerFactory

class PlayerFindaway(
  private val book: PlayerFindawayAudioBook,
  private val engine: AudioEngine)
  : PlayerType {

  private val log = LoggerFactory.getLogger(PlayerFindaway::class.java)

  private var rate: PlayerPlaybackRate = PlayerPlaybackRate.NORMAL_TIME

  override var playbackRate: PlayerPlaybackRate
    get() = this.rate
    set(value) {
      this.rate = value
      this.engine.playbackEngine.speed = this.rate.speed.toFloat()
    }

  private var playing = false

  private var playhead: PlayerPosition =
    if (this.book.spine.size > 0) {
      (this.book.spine[0] as PlayerSpineElementType).position
    } else {
      PlayerPosition(null, 0, 0, 0)
    }

  init {
    this.engine.playbackEngine.events().subscribe(
      { event -> this.onPlaybackEvent(event) },
      { error -> this.onPlaybackError(error) })

    this.engine.playbackEngine.state.subscribe(
      { state -> this.onPlaybackState(state) },
      { error -> this.onPlaybackError(error) })
  }

  private fun onPlaybackState(state: PlayerState?) {
    this.log.debug("onPlaybackState: {}: {}", this.playhead, state)

    when (state) {
      IDLE -> {

      }
      INITIALIZED -> {

      }
      PREPARING -> {

      }
      PREPARED -> {

      }
      BUFFERING -> {

      }
      PLAYING -> {

      }
      PAUSED -> {

      }
      STOPPED -> {

      }
      RELEASED -> {

      }
      ERROR -> {

      }
      COMPLETED -> {

      }
      null -> {
      }
    }
  }

  private fun onPlaybackError(error: Throwable?) {

  }

  private fun onPlaybackEvent(event: PlaybackEvent?) {

  }

  override val isPlaying: Boolean
    get() = this.playing

  override fun play() {
    this.engine.playbackEngine.play(
      this.book.manifest.licenseId,
      this.book.manifest.fulfillmentId,
      this.playhead.part,
      this.playhead.chapter,
      this.playhead.offset)
    this.playing = true
  }

  override fun pause() {
    this.engine.playbackEngine.pause()
    this.playing = false
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
    TODO("not implemented")
  }

  override val key: String
    get() = TODO("not implemented")

}