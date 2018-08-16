package org.nypl.audiobook.demo.android.with_fragments

import android.widget.ImageView
import org.nypl.audiobook.android.api.PlayerAudioBookType
import org.nypl.audiobook.android.api.PlayerSpineElementType
import org.nypl.audiobook.android.api.PlayerType

interface PlayerFragmentListenerType {

  fun onPlayerWantsPlayer(): PlayerType

  fun onPlayerWantsCoverImage(view: ImageView)

  fun onPlayerTOCWantsBook(): PlayerAudioBookType

  fun onPlayerTOCListInteraction(item: PlayerSpineElementType)

}
