package org.nypl.audiobook.demo.android.with_fragments

import android.widget.ImageView
import org.nypl.audiobook.android.api.PlayerType

interface PlayerFragmentListenerType {

  fun onPlayerWantsPlayer(): PlayerType

  fun onPlayerWantsCoverImage(view: ImageView)

}
