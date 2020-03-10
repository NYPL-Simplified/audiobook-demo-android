package org.librarysimplified.audiobook.demo.main_ui

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerAudioEngines
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerSleepTimer
import org.librarysimplified.audiobook.api.PlayerSleepTimerType
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.downloads.DownloadProvider
import org.librarysimplified.audiobook.feedbooks.FeedbooksParserExtensions
import org.librarysimplified.audiobook.license_check.api.LicenseChecks
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckProviderType
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_fulfill.api.ManifestFulfillmentStrategies
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicCredentials
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicParameters
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicType
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvent
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.audiobook.views.PlayerAccessibilityEvent
import org.librarysimplified.audiobook.views.PlayerFragment
import org.librarysimplified.audiobook.views.PlayerFragmentListenerType
import org.librarysimplified.audiobook.views.PlayerFragmentParameters
import org.librarysimplified.audiobook.views.PlayerPlaybackRateFragment
import org.librarysimplified.audiobook.views.PlayerSleepTimerFragment
import org.librarysimplified.audiobook.views.PlayerTOCFragment
import org.librarysimplified.audiobook.views.PlayerTOCFragmentParameters
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.util.ServiceLoader
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ExamplePlayerActivity : AppCompatActivity(), PlayerFragmentListenerType {

  private val log = LoggerFactory.getLogger(ExamplePlayerActivity::class.java)

  companion object {
    const val FETCH_PARAMETERS_ID =
      "org.nypl.audiobook.demo.android.with_fragments.PlayerActivity.PARAMETERS_ID"
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
    this.supportActionBar?.setTitle(R.string.exAppName)

    /*
     * Create executors for download threads, and for scheduling UI events. Each thread is
     * assigned a useful name for correct blame assignment during debugging.
     */

    this.downloadExecutor =
      MoreExecutors.listeningDecorator(
        Executors.newFixedThreadPool(4) { r: Runnable? ->
          val thread = Thread(r)
          thread.name = "org.librarysimplified.audiobook.demo.main_ui.downloader-${thread.id}"
          thread
        })

    this.uiScheduledExecutor =
      Executors.newSingleThreadScheduledExecutor { r: Runnable? ->
        val thread = Thread(r)
        thread.name = "org.librarysimplified.audiobook.demo.main_ui.ui-schedule-${thread.id}"
        thread
      }

    this.sleepTimer = PlayerSleepTimer.create()

    /*
     * Create a fragment that shows an indefinite progress bar whilst we do the work
     * of downloading and parsing manifests.
     */

    if (state == null) {
      this.examplePlayerFetchingFragment = ExamplePlayerFetchingFragment.newInstance()

      this.supportFragmentManager
        .beginTransaction()
        .replace(
          R.id.example_player_fragment_holder,
          this.examplePlayerFetchingFragment,
          "PLAYER_FETCHING"
        )
        .commit()
    }

    val args =
      this.intent.extras ?: throw IllegalStateException("No arguments passed to activity")
    val parameters: ExamplePlayerParameters =
      args.getSerializable(FETCH_PARAMETERS_ID) as ExamplePlayerParameters

    /*
     * Start the manifest asynchronously downloading in the background. When the
     * download/parsing operation completes, we will open an audio player using the
     * parsed manifest.
     */

    val manifestFuture =
      this.downloadExecutor.submit(Callable {
        return@Callable this.downloadAndParseManifestShowingErrors(parameters)
      })

    manifestFuture.addListener(
      Runnable {
        try {
          this.openPlayerForManifest(manifestFuture.get(3L, TimeUnit.SECONDS))
        } catch (e: Exception) {
          this.log.error("error downloading manifest: ", e)
        }
      },
      MoreExecutors.directExecutor()
    )
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
        this.log.error("error closing player: ", e)
      }

      try {
        this.book.close()
      } catch (e: Exception) {
        this.log.error("error closing book: ", e)
      }
    }
  }

  /**
   * Attempt to parse a manifest file.
   */

  private fun parseManifest(
    source: URI,
    data: ByteArray
  ): ParseResult<PlayerManifest> {
    this.log.debug("parseManifest")

    val extensions =
      ServiceLoader.load(ManifestParserExtensionType::class.java)
        .toList()

    return ManifestParsers.parse(
      uri = source,
      streams = data,
      extensions = extensions
    )
  }

  /**
   * Attempt to synchronously download a manifest file. If the download fails, return the
   * error details.
   */

  private fun downloadManifest(
    parameters: ExamplePlayerParameters
  ): PlayerResult<ByteArray, ManifestFulfillmentErrorType> {
    this.log.debug("downloadManifest")

    val credentials = parameters.credentials

    val strategies =
      ManifestFulfillmentStrategies.findStrategy(ManifestFulfillmentBasicType::class.java)
        ?: throw UnsupportedOperationException()

    val fulfillCredentials =
      if (credentials is ExamplePlayerCredentials.Basic) {
        ManifestFulfillmentBasicCredentials(
          userName = credentials.userName,
          password = credentials.password
        )
      } else {
        null
      }

    val strategy =
      strategies.create(
        ManifestFulfillmentBasicParameters(
          uri = URI.create(parameters.fetchURI),
          credentials = fulfillCredentials
        )
      )

    val fulfillSubscription =
      strategy.events.subscribe { event ->
        this.onManifestFulfillmentEvent(event)
      }

    try {
      return strategy.execute()
    } finally {
      fulfillSubscription.unsubscribe()
    }
  }

  private fun onManifestFulfillmentEvent(event: ManifestFulfillmentEvent) {
    ExampleUIThread.runOnUIThread(Runnable {
      this.examplePlayerFetchingFragment.setMessageText(event.message)
    })
  }

  /**
   * Attempt to perform any required license checks on the manifest.
   */

  private fun checkManifest(
    manifest: PlayerManifest
  ): Boolean {
    val singleChecks =
      ServiceLoader.load(SingleLicenseCheckProviderType::class.java)
        .toList()
    val check =
      LicenseChecks.createLicenseCheck(manifest, singleChecks)

    val checkSubscription =
      check.events.subscribe { event ->
      this.onLicenseCheckEvent(event)
    }

    try {
      val checkResult = check.execute()
      return checkResult.checkSucceeded()
    } finally {
      checkSubscription.unsubscribe()
    }
  }

  private fun onLicenseCheckEvent(event: SingleLicenseCheckStatus) {
    ExampleUIThread.runOnUIThread(Runnable {
      this.examplePlayerFetchingFragment.setMessageText(event.message)
    })
  }

  /**
   * Attempt to download and parse the audio book manifest. This composes [downloadManifest]
   * [parseManifest], and [checkManifest], with the main difference that errors are logged to the
   * UI thread on failure and the activity is closed.
   */

  private fun downloadAndParseManifestShowingErrors(
    parameters: ExamplePlayerParameters
  ): PlayerManifest {
    this.log.debug("downloadAndParseManifestShowingErrors")

    val downloadResult = this.downloadManifest(parameters)
    if (downloadResult is PlayerResult.Failure) {
      val exception = IOException()
      ExampleErrorDialogUtilities.showErrorWithRunnable(
        this@ExamplePlayerActivity,
        this.log,
        "Failed to download manifest: ${downloadResult.failure.message}",
        exception,
        Runnable {
          this.finish()
        }
      )
      throw exception
    }

    val (downloadBytes) = downloadResult as PlayerResult.Success
    val parseResult = this.parseManifest(URI.create(parameters.fetchURI), downloadBytes)
    if (parseResult is ParseResult.Failure) {
      val exception = IOException()
      ExampleErrorDialogUtilities.showErrorWithRunnable(
        this@ExamplePlayerActivity,
        this.log,
        "Failed to parse manifest: ${parseResult.errors[0].message}",
        exception,
        Runnable {
          this.finish()
        }
      )
      throw exception
    }

    val (_, parsedManifest) = parseResult as ParseResult.Success
    if (!checkManifest(parsedManifest)) {
      val exception = IOException()
      ExampleErrorDialogUtilities.showErrorWithRunnable(
        this@ExamplePlayerActivity,
        this.log,
        "One or more license checks failed for the audio book manifest.",
        exception,
        Runnable {
          this.finish()
        }
      )
      throw exception
    }

    return parsedManifest
  }

  private fun openPlayerForManifest(manifest: PlayerManifest) {
    this.log.debug("openPlayerForManifest")

    /*
     * Ask the API for the best audio engine available that can handle the given manifest.
     */

    val engine =
      PlayerAudioEngines.findBestFor(
        PlayerAudioEngineRequest(
          manifest = manifest,
          filter = { true },
          downloadProvider = DownloadProvider.create(this.downloadExecutor)
        )
      )

    if (engine == null) {
      ExampleErrorDialogUtilities.showErrorWithRunnable(
        this@ExamplePlayerActivity,
        this.log,
        "No audio engine available to handle the given book",
        null,
        Runnable {
          this.finish()
        }
      )
      return
    }

    this.log.debug(
      "selected audio engine: {} {}",
      engine.engineProvider.name(),
      engine.engineProvider.version()
    )

    /*
     * Configure any extensions that we want to use.
     */

    val extensions =
      ServiceLoader.load(PlayerExtensionType::class.java)
        .toList()

    /*
     * Create the audio book.
     */

    val bookResult =
      engine.bookProvider.create(
        context = this,
        extensions = extensions
      )

    if (bookResult is PlayerResult.Failure) {
      ExampleErrorDialogUtilities.showErrorWithRunnable(
        this@ExamplePlayerActivity,
        this.log,
        "Error parsing manifest",
        bookResult.failure,
        Runnable {
          this.finish()
        }
      )
      return
    }

    this.book = (bookResult as PlayerResult.Success).result
    this.bookTitle = manifest.metadata.title
    this.bookAuthor = "Unknown Author"
    this.player = this.book.createPlayer()
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
      this.supportActionBar?.setTitle(R.string.exTableOfContents)

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
    this.supportFragmentManager.popBackStack()
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
