package org.nypl.audiobook.demo.android.findaway

import io.audioengine.mobile.AudioEngine
import org.nypl.audiobook.demo.android.RawManifest
import org.nypl.audiobook.demo.android.api.PlayerAudioBookType
import org.nypl.audiobook.demo.android.api.PlayerChapterLocationType
import org.nypl.audiobook.demo.android.api.PlayerDownloadTaskType
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementDownloadFailed
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus.PlayerSpineElementInitial
import org.nypl.audiobook.demo.android.api.PlayerSpineElementType
import org.nypl.audiobook.demo.android.api.PlayerType
import rx.Observable
import rx.subjects.PublishSubject
import java.util.concurrent.ConcurrentHashMap

class PlayerFindawayAudioBook private constructor(
  private val manifest: RawManifest,
  private val engine: AudioEngine,
  override val spine: List<PlayerFindawaySpineElement>,
  override val spineByID: Map<String, PlayerFindawaySpineElement>,
  private val status_map: PlayerFindawaySpineElementStatusMap)
  : PlayerAudioBookType {

  init {
    if (this.spine.size != this.spineByID.size) {
      throw IllegalStateException(
        "Spine size " + this.spine.size + " must match spineByID size " + this.spineByID.size)
    }
  }

  private class PlayerFindawayDownloadTask(
    private val status_map: PlayerFindawaySpineElementStatusMap,
    private val spine_element: PlayerFindawaySpineElement)
    : PlayerDownloadTaskType {

    override fun fetch() {
      this.status_map.update(PlayerSpineElementDownloadFailed(
        this.spine_element.id, null, "Not implemented!"))
    }

    override fun delete() {
      this.status_map.update(PlayerSpineElementInitial(this.spine_element.id))
    }

    override val progress: Double
      get() = 0.0

    override val id: String
      get() = TODO("not implemented")
  }

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

  private class PlayerFindawaySpineElement(
    private val status_map: PlayerFindawaySpineElementStatusMap,
    private val bookId: String,
    private val partNumber: String,
    private val chapterNumber: String,
    override val title: String) : PlayerSpineElementType {

    override val downloadTask: PlayerDownloadTaskType =
      PlayerFindawayDownloadTask(this.status_map, this)

    override val status: PlayerSpineElementStatus
      get() = this.status_map.status(this.id)

    override val id: String
      get() = this.bookId + "-" + this.partNumber + "-" + this.chapterNumber

    override val chapter: PlayerChapterLocationType
      get() = TODO("not implemented")
  }

  companion object {

    fun create(manifest: RawManifest, engine: AudioEngine): PlayerAudioBookType {
      val status_map = PlayerFindawaySpineElementStatusMap()

      val elements = manifest.spine.map { raw_spine_item ->
        val element = PlayerFindawaySpineElement(
          status_map,
          manifest.metadata.identifier,
          raw_spine_item.values["findaway:part"].toString(),
          raw_spine_item.values["findaway:sequence"].toString(),
          raw_spine_item.values["title"].toString())

        status_map.update(PlayerSpineElementInitial(element.id))
        element
      }

      val elements_by_id = PlayerFindawayAudioBook.makeSpineByID(elements)
      return PlayerFindawayAudioBook(manifest, engine, elements, elements_by_id, status_map)
    }

    private fun makeSpineByID(spine: List<PlayerFindawaySpineElement>): Map<String, PlayerFindawaySpineElement> {
      return spine.associateBy { element -> element.id }
    }
  }

  override val uniqueIdentifier: String
    get() = this.manifest.metadata.identifier

  override val player: PlayerType
    get() = TODO("not implemented")

  override val title: String
    get() = this.manifest.metadata.title

  override val spineElementStatusUpdates: Observable<PlayerSpineElementStatus>
    get() = this.status_map.observable

}