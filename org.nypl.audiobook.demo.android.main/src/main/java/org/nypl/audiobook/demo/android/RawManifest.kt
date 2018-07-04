package org.nypl.audiobook.demo.android

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * A raw manifest parsed from a server.
 */

data class RawManifest(
  val spine: List<RawSpineItem>,
  val links: List<RawLink>,
  val metadata: RawMetadata) {

  companion object {

    /**
     * Parse a manifest from the given stream.
     *
     * @param stream An input stream
     * @return A parsed manifest, or a reason why it couldn't be parsed
     */

    fun parse(stream: InputStream): Result<RawManifest, Exception> {
      try {
        val mapper = ObjectMapper()
        val result = mapper.readTree(stream)
        if (result is ObjectNode) {
          return this.parseFromObjectNode(result)
        }
        return Result.Failure(IOException(
          "Expected a JSON object but received a " + result.nodeType))
      } catch (e: Exception) {
        return Result.Failure(e)
      }
    }

    /**
     * Parse a manifest from the given object node.
     *
     * @param node An object node
     * @return A parsed manifest, or a reason why it couldn't be parsed
     */

    fun parseFromObjectNode(node: ObjectNode): Result<RawManifest, Exception> {
      try {
        val links = this.parseLinks(node)
        val spine = this.parseSpine(node)
        val metadata = this.parseMetadata(node)
        return Result.Success(RawManifest(spine = spine, links = links, metadata = metadata))
      } catch (e: Exception) {
        return Result.Failure(e)
      }
    }

    private fun parseMetadata(node: ObjectNode): RawMetadata {
      val metadata = JSONParserUtilities.getObject(node, "metadata")

      val title = JSONParserUtilities.getString(metadata, "title")
      val language = JSONParserUtilities.getString(metadata, "language")
      val duration = JSONParserUtilities.getDouble(metadata, "duration")
      val identifier = JSONParserUtilities.getString(metadata, "identifier")
      val authors = this.parseAuthors(metadata)
      val encrypted = this.parseEncrypted(metadata)

      return RawMetadata(
        title = title,
        language = language,
        duration = duration,
        identifier = identifier,
        authors = authors,
        encrypted = encrypted)
    }

    private fun parseEncrypted(node: ObjectNode): RawEncrypted? {
      val encrypted = JSONParserUtilities.getObjectOptional(node, "encrypted")
      if (encrypted == null) {
        return null
      }

      val scheme = JSONParserUtilities.getString(encrypted, "scheme")
      val values = this.parseScalarMap(encrypted)
      return RawEncrypted(scheme, values)
    }

    private fun parseAuthors(node: ObjectNode): List<String> {
      val author_array = JSONParserUtilities.getArray(node, "authors")
      val authors = ArrayList<String>()

      for (index in 0..author_array.size() - 1) {
        if (author_array[index] is TextNode) {
          authors.add(author_array[index].asText())
        }
      }
      return authors.toList()
    }

    private fun parseSpine(node: ObjectNode): List<RawSpineItem> {
      val spine_array = JSONParserUtilities.getArray(node, "spine")
      val spines = ArrayList<RawSpineItem>()

      for (index in 0..spine_array.size() - 1) {
        spines.add(this.parseRawSpineItem(
          JSONParserUtilities.checkObject(null, spine_array[index])))
      }
      return spines.toList()
    }

    private fun parseRawSpineItem(node: ObjectNode): RawSpineItem {
      return RawSpineItem(this.parseScalarMap(node))
    }

    private fun parseScalarMap(node: ObjectNode): Map<String, RawScalar?> {
      val values = HashMap<String, RawScalar?>()
      for (key in node.fieldNames()) {
        values.put(key, JSONParserUtilities.getScalar(node, key))
      }
      return values.toMap()
    }

    private fun parseLinks(node: ObjectNode): List<RawLink> {
      val link_array = JSONParserUtilities.getArray(node, "links")
      val links = ArrayList<RawLink>()

      for (index in 0..link_array.size() - 1) {
        links.add(this.parseRawLink(JSONParserUtilities.checkObject(null, link_array[index])))
      }
      return links.toList()
    }

    private fun parseRawLink(node: ObjectNode): RawLink {
      return RawLink(
        href = JSONParserUtilities.getURI(node, "href"),
        relation = JSONParserUtilities.getString(node, "rel"))
    }
  }
}

data class RawMetadata(
  val title: String,
  val language: String,
  val duration: Double,
  val identifier: String,
  val authors: List<String>,
  val encrypted: RawEncrypted?)

sealed class RawScalar {
  data class RawScalarString(val text: String) : RawScalar()
  data class RawScalarNumber(val number: Double) : RawScalar()
  data class RawScalarBoolean(val value: Boolean) : RawScalar()
}

data class RawEncrypted(
  val scheme: String,
  val values: Map<String, RawScalar?>)

data class RawSpineItem(
  val values: Map<String, RawScalar?>)

data class RawLink(
  val href: URI,
  val relation: String)
