package org.nypl.audiobook.demo.android.main_ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import org.nypl.audiobook.android.api.PlayerAudioEngineProviderType
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.Properties
import java.util.ServiceLoader


/**
 * A trivial activity that allows the user to specify various parameters required for downloading
 * a book.
 */

class ExampleInitialActivity : Activity() {

  private val LOG = LoggerFactory.getLogger(ExampleInitialActivity::class.java)

  private lateinit var feedUsername: EditText
  private lateinit var feedPassword: EditText
  private lateinit var feedBorrowURI: EditText
  private lateinit var feedGo: Button
  private lateinit var feedPresets: Spinner
  private lateinit var feedPresetsItems: Array<FeedPreset>
  private lateinit var feedPresetsAdapter: FeedPresetsAdapter
  private lateinit var feedVersions: TextView

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)

    this.setContentView(R.layout.example_initial_view)

    this.feedPresetsItems = loadPresets()

    this.feedUsername = this.findViewById(R.id.example_feed_user_name_entry)
    this.feedPassword = this.findViewById(R.id.example_feed_password_entry)
    this.feedBorrowURI = this.findViewById(R.id.example_feed_borrow_uri_entry)
    this.feedGo = this.findViewById(R.id.example_feed_fetch)
    this.feedPresets = this.findViewById(R.id.example_feed_presets)

    this.feedPresetsAdapter = FeedPresetsAdapter(this, feedPresetsItems)
    this.feedPresets.adapter = this.feedPresetsAdapter
    this.feedPresetsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    this.feedVersions = this.findViewById(R.id.example_versions)

    this.feedPresets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) {

      }

      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val preset = this@ExampleInitialActivity.feedPresetsItems.get(position)
        this@ExampleInitialActivity.feedBorrowURI.setText(preset.uri.toString())
      }
    }

    this.logProviders(this.feedVersions)

    val properties = Properties()
    properties.load(this.assets.open("app.properties"))

    this.feedUsername.setText(properties.getProperty("feed.user_name"))
    this.feedPassword.setText(properties.getProperty("feed.password"))
    this.feedBorrowURI.setText(properties.getProperty("feed.borrow_uri"))

    this.feedGo.setOnClickListener { button ->
      val user = this.feedUsername.text.toString()
      this.LOG.debug("fetch: username {}", user)
      val pass = this.feedPassword.text.toString()
      this.LOG.debug("fetch: password {}", pass)
      val uri = this.feedBorrowURI.text.toString()
      this.LOG.debug("fetch: borrow uri {}", uri)

      val parameters =
        if (!user.isEmpty() && !pass.isEmpty()) {
          PlayerParameters(credentials = PlayerCredentials(user, pass), fetchURI = uri)
        } else {
          PlayerParameters(credentials = null, fetchURI = uri)
        }

      val args = Bundle()
      args.putSerializable(ExamplePlayerActivity.FETCH_PARAMETERS_ID, parameters)

      val intent = Intent(this@ExampleInitialActivity, ExamplePlayerActivity::class.java)
      intent.putExtras(args)
      this.startActivity(intent)
      this.finish()

      button.isEnabled = false
    }
  }

  private fun loadPresets(): Array<FeedPreset> {
    return arrayOf(
      FeedPreset(
        this.resources.getString(R.string.example_preset_io7m),
        URI.create(this.resources.getString(R.string.example_uri_io7m))),
      FeedPreset(
        this.resources.getString(R.string.example_preset_flatland),
        URI.create(this.resources.getString(R.string.example_uri_flatland))),
      FeedPreset(
        this.resources.getString(R.string.example_preset_librivox),
        URI.create(this.resources.getString(R.string.example_uri_librivox))))
  }

  data class FeedPreset(
    val name: String,
    val uri: java.net.URI)

  class FeedPresetsAdapter(
    context: Context,
    val items: Array<FeedPreset>)
    : ArrayAdapter<FeedPreset>(context, android.R.layout.simple_spinner_item, items) {

    override fun getDropDownView(
      position: Int,
      convertView: View?,
      parent: ViewGroup): View {
      return getCustomView(position, convertView, parent)
    }

    override fun getView(
      position: Int,
      convertView: View?,
      parent: ViewGroup): View {
      return getCustomView(position, convertView, parent)
    }

    private fun getCustomView(
      position: Int,
      convertView: View?,
      parent: ViewGroup): View {

      val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
      val row = inflater.inflate(android.R.layout.simple_spinner_item, parent, false)
      val label = row.findViewById(android.R.id.text1) as TextView
      label.text = this.items.get(position).name
      return row
    }
  }

  private fun logProviders(feedVersions: TextView) {
    val sb = StringBuilder()

    val loader =
      ServiceLoader.load(PlayerAudioEngineProviderType::class.java)

    loader.forEach { provider ->
      LOG.debug("available engine provider: {} {}", provider.name(), provider.version())
      sb.append(provider.name())
      sb.append(" ")
      sb.append(provider.version().toString())
      sb.append("\n")
    }

    feedVersions.text = sb.toString()
  }
}
