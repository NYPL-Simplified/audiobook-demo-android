package org.nypl.audiobook.demo.android.with_fragments

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.nypl.audiobook.android.api.PlayerSpineElementType

class PlayerTOCAdapter(
  private val spineElements: List<PlayerSpineElementType>,
  private val interactionListener: PlayerTOCFragmentListenerType)
  : RecyclerView.Adapter<PlayerTOCAdapter.ViewHolder>() {

  private val listener: View.OnClickListener

  init {
    this.listener = View.OnClickListener { v ->
      val item = v.tag as PlayerSpineElementType
      this.interactionListener.onPlayerTOCListInteraction(item)
    }
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int): ViewHolder {

    val view =
      LayoutInflater.from(parent.context)
      .inflate(R.layout.player_toc_item_view, parent, false)
    return this.ViewHolder(view)
  }

  override fun onBindViewHolder(
    holder: ViewHolder,
    position: Int) {

    val item = this.spineElements[position]

    with(holder.view) {
      this.tag = item
      this.setOnClickListener(this@PlayerTOCAdapter.listener)
    }
  }

  override fun getItemCount(): Int = this.spineElements.size

  inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {

  }
}
