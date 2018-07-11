package org.nypl.audiobook.demo.android.api

sealed class PlayerEvent {

  data class PlayerEventPlaybackStarted(
    val spineElement: PlayerSpineElementType,
    val offset: Int)
    : PlayerEvent()

  data class PlayerEventChapterCompleted(
    val spineElement: PlayerSpineElementType,
    val offset: Int)
    : PlayerEvent()

  data class PlayerEventPlaybackStopped(
    val spineElement: PlayerSpineElementType,
    val offset: Int)
    : PlayerEvent()

}
