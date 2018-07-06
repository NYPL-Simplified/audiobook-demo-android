package org.nypl.audiobook.demo.android.findaway

import org.nypl.audiobook.demo.android.RawManifest
import org.nypl.audiobook.demo.android.RawScalar
import org.nypl.audiobook.demo.android.RawSpineItem
import org.nypl.audiobook.demo.android.Result
import java.lang.StringBuilder

/**
 * A manifest transformed such that it contains information relevant to the Findaway audio engine.
 */

data class PlayerFindawayManifest(
  val title: String,
  val language: String,
  val id: String,
  val accountId: String,
  val checkoutId: String,
  val sessionKey: String,
  val fulfillmentId: String,
  val licenseId: String,
  val spineItems: List<PlayerFindawayManifestSpineItem>) {

  data class PlayerFindawayManifestSpineItem(
    val title: String,
    val part: Int,
    val chapter: Int,
    val type: String,
    val duration: Double)

  companion object {

    private fun valueString(map: Map<String, RawScalar?>, key: String): String {
      return (map[key] ?: throw IllegalArgumentException(
        StringBuilder(128)
          .append("Missing required key.\n")
          .append("  Key: ")
          .append(key)
          .append('\n')
          .toString())).toString()
    }

    private fun valueDouble(map: Map<String, RawScalar?>, key: String): Double {
      try {
        return (map[key] ?: throw IllegalArgumentException(
          StringBuilder(128)
            .append("Missing required key.\n")
            .append("  Key: ")
            .append(key)
            .append('\n')
            .toString())).toString().toDouble()
      } catch (e: NumberFormatException) {
        throw IllegalArgumentException(
          StringBuilder(128)
            .append("Malformed double value.\n")
            .append("  Key: ")
            .append(key)
            .append('\n')
            .toString(), e)
      }
    }

    private fun valueInt(map: Map<String, RawScalar?>, key: String): Int {
      try {
        return (map[key] ?: throw IllegalArgumentException(
          StringBuilder(128)
            .append("Missing required key.\n")
            .append("  Key: ")
            .append(key)
            .append('\n')
            .toString())).toString().toInt()
      } catch (e: NumberFormatException) {
        throw IllegalArgumentException(
          StringBuilder(128)
            .append("Malformed Int value.\n")
            .append("  Key: ")
            .append(key)
            .append('\n')
            .toString(), e)
      }
    }

    fun transform(manifest: RawManifest): Result<PlayerFindawayManifest, Exception> {
      try {
        if (manifest.metadata.encrypted == null) {
          throw IllegalArgumentException("Manifest is missing the required encrypted section")
        }
        val encrypted = manifest.metadata.encrypted
        if (encrypted.scheme != "http://librarysimplified.org/terms/drm/scheme/FAE") {
          throw IllegalArgumentException(
            StringBuilder(128)
              .append("Incorrect scheme.\n")
              .append("  Expected: ")
              .append("http://librarysimplified.org/terms/drm/scheme/FAE")
              .append('\n')
              .append("  Received: ")
              .append(encrypted.scheme)
              .append('\n')
              .toString())
        }

        return Result.Success(PlayerFindawayManifest(
          title = manifest.metadata.title,
          language = manifest.metadata.language,
          id = manifest.metadata.identifier,
          accountId = this.valueString(encrypted.values, "findaway:accountId"),
          checkoutId = this.valueString(encrypted.values, "findaway:checkoutId"),
          fulfillmentId = this.valueString(encrypted.values, "findaway:fulfillmentId"),
          licenseId = this.valueString(encrypted.values, "findaway:licenseId"),
          sessionKey = this.valueString(encrypted.values, "findaway:sessionKey"),
          spineItems = manifest.spine.map { item -> this.processSpineItem(item) }))
      } catch (e: Exception) {
        return Result.Failure(e)
      }
    }

    private fun processSpineItem(item: RawSpineItem): PlayerFindawayManifestSpineItem {
      return PlayerFindawayManifestSpineItem(
        title = this.valueString(item.values, "title"),
        type = this.valueString(item.values, "type"),
        duration = this.valueDouble(item.values, "duration"),
        chapter = this.valueInt(item.values, "findaway:sequence"),
        part = this.valueInt(item.values, "findaway:part"))
    }
  }
}
