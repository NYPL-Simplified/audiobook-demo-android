package org.nypl.audiobook.demo.android.findaway

import io.audioengine.mobile.AudioEngine
import io.audioengine.mobile.DownloadStatus
import io.audioengine.mobile.persistence.DeleteRequest
import io.audioengine.mobile.persistence.DownloadRequest
import io.audioengine.mobile.persistence.DownloadType
import org.nypl.audiobook.demo.android.api.PlayerDownloadTaskType
import org.nypl.audiobook.demo.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloadFailed
import org.nypl.audiobook.demo.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloaded
import org.nypl.audiobook.demo.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloading
import org.nypl.audiobook.demo.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementNotDownloaded
import org.slf4j.LoggerFactory
import rx.Subscription
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A download task capable of fetching a single spine item.
 */

class PlayerFindawayDownloadTask(
  private val engine: AudioEngine,
  private val manifest: PlayerFindawayManifest,
  private val spineElement: PlayerFindawaySpineElement)
  : PlayerDownloadTaskType {

  private val log = LoggerFactory.getLogger(PlayerFindawayDownloadTask::class.java)
  private val downloader = this.engine.downloadEngine

  private val downloading = AtomicBoolean()
  private var download_request: DownloadRequest? = null
  private var download_progress: Int = 0

  private val download_subscription_status: Subscription =
    this.downloader.getStatus(
      this.manifest.fulfillmentId,
      this.spineElement.itemManifest.part,
      this.spineElement.itemManifest.chapter)
      .subscribe(
        { status -> this.onDownloadStatus(status) },
        { error -> this.onDownloadError(error) })

  private val download_subscription_progress: Subscription =
    this.downloader.getProgress(
      this.manifest.fulfillmentId,
      this.spineElement.itemManifest.part,
      this.spineElement.itemManifest.chapter)
      .subscribe(
        { status -> this.onDownloadProgress(status) },
        { error -> this.onDownloadError(error) })

  override fun fetch() {
    if (this.downloading.compareAndSet(false, true)) {
      this.log.debug("fetching")
      this.spineElement.setDownloadStatus(PlayerSpineElementDownloading(0))

      val request =
        DownloadRequest.builder()
          .chapter(this.spineElement.itemManifest.chapter)
          .part(this.spineElement.itemManifest.part)
          .licenseId(this.manifest.licenseId)
          .type(DownloadType.SINGLE)
          .contentId(this.manifest.fulfillmentId)
          .build()

      this.downloader.download(request)
      this.download_request = request
    }
  }

  private fun onDownloadProgress(progress: Int?) {
    this.log.trace("onDownloadProgress: {}: {}", this.spineElement.id, progress)

    if (this.downloading.get()) {
      val prog = progress ?: 0
      this.spineElement.setDownloadStatus(PlayerSpineElementDownloading(prog))
      this.download_progress = prog
    }
  }

  private fun onDownloadError(error: Throwable?) {
    this.log.error("onDownloadError: {}: ", this.spineElement.id, error)

    if (this.downloading.compareAndSet(true, false)) {
      val exception = Exception(error)
      this.spineElement.setDownloadStatus(PlayerSpineElementDownloadFailed(
        exception,
        "Download failed!"))
    }
  }

  private fun onDownloadStatus(status: DownloadStatus?) {
    this.log.trace("onDownloadStatus: {}: {}", this.spineElement.id, status)

    when (status) {
      DownloadStatus.NOT_DOWNLOADED -> {

      }
      DownloadStatus.DOWNLOADED -> {
        this.onDownloadFinished()
      }
      DownloadStatus.DOWNLOADING -> {

      }
      DownloadStatus.QUEUED -> {

      }
      DownloadStatus.PAUSED -> {

      }
      null -> {

      }
    }
  }

  private fun onDownloadFinished() {
    this.log.debug("onDownloadFinished: {}", this.spineElement.id)
    this.downloading.set(false)
    this.spineElement.setDownloadStatus(PlayerSpineElementDownloaded)
  }

  override fun delete() {

    /*
     * If a download is in progress, cancel it.
     */

    if (this.downloading.compareAndSet(true, false)) {
      this.log.debug("cancelling download")
      val request = this.download_request
      if (request != null) {
        this.downloader.cancel(request)
      }
    }

    /*
     * Request deletion of the local content.
     */

    val request =
      DownloadRequest.builder()
        .chapter(this.spineElement.itemManifest.chapter)
        .part(this.spineElement.itemManifest.part)
        .licenseId(this.manifest.licenseId)
        .type(DownloadType.SINGLE)
        .contentId(this.manifest.fulfillmentId)
        .build()

    val delete_request =
      DeleteRequest.builder()
        .downloadRequest(request)
        .contentId(this.manifest.fulfillmentId)
        .build()

    this.downloader.delete(delete_request)
    this.spineElement.setDownloadStatus(PlayerSpineElementNotDownloaded)
  }

  override val progress: Double
    get() = this.download_progress.toDouble()

  override val id: String
    get() = this.spineElement.id
}