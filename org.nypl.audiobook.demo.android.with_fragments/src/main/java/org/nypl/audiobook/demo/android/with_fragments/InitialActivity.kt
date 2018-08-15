package org.nypl.audiobook.demo.android.with_fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import org.nypl.audiobook.demo.android.with_fragments.PlayerCredentials
import org.nypl.audiobook.demo.android.with_fragments.PlayerParameters
import org.slf4j.LoggerFactory
import java.util.Properties

/**
 * A trivial activity that allows the user to specify various parameters required for downloading
 * a book.
 */

class InitialActivity : Activity() {

  private val LOG = LoggerFactory.getLogger(InitialActivity::class.java)

  private lateinit var feed_username: EditText
  private lateinit var feed_password: EditText
  private lateinit var feed_borrow_uri: EditText
  private lateinit var feed_go: Button
  private lateinit var feed_preset_io7m: Button
  private lateinit var feed_preset_default: Button
  private lateinit var feed_preset_flatland: Button

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)

    this.setContentView(R.layout.initial_view)

    this.feed_username = this.findViewById(R.id.feed_user_name_entry)
    this.feed_password = this.findViewById(R.id.feed_password_entry)
    this.feed_borrow_uri = this.findViewById(R.id.feed_borrow_uri_entry)
    this.feed_go = this.findViewById(R.id.feed_fetch)
    this.feed_preset_io7m = this.findViewById(R.id.feed_preset_io7m)
    this.feed_preset_default = this.findViewById(R.id.feed_preset_default)
    this.feed_preset_flatland = this.findViewById(R.id.feed_preset_flatland)

    val properties = Properties()
    properties.load(this.assets.open("app.properties"))

    this.feed_username.setText(properties.getProperty("feed.user_name"))
    this.feed_password.setText(properties.getProperty("feed.password"))
    this.feed_borrow_uri.setText(properties.getProperty("feed.borrow_uri"))

    this.feed_preset_default.setOnClickListener { button ->
      this.feed_borrow_uri.setText(properties.getProperty("feed.borrow_uri"))
    }
    this.feed_preset_flatland.setOnClickListener { button ->
      this.feed_borrow_uri.setText(R.string.uri_flatland)
    }
    this.feed_preset_io7m.setOnClickListener { button ->
      this.feed_borrow_uri.setText(R.string.uri_io7m)
    }

    this.feed_go.setOnClickListener { button ->
      val user = this.feed_username.text.toString()
      this.LOG.debug("fetch: username {}", user)
      val pass = this.feed_password.text.toString()
      this.LOG.debug("fetch: password {}", pass)
      val uri = this.feed_borrow_uri.text.toString()
      this.LOG.debug("fetch: borrow uri {}", uri)

      val parameters =
        if (!user.isEmpty() && !pass.isEmpty()) {
          PlayerParameters(credentials = PlayerCredentials(user, pass), fetchURI = uri)
        } else {
          PlayerParameters(credentials = null, fetchURI = uri)
        }

      val args = Bundle()
      args.putSerializable(PlayerActivity.FETCH_PARAMETERS_ID, parameters)

      val intent = Intent(this@InitialActivity, PlayerActivity::class.java)
      intent.putExtras(args)
      this.startActivity(intent)
      this.finish()

      button.isEnabled = false
    }
  }
}
