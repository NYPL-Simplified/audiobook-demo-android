package org.nypl.audiobook.demo.android.api

sealed class PlayerEvent {

  /**
   * Playback of the given spine element has started.
   */

  data class PlayerEventPlaybackStarted(
    val spineElement: PlayerSpineElementType,
    val offsetMilliseconds: Int)
    : PlayerEvent()

  /**
   * The given spine item is playing, and this event is a progress update indicating how far
   * along playback is.
   */

  data class PlayerEventPlaybackProgressUpdate(
    val spineElement: PlayerSpineElementType,
    val offsetMilliseconds: Int)
    : PlayerEvent()

  /**
   * Playback of the given spine element has just completed, and playback will continue to the
   * next spine item if it is available (downloaded).
   */

  data class PlayerEventChapterCompleted(
    val spineElement: PlayerSpineElementType,
    val offsetMilliseconds: Int)
    : PlayerEvent()

  /**
   * Playback could not continue to the given spine element because the spine element has not
   * been downloaded. Playback is about to stop.
   */

  data class PlayerEventUnavailableForPlayback(
    val spineElement: PlayerSpineElementType)
    : PlayerEvent()

  /**
   * Playback of the given spine element has stopped.
   */

  data class PlayerEventPlaybackStopped(
    val spineElement: PlayerSpineElementType,
    val offsetMilliseconds: Int)
    : PlayerEvent()

}
