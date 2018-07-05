package org.nypl.audiobook.demo.android.findaway

import android.content.Context
import io.audioengine.mobile.AudioEngine
import io.audioengine.mobile.DownloadStatus
import io.audioengine.mobile.config.LogLevel
import io.audioengine.mobile.persistence.DownloadRequest
import io.audioengine.mobile.persistence.DownloadType
import org.nypl.audiobook.demo.android.api.PlayerAudioBookType
import org.nypl.audiobook.demo.android.api.PlayerChapterLocationType
import org.nypl.audiobook.demo.android.api.PlayerDownloadTaskType
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.*
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
    private var download_subscription_status: Subscription? = null
    private var download_subscription_progress: Subscription? = null
    private var download_request: DownloadRequest? = null

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

        val status_observable =
          this.downloader.getStatus(
            this.manifest.fulfillmentId,
            this.spine_element.manifest.part,
            this.spine_element.manifest.chapter)

        val progress_observable =
          this.downloader.getProgress(
            this.manifest.fulfillmentId,
            this.spine_element.manifest.part,
            this.spine_element.manifest.chapter)

        this.download_request = request
        this.download_subscription_status =
          status_observable.subscribe(
            { status -> this.onDownloadStatus(status) },
            { error -> this.onDownloadError(error) })
        this.download_subscription_progress =
          progress_observable.subscribe(
            { status -> this.onDownloadProgress(status) },
            { error -> this.onDownloadError(error) })
      }
    }

    private fun onDownloadProgress(progress: Int?) {
      this.log.debug("onDownloadProgress: {}", progress)

      if (this.downloading.get()) {
        this.status_map.update(PlayerSpineElementDownloading(
          this.spine_element.id, progress ?: 0))
      }
    }

    private fun onDownloadError(error: Throwable?) {
      this.log.error("onDownloadError: ", error)
    }

    private fun onDownloadStatus(status: DownloadStatus?) {
      this.log.debug("onDownloadStatus: {}", status)

      if (this.downloading.get()) {
        when (status) {
          DownloadStatus.NOT_DOWNLOADED -> { }
          DownloadStatus.DOWNLOADED -> {
            this.onDownloadFinished()
          }
          DownloadStatus.DOWNLOADING -> { }
          DownloadStatus.QUEUED -> { }
          DownloadStatus.PAUSED -> { }
          null -> {

          }
        }
      }
    }

    private fun onDownloadFinished() {
      if (this.downloading.compareAndSet(true, false)) {
        this.log.debug("onDownloadFinished")
        this.cleanUpSubscriptions()
        this.status_map.update(PlayerSpineElementDownloaded(this.spine_element.id))
      }
    }

    override fun delete() {
      if (this.downloading.compareAndSet(true, false)) {
        this.log.debug("cancelling download")
        val request = this.download_request
        if (request != null) {
          this.downloader.cancel(request)
        }
        this.cleanUpSubscriptions()
      }

      this.status_map.update(PlayerSpineElementInitial(this.spine_element.id))
    }

    private fun cleanUpSubscriptions() {
      this.download_subscription_status =
        this.unsubscribe(this.download_subscription_status)
      this.download_subscription_progress =
        this.unsubscribe(this.download_subscription_progress)
    }

    private fun unsubscribe(sub: Subscription?): Subscription? {
      if (sub != null) {
        sub.unsubscribe()
      }
      return null
    }

    override val progress: Double
      get() = 0.0

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
      return this.status_map[id] ?: throw IllegalArgumentException("Unknown spine element: " + id)
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
        status_map.update(PlayerSpineElementInitial(element.id))
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