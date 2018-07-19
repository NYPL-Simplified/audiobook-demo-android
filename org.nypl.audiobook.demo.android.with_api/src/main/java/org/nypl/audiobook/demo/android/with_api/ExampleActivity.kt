package org.nypl.audiobook.demo.android.with_api

import android.app.Activity
import android.os.Bundle
import org.nypl.audiobook.android.api.PlayerAudioEngineProviderType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ServiceLoader

class ExampleActivity : Activity() {

  private val log : Logger = LoggerFactory.getLogger(ExampleActivity::class.java)

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)

    this.log.debug("loading providers")
    val iter = ServiceLoader.load(PlayerAudioEngineProviderType::class.java).iterator()
    while (iter.hasNext()) {
      val provider = iter.next()
      this.log.debug("got provider: {}", provider)
    }
    this.log.debug("finished loading providers")
  }

}