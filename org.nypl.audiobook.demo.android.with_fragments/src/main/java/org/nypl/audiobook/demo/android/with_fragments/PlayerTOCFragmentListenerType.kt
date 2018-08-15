package org.nypl.audiobook.demo.android.with_fragments

import org.nypl.audiobook.android.api.PlayerAudioBookType
import org.nypl.audiobook.android.api.PlayerSpineElementType

interface PlayerTOCFragmentListenerType {

  fun onPlayerTOCWantsBook(): PlayerAudioBookType

  fun onPlayerTOCListInteraction(item: PlayerSpineElementType)

}