package org.nypl.audiobook.demo.android.api

/**
 * The status of a spine element.
 */

sealed class PlayerSpineElementStatus {

  /**
   * The unique ID of the spine element.
   */

  abstract val id: String

  /**
   * The initial state of the spine element.
   */

  data class PlayerSpineElementInitial(
    override val id: String)
    : PlayerSpineElementStatus()

  /**
   * The spine element is currently downloading.
   */

  data class PlayerSpineElementDownloading(
    override val id: String,
    val progress: Int)
    : PlayerSpineElementStatus()

  /**
   * The download of the spine element failed.
   */

  data class PlayerSpineElementDownloadFailed(
    override val id: String,
    val exception: Exception?,
    val message: String)
    : PlayerSpineElementStatus()

  /**
   * The spine element is fully downloaded and ready for playback.
   */

  data class PlayerSpineElementDownloaded(
    override val id: String)
    : PlayerSpineElementStatus()

}
