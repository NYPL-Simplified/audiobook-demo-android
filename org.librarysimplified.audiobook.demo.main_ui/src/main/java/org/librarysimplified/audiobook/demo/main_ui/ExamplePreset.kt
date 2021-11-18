package org.librarysimplified.audiobook.demo.main_ui

import android.content.Context
import org.librarysimplified.audiobook.json_web_token.JSONBase64String
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAPassword
import org.xmlpull.v1.XmlPullParser
import java.net.URI

/**
 * Bundled presets for testing various audio books.
 */

data class ExamplePreset(
  val name: String,
  val uri: URI,
  val credentials: ExamplePlayerCredentials
) {

  companion object {

    /**
     * Parse bundled repositories from XML resources.
     */

    fun fromXMLResources(context: Context): List<ExamplePreset> {
      return loadFrom(context.resources.getXml(R.xml.presets))
    }

    /**
     * Load presets from the given XML parser.
     */

    fun loadFrom(
      parser: XmlPullParser
    ): List<ExamplePreset> {
      var name = ""
      var location = ""
      var credentials = ExamplePlayerCredentials.None(23) as ExamplePlayerCredentials
      val presets = mutableListOf<ExamplePreset>()

      while (true) {
        when (parser.next()) {
          XmlPullParser.END_DOCUMENT ->
            return presets.toList()

          XmlPullParser.START_TAG ->
            when (parser.name) {
              "Presets" -> {

              }

              "Preset" -> {
                name = parser.getAttributeValue(null, "name")
                location = parser.getAttributeValue(null, "location")
              }

              "AuthenticationBasic" -> {
                credentials = ExamplePlayerCredentials.Basic(
                  userName = parser.getAttributeValue(null, "userName"),
                  password = parser.getAttributeValue(null, "password")
                )
              }

              "Overdrive" -> {
                credentials = ExamplePlayerCredentials.Overdrive(
                  userName = parser.getAttributeValue(null, "userName"),
                  password = OPAPassword.Password(parser.getAttributeValue(null, "password")),
                  clientKey = parser.getAttributeValue(null, "clientKey"),
                  clientPass = parser.getAttributeValue(null, "clientSecret")
                )
              }

              "Feedbooks" -> {
                val encoded =
                  parser.getAttributeValue(null, "bearerTokenSecret")

                credentials = ExamplePlayerCredentials.Feedbooks(
                  userName = parser.getAttributeValue(null, "userName"),
                  password = parser.getAttributeValue(null, "password"),
                  bearerTokenSecret = JSONBase64String(encoded).decode(),
                  issuerURL = parser.getAttributeValue(null, "issuerURL")
                )
              }

              "AuthenticationNone" -> {
                credentials = ExamplePlayerCredentials.None(23)
              }

              else -> {

              }
            }

          XmlPullParser.END_TAG -> {
            when (parser.name) {
              "Presets" -> Unit
              "Preset" -> {
                presets.add(
                  ExamplePreset(
                    name = name,
                    uri = URI.create(location),
                    credentials = credentials
                  )
                )
              }
              else -> {

              }
            }
          }

          else -> Unit
        }
      }
    }
  }
}
