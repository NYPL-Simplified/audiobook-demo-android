package org.nypl.audiobook.demo.android.api

import rx.Observable
import java.util.SortedMap

interface PlayerAudioBookType {

  val uniqueIdentifier: String

  val spine: List<PlayerSpineElementType>

  val spineByID: Map<String, PlayerSpineElementType>

  val spineByPartAndChapter: SortedMap<Int, SortedMap<Int, PlayerSpineElementType>>

  fun spineElementInitial() : PlayerSpineElementType?

  fun spineElementForPartAndChapter(
    part: Int,
    chapter: Int): PlayerSpineElementType?

  val spineElementStatusUpdates: Observable<PlayerSpineElementStatus>

  val player: PlayerType

  val title: String

}