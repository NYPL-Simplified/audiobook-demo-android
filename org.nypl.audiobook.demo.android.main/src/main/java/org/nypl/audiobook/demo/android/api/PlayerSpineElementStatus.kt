package org.nypl.audiobook.demo.android.api

/**
 * The status of a spine element.
 */

sealed class PlayerSpineElementStatus {

  /**
   * The initial state of the spine element.
   */

  object PlayerSpineElementInitial : PlayerSpineElementStatus()

  /**
   * The spine element is currently downloading.
   */

  data class PlayerSpineElementDownloading(
    val progress: Int)
    : PlayerSpineElementStatus()

  /**
   * The download of the spine element failed.
   */

  data class PlayerSpineElementDownloadFailed(
    val exception: Exception?,
    val message: String)
    : PlayerSpineElementStatus()

  /**
   * The spine element is fully downloaded and ready for playback.
   */

  data class PlayerSpineElementDownloaded(
    val playing : Playing)
    : PlayerSpineElementStatus()

  enum class Playing {
    STOPPED, PAUSED, PLAYING
  }
}
