package org.nypl.audiobook.demo.android.with_fragments

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.nypl.audiobook.android.api.PlayerAudioBookType
import org.nypl.audiobook.android.api.PlayerEvent
import org.nypl.audiobook.android.api.PlayerSpineElementDownloadStatus
import org.nypl.audiobook.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloadFailed
import org.nypl.audiobook.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloaded
import org.nypl.audiobook.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloading
import org.nypl.audiobook.android.api.PlayerSpineElementDownloadStatus.PlayerSpineElementNotDownloaded
import org.nypl.audiobook.android.api.PlayerSpineElementType
import org.nypl.audiobook.android.api.PlayerType
import org.slf4j.LoggerFactory
import rx.Subscription

class PlayerTOCFragment : Fragment() {

  private val log = LoggerFactory.getLogger(PlayerTOCFragment::class.java)
  private lateinit var listener: PlayerFragmentListenerType
  private lateinit var adapter: PlayerTOCAdapter
  private lateinit var book: PlayerAudioBookType
  private lateinit var player: PlayerType
  private var bookSubscription: Subscription? = null
  private var playerSubscription: Subscription? = null
  private lateinit var parameters: PlayerTOCFragmentParameters

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    state: Bundle?): View? {

    val view: RecyclerView =
      inflater.inflate(R.layout.player_toc_view, container, false) as RecyclerView

    view.layoutManager = LinearLayoutManager(view.context)
    view.setHasFixedSize(true)
    view.adapter = this.adapter

    return view
  }


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    this.parameters =
      this.arguments!!.getSerializable(PlayerTOCFragment.parametersKey)
        as PlayerTOCFragmentParameters
  }

  override fun onDestroy() {
    super.onDestroy()

    this.bookSubscription?.unsubscribe()
    this.playerSubscription?.unsubscribe()

    this.listener.onPlayerTOCClosed()
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)

    if (context is PlayerFragmentListenerType) {
      this.listener = context

      this.book = this.listener.onPlayerTOCWantsBook()
      this.player = this.listener.onPlayerWantsPlayer()

      this.adapter =
        PlayerTOCAdapter(
          context = context,
          spineElements = this.book.spine,
          onSelect = { item -> this.onTOCItemSelected(item) })

      this.bookSubscription =
        this.book.spineElementDownloadStatus.subscribe(
          { status -> this.onSpineElementStatusChanged(status) },
          { error -> this.onSpineElementStatusError(error) },
          { })

      this.playerSubscription =
        this.player.events.subscribe(
          { event -> this.onPlayerEvent(event) },
          { error -> this.onPlayerError(error) },
          { })

    } else {
      throw ClassCastException(
        StringBuilder(64)
          .append("The activity hosting this fragment must implement one or more listener interfaces.\n")
          .append("  Activity: ")
          .append(context::class.java.canonicalName)
          .append('\n')
          .append("  Required interface: ")
          .append(PlayerFragmentListenerType::class.java.canonicalName)
          .append('\n')
          .toString())
    }
  }

  private fun onTOCItemSelected(item: PlayerSpineElementType) {
    this.log.debug("onTOCItemSelected: ", item.index)

    return when (item.downloadStatus) {
      is PlayerSpineElementNotDownloaded ->
        if (this.book.supportsStreaming) {
          this.playItemAndClose(item)
        } else {

        }

      is PlayerSpineElementDownloading ->
        if (this.book.supportsStreaming) {
          this.playItemAndClose(item)
        } else {

        }

      is PlayerSpineElementDownloaded ->
        this.playItemAndClose(item)

      is PlayerSpineElementDownloadFailed ->
        if (this.book.supportsStreaming) {
          this.playItemAndClose(item)
        } else {

        }
    }
  }

  private fun playItemAndClose(item: PlayerSpineElementType) {
    this.player.playAtLocation(item.position)
    this.closeTOC()
  }

  private fun closeTOC() {
    this.listener.onPlayerTOCWantsClose()
  }

  private fun onPlayerError(error: Throwable) {
    this.log.error("onPlayerError: ", error)
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    return when (event) {
      is PlayerEvent.PlayerEventPlaybackRateChanged -> Unit
      is PlayerEvent.PlayerEventWithSpineElement ->
        this.onPlayerSpineElement(event.spineElement.index)
    }
  }

  private fun onPlayerSpineElement(index: Int) {
    UIThread.runOnUIThread(Runnable {
      this.adapter.setCurrentSpineElement(index)
    })
  }

  private fun onSpineElementStatusError(error: Throwable?) {
    this.log.error("onSpineElementStatusError: ", error)
  }

  private fun onSpineElementStatusChanged(status: PlayerSpineElementDownloadStatus) {
    UIThread.runOnUIThread(Runnable {
      val spineElement = status.spineElement
      this.adapter.notifyItemChanged(spineElement.index)
    })
  }

  companion object {

    private val parametersKey = "org.nypl.audiobook.demo.android.with_fragments.PlayerFragmentParameters"

    @JvmStatic
    fun newInstance(parameters: PlayerTOCFragmentParameters): PlayerTOCFragment {
      val args = Bundle()
      args.putSerializable(this.parametersKey, parameters)
      val fragment = PlayerTOCFragment()
      fragment.arguments = args
      return fragment
    }
  }
}
