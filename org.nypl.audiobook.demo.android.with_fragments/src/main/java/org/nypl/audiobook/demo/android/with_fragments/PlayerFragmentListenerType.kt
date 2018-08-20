package org.nypl.audiobook.demo.android.with_fragments

import android.widget.ImageView
import org.nypl.audiobook.android.api.PlayerAudioBookType
import org.nypl.audiobook.android.api.PlayerType

interface PlayerFragmentListenerType {

  fun onPlayerWantsPlayer(): PlayerType

  fun onPlayerWantsCoverImage(view: ImageView)

  fun onPlayerWantsTitle(): String

  fun onPlayerWantsAuthor(): String

  fun onPlayerTOCShouldOpen()

  fun onPlayerTOCWantsBook(): PlayerAudioBookType

  fun onPlayerTOCClosed()

  fun onPlayerTOCWantsClose()

  fun onPlayerPlaybackRateShouldOpen()
}
