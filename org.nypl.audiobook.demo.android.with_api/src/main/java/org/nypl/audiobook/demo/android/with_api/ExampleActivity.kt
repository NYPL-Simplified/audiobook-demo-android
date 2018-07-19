package org.nypl.audiobook.demo.android.with_api

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import org.nypl.audiobook.android.api.PlayerAudioEngineProviderType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.util.ServiceLoader

class ExampleActivity : Activity() {

  private val log : Logger = LoggerFactory.getLogger(ExampleActivity::class.java)

  private lateinit var providers_text : EditText

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)

    this.setContentView(R.layout.provider_list)

    this.providers_text = this.findViewById(R.id.providers_text)!!

    try {
      val providers = ArrayList<PlayerAudioEngineProviderType>()
      this.log.debug("loading providers")
      val iter = ServiceLoader.load(PlayerAudioEngineProviderType::class.java).iterator()
      while (iter.hasNext()) {
        providers.add(iter.next())
      }
      this.log.debug("finished loading providers")

      if (providers.isEmpty()) {
        this.providers_text.setText("None! Something is wrong.")
      } else {
        val text = StringBuilder(64)
        providers.forEach({ provider ->
          text.append(provider.name())
          text.append(':')
          text.append(provider.version().major)
          text.append('.')
          text.append(provider.version().minor)
          text.append('.')
          text.append(provider.version().patch)
          text.append('\n')
        })
        this.providers_text.setText(text.toString())
      }
    } catch (e: Exception) {
      log.error("failed to load providers: ", e)
      val bao = ByteArrayOutputStream()
      val writer = PrintWriter(bao)
      e.printStackTrace(writer)
      writer.flush()
      this.providers_text.setText(bao.toString("UTF-8"))
      throw e
    }
  }

}