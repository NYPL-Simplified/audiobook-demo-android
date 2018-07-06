package org.nypl.audiobook.demo.android.api

import rx.Observable

interface PlayerAudioBookType {

  val uniqueIdentifier: String

  val spine: List<PlayerSpineElementType>

  val spineByID: Map<String, PlayerSpineElementType>

  val spineElementStatusUpdates: Observable<PlayerSpineElementStatus>

  val player: PlayerType

  val title: String

}