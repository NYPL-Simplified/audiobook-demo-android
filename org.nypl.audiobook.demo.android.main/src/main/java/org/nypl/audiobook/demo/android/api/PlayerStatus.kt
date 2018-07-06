package org.nypl.audiobook.demo.android.api

sealed class PlayerStatus {

  data class PlayerPlaybackStarted(
    val position: PlayerPosition)
    : PlayerStatus()

  data class PlayerPlaybackStopped(
    val position: PlayerPosition)
    : PlayerStatus()

}
