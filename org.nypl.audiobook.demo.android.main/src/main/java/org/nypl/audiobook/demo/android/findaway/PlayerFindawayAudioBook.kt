package org.nypl.audiobook.demo.android.findaway

import android.content.Context
import io.audioengine.mobile.AudioEngine
import io.audioengine.mobile.config.LogLevel
import org.nypl.audiobook.demo.android.api.PlayerAudioBookType
import org.nypl.audiobook.demo.android.api.PlayerSpineElementStatus
import org.nypl.audiobook.demo.android.api.PlayerSpineElementType
import org.nypl.audiobook.demo.android.api.PlayerType
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.util.SortedMap
import java.util.TreeMap
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.first
import kotlin.collections.forEach

/**
 * A Findaway based implementation of the {@link PlayerAudioBookType} interface.
 */

class PlayerFindawayAudioBook private constructor(
  val manifest: PlayerFindawayManifest,
  private val engine: AudioEngine,
  private val statusEvents: PublishSubject<PlayerSpineElementStatus>,
  override val spine: List<PlayerFindawaySpineElement>,
  override val spineByID: Map<String, PlayerFindawaySpineElement>,
  override val spineByPartAndChapter: SortedMap<Int, SortedMap<Int, PlayerSpineElementType>>)
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

  override val spineElementStatusUpdates: Observable<PlayerSpineElementStatus>
    get() = this.statusEvents

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

      val statusEvents : PublishSubject<PlayerSpineElementStatus> = PublishSubject.create()
      val engine = AudioEngine.getInstance()
      val elements = ArrayList<PlayerFindawaySpineElement>()
      val elements_by_id = HashMap<String, PlayerFindawaySpineElement>()
      val elements_by_part = TreeMap<Int, TreeMap<Int, PlayerSpineElementType>>()

      var index = 0
      var spine_item_previous: PlayerFindawaySpineElement? = null
      manifest.spineItems.forEach { spine_item ->
        val element =
          PlayerFindawaySpineElement(
            engine = engine,
            statusEvents = statusEvents,
            itemManifest = spine_item,
            bookManifest = manifest,
            index = index,
            nextElement = null)

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

      return PlayerFindawayAudioBook(
        manifest = manifest,
        engine = engine,
        statusEvents = statusEvents,
        spine = elements,
        spineByID = elements_by_id,
        spineByPartAndChapter = elements_by_part as SortedMap<Int, SortedMap<Int, PlayerSpineElementType>>)
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