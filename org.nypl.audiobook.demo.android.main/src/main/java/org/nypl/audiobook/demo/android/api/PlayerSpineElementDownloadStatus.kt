package org.nypl.audiobook.demo.android.api

sealed class PlayerSpineElementDownloadStatus {

  object PlayerSpineElementNotDownloaded
    : PlayerSpineElementDownloadStatus()

  data class PlayerSpineElementDownloading(
    val percent: Int)
    : PlayerSpineElementDownloadStatus()

  object PlayerSpineElementDownloaded
    : PlayerSpineElementDownloadStatus()

  data class PlayerSpineElementDownloadFailed(
    val exception: Exception?,
    val message: String)
    : PlayerSpineElementDownloadStatus()

}
