package org.nypl.audiobook.demo.android.findaway

import io.audioengine.mobile.AudioEngine
import org.joda.time.Duration
import org.nypl.audiobook.demo.android.api.PlayerAudioBookType
import org.nypl.audiobook.demo.android.api.PlayerDownloadTaskType
import org.nypl.audiobook.demo.android.api.PlayerPosition
import org.nypl.audiobook.demo.android.api.PlayerSpineElementDownloadStatus
import org.nypl.audiobook.demo.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementNotDownloaded
import org.nypl.audiobook.demo.android.api.PlayerSpineElementType
import rx.subjects.PublishSubject

/**
 * A spine element in an audio book.
 */

class PlayerFindawaySpineElement(
  private val engine: AudioEngine,
  private val downloadStatusEvents: PublishSubject<PlayerSpineElementDownloadStatus>,
  val bookManifest: PlayerFindawayManifest,
  val itemManifest: PlayerFindawayManifest.PlayerFindawayManifestSpineItem,
  override val index: Int,
  internal var nextElement: PlayerSpineElementType?,
  override val duration: Duration)
  : PlayerSpineElementType {

  override val book: PlayerAudioBookType
    get() = this.bookActual

  private val statusLock: Any = Object()
  private var statusNow: PlayerSpineElementDownloadStatus = PlayerSpineElementNotDownloaded
  private lateinit var bookActual: PlayerFindawayAudioBook

  override val next: PlayerSpineElementType?
    get() = this.nextElement

  override val position: PlayerPosition
    get() = PlayerPosition(
      this.itemManifest.title,
      this.itemManifest.part,
      this.itemManifest.chapter,
      0)

  override val title: String
    get() = this.itemManifest.title

  override val downloadTask: PlayerDownloadTaskType =
    PlayerFindawayDownloadTask(
      engine = this.engine,
      manifest = this.bookManifest,
      spineElement = this)

  fun setBook(book: PlayerFindawayAudioBook) {
    this.bookActual = book
  }

  fun setDownloadStatus(status: PlayerSpineElementDownloadStatus) {
    synchronized(this.statusLock, { this.statusNow = status })
    this.downloadStatusEvents.onNext(status)
  }

  override val downloadStatus: PlayerSpineElementDownloadStatus
    get() = synchronized(this.statusLock, { this.statusNow })

  override val id: String
    get() = String.format("%d-%d", this.itemManifest.part, this.itemManifest.chapter)
}