package org.nypl.audiobook.demo.android.with_fragments

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

class PlayerTOCFragment : Fragment() {

  private lateinit var listener: PlayerTOCFragmentListenerType

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    state: Bundle?): View? {
    val view = inflater.inflate(R.layout.player_toc_view, container, false)

    if (view is RecyclerView) {
      with(view) {
        this.layoutManager = LinearLayoutManager(this.context)
        this.adapter = PlayerTOCAdapter(
          this@PlayerTOCFragment.listener.onPlayerTOCWantsBook().spine,
          this@PlayerTOCFragment.listener)
      }
    }
    return view
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)

    if (context is PlayerTOCFragmentListenerType) {
      this.listener = context
    } else {
      throw ClassCastException(
        StringBuilder(64)
          .append("The activity hosting this fragment must implement one or more listener interfaces.\n")
          .append("  Activity: ")
          .append(context::class.java.canonicalName)
          .append('\n')
          .append("  Required interface: ")
          .append(PlayerTOCFragmentListenerType::class.java.canonicalName)
          .append('\n')
          .toString())
    }
  }

  companion object {
    @JvmStatic fun newInstance() = PlayerTOCFragment()
  }
}
