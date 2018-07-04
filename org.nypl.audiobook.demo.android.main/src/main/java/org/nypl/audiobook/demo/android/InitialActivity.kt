package org.nypl.audiobook.demo.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import org.nypl.audiobook.demo.android.main.R
import org.slf4j.LoggerFactory
import java.util.*

/**
 * A trivial activity that allows the user to specify various parameters required for downloading
 * a book.
 */

class InitialActivity : Activity() {

  private val LOG = LoggerFactory.getLogger(InitialActivity::class.java)

  private var feed_username: EditText? = null
  private var feed_password: EditText? = null
  private var feed_borrow_uri: EditText? = null
  private var feed_go: Button? = null

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)

    this.setContentView(R.layout.initial_view)

    this.feed_username = this.findViewById(R.id.feed_user_name_entry)
    this.feed_password = this.findViewById(R.id.feed_password_entry)
    this.feed_borrow_uri = this.findViewById(R.id.feed_borrow_uri_entry)
    this.feed_go = this.findViewById(R.id.feed_fetch)

    val properties = Properties()
    properties.load(this.assets.open("app.properties"))

    this.feed_username!!.setText(properties.getProperty("feed.user_name"))
    this.feed_password!!.setText(properties.getProperty("feed.password"))
    this.feed_borrow_uri!!.setText(properties.getProperty("feed.borrow_uri"))

    this.feed_go!!.setOnClickListener { button ->
      val user = this.feed_username!!.text.toString()
      this.LOG.debug("fetch: username {}", user)
      val pass = this.feed_password!!.text.toString()
      this.LOG.debug("fetch: password {}", pass)
      val uri = this.feed_borrow_uri!!.text.toString()
      this.LOG.debug("fetch: borrow uri {}", uri)
      button.isEnabled = false

      val parameters =
        PlayerParameters(user = user, password = pass, fetchURI = uri)

      val args = Bundle()
      args.putSerializable(PlayerActivity.FETCH_PARAMETERS_ID, parameters)

      val intent = Intent(this@InitialActivity, PlayerActivity::class.java)
      intent.putExtras(args)
      this.startActivity(intent)
      this.finish()
    }
  }
}
