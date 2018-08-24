package org.nypl.audiobook.demo.android.main_ui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * A mindlessly simple fragment that just shows a piece of explanatory text and a progress
 * indicator.
 */

class ExamplePlayerFetchingFragment : Fragment() {

  companion object {
    fun newInstance(): ExamplePlayerFetchingFragment {
      return ExamplePlayerFetchingFragment()
    }
  }

  private lateinit var fetchText: TextView

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    state: Bundle?): View? {
    return inflater.inflate(R.layout.example_player_fetch_view, container, false)
  }

  override fun onViewCreated(view: View, state: Bundle?) {
    super.onViewCreated(view, state)
    this.fetchText = view.findViewById(R.id.example_fetch_text)!!
  }

  override fun onSaveInstanceState(state: Bundle) {
    super.onSaveInstanceState(state)
    state.putString("text", this.fetchText.text.toString())
  }

  override fun onActivityCreated(state: Bundle?) {
    super.onActivityCreated(state)

    if (state != null) {
      this.fetchText.text = state.getString("text")
    }
  }

  fun setMessageTextId(textId: Int) {
    this.fetchText.setText(textId)
  }
}
