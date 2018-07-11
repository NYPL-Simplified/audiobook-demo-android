package org.nypl.audiobook.demo.android.findaway

import io.audioengine.mobile.AudioEngine
import org.nypl.audiobook.demo.android.api.PlayerDownloadTaskType
import org.nypl.audiobook.demo.android.api.PlayerPosition
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus
import org.nypl.audiobook.demo.android.api.PlayerSpineElementType
import rx.subjects.PublishSubject

/**
 * A spine element in an audio book.
 */

class PlayerFindawaySpineElement(
  private val engine: AudioEngine,
  private val statusEvents: PublishSubject<PlayerSpineElementStatus>,
  val bookManifest: PlayerFindawayManifest,
  val itemManifest: PlayerFindawayManifest.PlayerFindawayManifestSpineItem,
  override val index: Int,
  internal var nextElement: PlayerSpineElementType?)
  : PlayerSpineElementType {

  private val statusLock: Any = Object()
  private var statusNow: PlayerSpineElementStatus =
    PlayerSpineElementStatus.PlayerSpineElementInitial

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

  fun setStatus(status: PlayerSpineElementStatus) {
    synchronized(this.statusLock, { this.statusNow = status })
    this.statusEvents.onNext(status)
  }

  override val status: PlayerSpineElementStatus
    get() = synchronized(this.statusLock, { this.statusNow })

  override val id: String
    get() = String.format("%d-%d", this.itemManifest.part, this.itemManifest.chapter)
}