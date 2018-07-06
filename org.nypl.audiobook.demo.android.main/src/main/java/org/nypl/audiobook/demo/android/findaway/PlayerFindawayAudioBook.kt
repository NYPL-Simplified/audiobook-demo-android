package org.nypl.audiobook.demo.android.findaway

import android.content.Context
import io.audioengine.mobile.AudioEngine
import io.audioengine.mobile.DownloadStatus
import io.audioengine.mobile.config.LogLevel
import io.audioengine.mobile.persistence.DeleteRequest
import io.audioengine.mobile.persistence.DownloadRequest
import io.audioengine.mobile.persistence.DownloadType
import org.nypl.audiobook.demo.android.api.PlayerAudioBookType
import org.nypl.audiobook.demo.android.api.PlayerChapterLocationType
import org.nypl.audiobook.demo.android.api.PlayerDownloadTaskType
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementDownloadFailed
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementDownloaded
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementDownloading
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementInitial
import org.nypl.audiobook.demo.android.api.PlayerSpineElementType
import org.nypl.audiobook.demo.android.api.PlayerType
import org.nypl.audiobook.demo.android.findaway.PlayerFindawayManifest.PlayerFindawayManifestSpineItem
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.PublishSubject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class PlayerFindawayAudioBook private constructor(
  private val manifest: PlayerFindawayManifest,
  private val engine: AudioEngine,
  override val spine: List<PlayerFindawaySpineElement>,
  override val spineByID: Map<String, PlayerFindawaySpineElement>,
  private val status_map: PlayerFindawaySpineElementStatusMap) : PlayerAudioBookType {

  init {
    if (this.spine.size != this.spineByID.size) {
      throw IllegalStateException(
        "Spine size " + this.spine.size + " must match spineByID size " + this.spineByID.size)
    }
  }

  /**
   * A download task capable of fetching a single spine item.
   */

  private class PlayerFindawayDownloadTask(
    private val engine: AudioEngine,
    private val status_map: PlayerFindawaySpineElementStatusMap,
    private val manifest: PlayerFindawayManifest,
    private val spine_element: PlayerFindawaySpineElement)
    : PlayerDownloadTaskType {

    private val log = LoggerFactory.getLogger(PlayerFindawayDownloadTask::class.java)
    private val downloader = this.engine.downloadEngine

    private val downloading = AtomicBoolean()
    private var download_request: DownloadRequest? = null
    private var download_progress: Int = 0

    private val download_subscription_status: Subscription =
      this.downloader.getStatus(
        this.manifest.fulfillmentId,
        this.spine_element.manifest.part,
        this.spine_element.manifest.chapter)
        .subscribe(
          { status -> this.onDownloadStatus(status) },
          { error -> this.onDownloadError(error) })

    private val download_subscription_progress: Subscription =
      this.downloader.getProgress(
        this.manifest.fulfillmentId,
        this.spine_element.manifest.part,
        this.spine_element.manifest.chapter)
        .subscribe(
          { status -> this.onDownloadProgress(status) },
          { error -> this.onDownloadError(error) })

    override fun fetch() {
      if (this.downloading.compareAndSet(false, true)) {
        this.log.debug("fetching")
        this.status_map.update(PlayerSpineElementDownloading(this.spine_element.id, 0))

        val request =
          DownloadRequest.builder()
            .chapter(this.spine_element.manifest.chapter)
            .part(this.spine_element.manifest.part)
            .licenseId(this.manifest.licenseId)
            .type(DownloadType.SINGLE)
            .contentId(this.manifest.fulfillmentId)
            .build()

        this.downloader.download(request)
        this.download_request = request
      }
    }

    private fun onDownloadProgress(progress: Int?) {
      this.log.trace("onDownloadProgress: {}: {}", this.spine_element.id, progress)

      if (this.downloading.get()) {
        val prog = progress ?: 0
        this.status_map.update(PlayerSpineElementDownloading(this.spine_element.id, prog))
        this.download_progress = prog
      }
    }

    private fun onDownloadError(error: Throwable?) {
      this.log.error("onDownloadError: {}: ", this.spine_element.id, error)

      if (this.downloading.compareAndSet(true, false)) {
        val exception = Exception(error)
        this.status_map.update(PlayerSpineElementDownloadFailed(
          this.spine_element.id,
          exception,
          "Download failed!"))
      }
    }

    private fun onDownloadStatus(status: DownloadStatus?) {
      this.log.trace("onDownloadStatus: {}: {}", this.spine_element.id, status)

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
      this.log.debug("onDownloadFinished: {}", this.spine_element.id)

      /*
       * Findaway will redundantly publish DOWNLOADED status updates for books hundreds of times.
       */

      this.downloading.set(false)
      this.status_map.update(PlayerSpineElementDownloaded(this.spine_element.id))
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
          .chapter(this.spine_element.manifest.chapter)
          .part(this.spine_element.manifest.part)
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
      this.status_map.update(PlayerSpineElementInitial(this.spine_element.id))
    }

    override val progress: Double
      get() = this.download_progress.toDouble()

    override val id: String
      get() = this.spine_element.id
  }

  /*
   * A map from spine item IDs to status values. Updates trigger status broadcasts to subscribers.
   */

  private data class PlayerFindawaySpineElementStatusMap(
    private val status_map: ConcurrentHashMap<String, PlayerSpineElementStatus> = ConcurrentHashMap(),
    private val subject: PublishSubject<PlayerSpineElementStatus> = PublishSubject.create()) {

    val observable: Observable<PlayerSpineElementStatus> = this.subject

    fun update(status: PlayerSpineElementStatus) {
      this.status_map.put(status.id, status)
      this.subject.onNext(status)
    }

    fun status(id: String): PlayerSpineElementStatus {
      return this.status_map[id] ?: PlayerSpineElementInitial(id)
    }
  }

  /*
   * A single spine element.
   */

  private class PlayerFindawaySpineElement(
    private val book: PlayerFindawayAudioBook,
    private val engine: AudioEngine,
    private val status_map: PlayerFindawaySpineElementStatusMap,
    val manifest: PlayerFindawayManifestSpineItem) : PlayerSpineElementType {

    override val title: String
      get() = this.manifest.title

    override val downloadTask: PlayerDownloadTaskType =
      PlayerFindawayDownloadTask(this.engine, this.status_map, this.book.manifest, this)

    override val status: PlayerSpineElementStatus
      get() = this.status_map.status(this.id)

    override val id: String
      get() = String.format("%d-%d", this.manifest.part, this.manifest.chapter)

    override val chapter: PlayerChapterLocationType
      get() = TODO("not implemented")
  }

  companion object {

    private val log = LoggerFactory.getLogger(PlayerFindawayAudioBook::class.java)

    /*
     * Create a new audio book from the given parsed manifest.
     */

    fun create(context: Context, manifest: PlayerFindawayManifest): PlayerAudioBookType {
      val status_map = PlayerFindawaySpineElementStatusMap()

      /*
       * Initialize the audio engine.
       */

      this.log.debug("initializing audio engine")
      AudioEngine.init(context, manifest.sessionKey, LogLevel.VERBOSE)
      this.log.debug("initialized audio engine")

      /*
       * Set up all the various bits of state required.
       */

      val engine = AudioEngine.getInstance()
      val elements = ArrayList<PlayerFindawaySpineElement>()
      val elements_by_id = HashMap<String, PlayerFindawaySpineElement>()
      val book = PlayerFindawayAudioBook(manifest, engine, elements, elements_by_id, status_map)

      manifest.spineItems.forEach { spine_item ->
        val element = PlayerFindawaySpineElement(book, engine, status_map, spine_item)
        elements.add(element)
        elements_by_id.put(element.id, element)
      }

      return book
    }
  }

  override val uniqueIdentifier: String
    get() = this.manifest.id

  override val player: PlayerType
    get() = TODO("not implemented")

  override val title: String
    get() = this.manifest.title

  override val spineElementStatusUpdates: Observable<PlayerSpineElementStatus>
    get() = this.status_map.observable

}