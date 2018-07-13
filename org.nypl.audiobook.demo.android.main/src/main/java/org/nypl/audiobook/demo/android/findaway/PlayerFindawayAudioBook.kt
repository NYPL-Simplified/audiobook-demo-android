package org.nypl.audiobook.demo.android.findaway

import android.content.Context
import io.audioengine.mobile.AudioEngine
import io.audioengine.mobile.config.LogLevel
import org.joda.time.Duration
import org.nypl.audiobook.demo.android.api.PlayerAudioBookType
import org.nypl.audiobook.demo.android.api.PlayerSpineElementDownloadStatus
import org.nypl.audiobook.demo.android.api.PlayerSpineElementType
import org.nypl.audiobook.demo.android.api.PlayerType
import org.slf4j.LoggerFactory
import rx.subjects.PublishSubject
import java.util.SortedMap
import java.util.TreeMap

/**
 * A Findaway based implementation of the {@link PlayerAudioBookType} interface.
 */

class PlayerFindawayAudioBook private constructor(
  val manifest: PlayerFindawayManifest,
  private val engine: AudioEngine,
  override val spine: List<PlayerFindawaySpineElement>,
  override val spineByID: Map<String, PlayerFindawaySpineElement>,
  override val spineByPartAndChapter: SortedMap<Int, SortedMap<Int, PlayerSpineElementType>>,
  override val spineElementDownloadStatus: PublishSubject<PlayerSpineElementDownloadStatus>)
  : PlayerAudioBookType {

  override fun spineElementInitial(): PlayerSpineElementType? {
    if (!this.spine.isEmpty()) {
      return this.spine.first()
    }
    return null
  }

  override fun spineElementForPartAndChapter(part: Int, chapter: Int): PlayerSpineElementType? {
    if (this.spineByPartAndChapter.containsKey(part)) {
      val chapters = this.spineByPartAndChapter[part]!!
      return chapters[chapter]
    }
    return null
  }

  private val findaway_player: PlayerFindaway = PlayerFindaway(this, this.engine)

  init {
    if (this.spine.size != this.spineByID.size) {
      throw IllegalStateException(
        "Spine size " + this.spine.size + " must match spineByID size " + this.spineByID.size)
    }
  }

  override val uniqueIdentifier: String
    get() = this.manifest.id

  override val player: PlayerType
    get() = this.findaway_player

  override val title: String
    get() = this.manifest.title

  companion object {

    private val log = LoggerFactory.getLogger(PlayerFindawayAudioBook::class.java)

    /*
     * Create a new audio book from the given parsed manifest.
     */

    fun create(context: Context, manifest: PlayerFindawayManifest): PlayerAudioBookType {

      /*
       * Initialize the audio engine.
       */

      this.log.debug("initializing audio engine")
      AudioEngine.init(context, manifest.sessionKey, LogLevel.VERBOSE)
      this.log.debug("initialized audio engine")

      /*
       * Set up all the various bits of state required.
       */

      val statusEvents: PublishSubject<PlayerSpineElementDownloadStatus> = PublishSubject.create()
      val engine = AudioEngine.getInstance()
      val elements = ArrayList<PlayerFindawaySpineElement>()
      val elements_by_id = HashMap<String, PlayerFindawaySpineElement>()
      val elements_by_part = TreeMap<Int, TreeMap<Int, PlayerSpineElementType>>()

      var index = 0
      var spine_item_previous: PlayerFindawaySpineElement? = null
      manifest.spineItems.forEach { spine_item ->

        val duration =
          Duration.standardSeconds(Math.floor(spine_item.duration).toLong())

        val element =
          PlayerFindawaySpineElement(
            engine = engine,
            downloadStatusEvents = statusEvents,
            itemManifest = spine_item,
            bookManifest = manifest,
            index = index,
            nextElement = null,
            duration = duration)

        elements.add(element)
        elements_by_id.put(element.id, element)
        this.addElementByPartAndChapter(elements_by_part, element)
        ++index

        /*
         * Make the "next" field of the previous element point to the current element.
         */

        val previous = spine_item_previous
        if (previous != null) {
          previous.nextElement = element
        }
        spine_item_previous = element
      }

      val book = PlayerFindawayAudioBook(
        manifest = manifest,
        engine = engine,
        spine = elements,
        spineByID = elements_by_id,
        spineByPartAndChapter = elements_by_part as SortedMap<Int, SortedMap<Int, PlayerSpineElementType>>,
        spineElementDownloadStatus = statusEvents)

      for (e in elements) {
        e.setBook(book)
      }
      return book
    }

    /**
     * Organize an element by part number and chapter number.
     */

    private fun addElementByPartAndChapter(
      elements_by_part: TreeMap<Int, TreeMap<Int, PlayerSpineElementType>>,
      element: PlayerFindawaySpineElement) {

      val part_chapters: TreeMap<Int, PlayerSpineElementType> =
        if (elements_by_part.containsKey(element.itemManifest.part)) {
          elements_by_part[element.itemManifest.part]!!
        } else {
          TreeMap()
        }

      part_chapters.put(element.itemManifest.chapter, element)
      elements_by_part.put(element.itemManifest.part, part_chapters)
    }
  }
}