package org.nypl.audiobook.demo.android.main_ui

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.support.v7.app.AppCompatActivity
import android.widget.ImageView
import android.widget.Toast
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.nypl.audiobook.android.api.PlayerAudioBookType
import org.nypl.audiobook.android.api.PlayerAudioEngineRequest
import org.nypl.audiobook.android.api.PlayerAudioEngines
import org.nypl.audiobook.android.api.PlayerManifest
import org.nypl.audiobook.android.api.PlayerManifests
import org.nypl.audiobook.android.api.PlayerResult
import org.nypl.audiobook.android.api.PlayerSleepTimer
import org.nypl.audiobook.android.api.PlayerSleepTimerType
import org.nypl.audiobook.android.api.PlayerType
import org.nypl.audiobook.android.downloads.DownloadProvider
import org.nypl.audiobook.android.views.PlayerAccessibilityEvent
import org.nypl.audiobook.android.views.PlayerFragment
import org.nypl.audiobook.android.views.PlayerFragmentListenerType
import org.nypl.audiobook.android.views.PlayerFragmentParameters
import org.nypl.audiobook.android.views.PlayerPlaybackRateFragment
import org.nypl.audiobook.android.views.PlayerSleepTimerFragment
import org.nypl.audiobook.android.views.PlayerTOCFragment
import org.nypl.audiobook.android.views.PlayerTOCFragmentParameters
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class ExamplePlayerActivity : AppCompatActivity(), PlayerFragmentListenerType {

  private val log = LoggerFactory.getLogger(ExamplePlayerActivity::class.java)

  companion object {
    const val FETCH_PARAMETERS_ID =
      "org.nypl.audiobook.demo.android.with_fragments.PlayerActivity.PARAMETERS_ID"
  }

  /**
   * A runnable that finishes this activity and goes back to the initial one.
   */

  private val GO_BACK_TO_INITIAL_ACTIVITY = Runnable {
    val intent = Intent(this@ExamplePlayerActivity, ExampleInitialActivity::class.java)
    this.startActivity(intent)
    this.finish()
  }

  private lateinit var examplePlayerFetchingFragment: ExamplePlayerFetchingFragment
  private lateinit var downloadExecutor: ListeningExecutorService
  private lateinit var uiScheduledExecutor: ScheduledExecutorService
  private lateinit var playerFragment: PlayerFragment
  private lateinit var player: PlayerType
  private var playerInitialized: Boolean = false
  private lateinit var book: PlayerAudioBookType
  private lateinit var bookTitle: String
  private lateinit var bookAuthor: String
  private lateinit var sleepTimer: PlayerSleepTimerType

  override fun onCreate(state: Bundle?) {
    super.onCreate(null)

    this.setTheme(R.style.AudioBooksWithActionBar)
    this.setContentView(R.layout.example_player_activity)
    this.supportActionBar?.setTitle(R.string.example_player_title)

    /*
     * Create an executor for download threads, and for scheduling UI events. Each thread is
     * assigned a useful name for correct blame assignment during debugging.
     */

    this.downloadExecutor =
      MoreExecutors.listeningDecorator(
        Executors.newFixedThreadPool(4) { r: Runnable? ->
          val thread = Thread(r)
          thread.name = "org.nypl.audiobook.demo.android.with_fragments.downloader-${thread.id}"
          thread
        })

    this.uiScheduledExecutor =
      Executors.newSingleThreadScheduledExecutor { r: Runnable? ->
        val thread = Thread(r)
        thread.name = "org.nypl.audiobook.demo.android.with_fragments.ui-schedule-${thread.id}"
        thread
      }

    this.sleepTimer = PlayerSleepTimer.create()

    if (state == null) {
      this.examplePlayerFetchingFragment = ExamplePlayerFetchingFragment.newInstance()

      this.supportFragmentManager
        .beginTransaction()
        .replace(R.id.example_player_fragment_holder, this.examplePlayerFetchingFragment, "PLAYER_FETCHING")
        .commit()
    }

    val args = this.intent.extras
    if (args == null) {
      throw IllegalStateException("No arguments passed to activity")
    }

    val parameters: PlayerParameters =
      args.getSerializable(ExamplePlayerActivity.FETCH_PARAMETERS_ID) as PlayerParameters

    this.doInitialManifestRequest(parameters)
  }

  override fun onDestroy() {
    super.onDestroy()

    try {
      this.downloadExecutor.shutdown()
    } catch (e: Exception) {
      this.log.error("error shutting down download executor: ", e)
    }

    try {
      this.sleepTimer.close()
    } catch (e: Exception) {
      this.log.error("error shutting down sleep timer: ", e)
    }

    if (this.playerInitialized) {
      try {
        this.player.close()
      } catch (e: Exception) {
        this.log.error("error shutting down player: ", e)
      }
    }
  }

  /**
   * Fetch the manifest from the remote server.
   */

  private fun doInitialManifestRequest(parameters: PlayerParameters) {
    val client = OkHttpClient()

    val request_builder =
      Request.Builder()
        .url(parameters.fetchURI)

    /*
     * Use basic auth if a username and password were given.
     */

    val credentials = parameters.credentials
    if (credentials != null) {
      request_builder.header(
        "Authorization",
        Credentials.basic(credentials.user, credentials.password))
    }

    val request = request_builder.build()

    this.log.debug("fetching {}", parameters.fetchURI)

    val call = client.newCall(request)
    call.enqueue(object : Callback {
      override fun onFailure(call: Call?, e: IOException?) {
        this@ExamplePlayerActivity.onURIFetchFailure(e)
      }

      override fun onResponse(call: Call?, response: Response?) {
        this@ExamplePlayerActivity.onURIFetchSuccess(response!!)
      }
    })
  }

  private fun onURIFetchFailure(e: IOException?) {
    ExampleErrorDialogUtilities.showErrorWithRunnable(
      this@ExamplePlayerActivity,
      this.log,
      "Failed to fetch URI",
      e,
      this.GO_BACK_TO_INITIAL_ACTIVITY)
  }

  /**
   * Fetching the manifest was successful.
   */

  private fun onURIFetchSuccess(response: Response) {
    this.log.debug("onURIFetchSuccess: {}", response)

    ExampleUIThread.runOnUIThread(Runnable {
      this.examplePlayerFetchingFragment.setMessageTextId(R.string.example_fetch_processing_manifest)
    })

    /*
     * If the manifest can be parsed, parse it and update the player. Otherwise fail loudly.
     */

    if (response.isSuccessful) {
      val stream = response.body()!!.byteStream()
      stream.use { _ ->
        val result = PlayerManifests.parse(stream)
        when (result) {
          is PlayerResult.Success -> {
            this.onProcessManifest(result.result)
          }
          is PlayerResult.Failure -> {
            ExampleErrorDialogUtilities.showErrorWithRunnable(
              this@ExamplePlayerActivity,
              this.log,
              "Failed to parse manifest",
              result.failure,
              this.GO_BACK_TO_INITIAL_ACTIVITY)
          }
        }
      }
    } else {
      ExampleErrorDialogUtilities.showErrorWithRunnable(
        this@ExamplePlayerActivity,
        this.log,
        "Server returned a failure message: " + response.code() + " " + response.message(),
        null,
        this.GO_BACK_TO_INITIAL_ACTIVITY)
    }
  }

  private fun onProcessManifest(manifest: PlayerManifest) {
    this.log.debug("onProcessManifest")

    /*
     * Ask the API for the best audio engine available that can handle the given manifest.
     */

    val engine = PlayerAudioEngines.findBestFor(
      PlayerAudioEngineRequest(
        manifest = manifest,
        filter = { true },
        downloadProvider = DownloadProvider.create(this.downloadExecutor)))

    if (engine == null) {
      ExampleErrorDialogUtilities.showErrorWithRunnable(
        this@ExamplePlayerActivity,
        this.log,
        "No audio engine available to handle the given book",
        null,
        this.GO_BACK_TO_INITIAL_ACTIVITY)
      return
    }

    this.log.debug(
      "selected audio engine: {} {}",
      engine.engineProvider.name(),
      engine.engineProvider.version())

    /*
     * Create the audio book.
     */

    val bookResult = engine.bookProvider.create(this)
    if (bookResult is PlayerResult.Failure) {
      ExampleErrorDialogUtilities.showErrorWithRunnable(
        this@ExamplePlayerActivity,
        this.log,
        "Error parsing manifest",
        bookResult.failure,
        this.GO_BACK_TO_INITIAL_ACTIVITY)
      return
    }

    this.book = (bookResult as PlayerResult.Success).result
    this.bookTitle = manifest.metadata.title
    this.bookAuthor = "Unknown Author"
    this.player = book.createPlayer()
    this.playerInitialized = true

    /*
     * Create and load the main player fragment into the holder view declared in the activity.
     */

    ExampleUIThread.runOnUIThread(Runnable {
      this.playerFragment = PlayerFragment.newInstance(PlayerFragmentParameters())

      this.supportFragmentManager
        .beginTransaction()
        .replace(R.id.example_player_fragment_holder, this.playerFragment, "PLAYER")
        .commit()
    })
  }

  override fun onPlayerWantsPlayer(): PlayerType {
    this.log.debug("onPlayerWantsPlayer")
    return this.player
  }

  override fun onPlayerWantsCoverImage(view: ImageView) {
    this.log.debug("onPlayerWantsCoverImage: {}", view)
    view.setImageResource(R.drawable.example_cover)
    view.setOnLongClickListener {
      val toast = Toast.makeText(this, "Deleted local book data", Toast.LENGTH_SHORT)
      toast.show()
      this.book.wholeBookDownloadTask.delete()
      true
    }
  }

  override fun onPlayerTOCWantsBook(): PlayerAudioBookType {
    this.log.debug("onPlayerTOCWantsBook")
    return this.book
  }

  override fun onPlayerTOCShouldOpen() {
    this.log.debug("onPlayerTOCShouldOpen")

    /*
     * The player fragment wants us to open the table of contents. Load and display it, and
     * also set the action bar title.
     */

    ExampleUIThread.runOnUIThread(Runnable {
      this.supportActionBar?.setTitle(R.string.example_player_toc_title)

      val fragment =
        PlayerTOCFragment.newInstance(PlayerTOCFragmentParameters())

      this.supportFragmentManager
        .beginTransaction()
        .replace(R.id.example_player_fragment_holder, fragment, "PLAYER_TOC")
        .addToBackStack(null)
        .commit()
    })
  }

  override fun onPlayerSleepTimerShouldOpen() {
    this.log.debug("onPlayerSleepTimerShouldOpen")

    /*
     * The player fragment wants us to open the sleep timer.
     */

    ExampleUIThread.runOnUIThread(Runnable {
      val fragment =
        PlayerSleepTimerFragment.newInstance(PlayerFragmentParameters())
      fragment.show(this.supportFragmentManager, "PLAYER_SLEEP_TIMER")
    })
  }

  override fun onPlayerPlaybackRateShouldOpen() {
    this.log.debug("onPlayerPlaybackRateShouldOpen")

    /*
     * The player fragment wants us to open the playback rate selection dialog.
     */

    ExampleUIThread.runOnUIThread(Runnable {
      val fragment =
        PlayerPlaybackRateFragment.newInstance(PlayerFragmentParameters())
      fragment.show(this.supportFragmentManager, "PLAYER_RATE")
    })
  }

  override fun onPlayerTOCWantsClose() {
    this.log.debug("onPlayerTOCWantsClose")

    /*
     * The player fragment wants to close the table of contents dialog. Pop it from the back
     * stack and set the action bar title back to the original title.
     */

    this.supportFragmentManager.popBackStack()
    this.actionBar.setTitle(R.string.example_player_title)
  }

  override fun onPlayerWantsTitle(): String {
    this.log.debug("onPlayerWantsTitle")
    return this.bookTitle
  }

  override fun onPlayerWantsAuthor(): String {
    this.log.debug("onPlayerWantsAuthor")
    return this.bookAuthor
  }

  override fun onPlayerWantsSleepTimer(): PlayerSleepTimerType {
    this.log.debug("onPlayerWantsSleepTimer")
    return this.sleepTimer
  }

  override fun onPlayerWantsScheduledExecutor(): ScheduledExecutorService {
    return this.uiScheduledExecutor
  }

  override fun onPlayerAccessibilityEvent(event: PlayerAccessibilityEvent) {
    ExampleUIThread.runOnUIThread(Runnable {
      val toast = Toast.makeText(this.applicationContext, event.message, Toast.LENGTH_LONG)
      toast.show()
    })
  }
}
