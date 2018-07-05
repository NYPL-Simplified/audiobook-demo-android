package org.nypl.audiobook.demo.android

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import java.math.BigInteger
import java.net.URI
import java.net.URISyntaxException

/**
 *
 * Utility functions for deserializing elements from JSON.
 *
 *
 * The functions take a strict approach: Types are checked upon key retrieval
 * and exceptions are raised if the type is not exactly as expected.
 */

class JSONParserUtilities private constructor() {

  companion object {

    /**
     * Check that `n` is an object.
     *
     * @param key An optional advisory key to be used in error messages
     * @param n   A node
     *
     * @return `n` as an [ObjectNode]
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun checkObject(
      key: String?,
      n: JsonNode): ObjectNode {

      when (n.nodeType) {
        JsonNodeType.ARRAY,
        JsonNodeType.BINARY,
        JsonNodeType.BOOLEAN,
        JsonNodeType.MISSING,
        JsonNodeType.NULL,
        JsonNodeType.NUMBER,
        JsonNodeType.POJO,
        JsonNodeType.STRING -> {
          val sb = StringBuilder(128)
          if (key != null) {
            sb.append("Expected: A key '")
            sb.append(key)
            sb.append("' with a value of type Object\n")
            sb.append("Received: A value of type ")
            sb.append(n.nodeType)
            sb.append("\n")
          } else {
            sb.append("Expected: A value of type Object\n")
            sb.append("Received: A value of type ")
            sb.append(n.nodeType)
            sb.append("\n")
          }

          throw JSONParseException(sb.toString())
        }
        JsonNodeType.OBJECT -> {
          return n as ObjectNode
        }
      }
    }

    /**
     * @param key A key assumed to be holding a value
     * @param s   A node
     *
     * @return An array from key `key`
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getArray(
      s: ObjectNode,
      key: String): ArrayNode {

      val n = this.getNode(s, key)
      when (n.nodeType) {
        JsonNodeType.ARRAY -> {
          return n as ArrayNode
        }
        JsonNodeType.BINARY,
        JsonNodeType.BOOLEAN,
        JsonNodeType.MISSING,
        JsonNodeType.NULL,
        JsonNodeType.NUMBER,
        JsonNodeType.POJO,
        JsonNodeType.STRING,
        JsonNodeType.OBJECT -> {
          val sb = StringBuilder(128)
          sb.append("Expected: A key '")
          sb.append(key)
          sb.append("' with a value of type Array\n")
          sb.append("Received: A value of type ")
          sb.append(n.nodeType)
          sb.append("\n")
          throw JSONParseException(sb.toString())
        }
      }
    }

    /**
     * @param key A key assumed to be holding a value
     * @param o   A node
     *
     * @return A boolean value from key `key`
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getBoolean(
      o: ObjectNode,
      key: String): Boolean {

      val v = this.getNode(o, key)
      when (v.nodeType) {
        JsonNodeType.ARRAY,
        JsonNodeType.BINARY,
        JsonNodeType.MISSING,
        JsonNodeType.NULL,
        JsonNodeType.OBJECT,
        JsonNodeType.POJO,
        JsonNodeType.STRING,
        JsonNodeType.NUMBER -> {
          val sb = StringBuilder(128)
          sb.append("Expected: A key '")
          sb.append(key)
          sb.append("' with a value of type Boolean\n")
          sb.append("Received: A value of type ")
          sb.append(v.nodeType)
          sb.append("\n")
          throw JSONParseException(sb.toString())
        }
        JsonNodeType.BOOLEAN -> {
          return v.asBoolean()
        }
      }
    }

    /**
     * @param key A key assumed to be holding a value
     * @param n   A node
     *
     * @return An integer value from key `key`
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getInteger(
      n: ObjectNode,
      key: String): Int {

      val v = this.getNode(n, key)
      when (v.nodeType) {
        JsonNodeType.ARRAY,
        JsonNodeType.BINARY,
        JsonNodeType.BOOLEAN,
        JsonNodeType.MISSING,
        JsonNodeType.NULL,
        JsonNodeType.OBJECT,
        JsonNodeType.POJO,
        JsonNodeType.STRING -> {
          val sb = StringBuilder(128)
          sb.append("Expected: A key '")
          sb.append(key)
          sb.append("' with a value of type Integer\n")
          sb.append("Received: A value of type ")
          sb.append(v.nodeType)
          sb.append("\n")
          throw JSONParseException(sb.toString())
        }
        JsonNodeType.NUMBER -> {
          return v.asInt()
        }
      }
    }

    /**
     * @param key A key assumed to be holding a value
     * @param s   A node
     *
     * @return An arbitrary json node from key `key`
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getNode(
      s: ObjectNode,
      key: String): JsonNode {

      if (s.has(key)) {
        return s.get(key)
      }

      val sb = StringBuilder(128)
      sb.append("Expected: A key '")
      sb.append(key)
      sb.append("'\n")
      sb.append("Received: nothing\n")
      throw JSONParseException(sb.toString())
    }

    /**
     * @param key A key assumed to be holding a value
     * @param s   A node
     *
     * @return An object value from key `key`
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getObject(
      s: ObjectNode,
      key: String): ObjectNode {

      val n = this.getNode(s, key)
      return this.checkObject(key, n)
    }

    /**
     * @param key A key assumed to be holding a value
     * @param s   A node
     *
     * @return An object value from key `key`, if the key exists
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getObjectOptional(
      s: ObjectNode,
      key: String): ObjectNode? {

      return if (s.has(key)) {
        this.getObject(s, key)
      } else null
    }

    /**
     * @param key A key assumed to be holding a value
     * @param s   A node
     *
     * @return A string value from key `key`
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getString(
      s: ObjectNode,
      key: String): String {

      val v = this.getNode(s, key)
      when (v.nodeType) {
        JsonNodeType.ARRAY,
        JsonNodeType.BINARY,
        JsonNodeType.BOOLEAN,
        JsonNodeType.MISSING,
        JsonNodeType.NULL,
        JsonNodeType.NUMBER,
        JsonNodeType.OBJECT,
        JsonNodeType.POJO -> {
          val sb = StringBuilder(128)
          sb.append("Expected: A key '")
          sb.append(key)
          sb.append("' with a value of type String\n")
          sb.append("Received: A value of type ")
          sb.append(v.nodeType)
          sb.append("\n")
          throw JSONParseException(sb.toString())
        }
        JsonNodeType.STRING -> {
          return v.asText()
        }
      }
    }

    /**
     * @param key A key assumed to be holding a value
     * @param n   A node
     *
     * @return An integer value from key `key`, if the key exists
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getIntegerOptional(
      n: ObjectNode,
      key: String): Int? {

      return if (n.has(key)) {
        this.getInteger(n, key)
      } else null
    }

    /**
     * @param key A key assumed to be holding a value
     * @param n   A node
     *
     * @return A string value from key `key`, if the key exists
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getStringOptional(
      n: ObjectNode,
      key: String): String? {

      return if (n.has(key)) {
        this.getString(n, key)
      } else null
    }

    /**
     * @param key A key assumed to be holding a value
     * @param n   A node
     *
     * @return A URI value from key `key`
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getURI(
      n: ObjectNode,
      key: String): URI {

      try {
        return URI(this.getString(n, key))
      } catch (e: URISyntaxException) {
        throw JSONParseException(e)
      }
    }

    /**
     * @param key A key assumed to be holding a value
     * @param n   A node
     * @param v   A default value
     *
     * @return A boolean from key `key`, or `v` if the key does not
     * exist
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getBooleanDefault(
      n: ObjectNode,
      key: String,
      v: Boolean): Boolean {

      return if (n.has(key)) {
        this.getBoolean(n, key)
      } else v
    }

    /**
     * @param key A key assumed to be holding a value
     * @param n   A node
     *
     * @return A big integer value from key `key`, if the key exists
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getBigIntegerOptional(
      n: ObjectNode,
      key: String): BigInteger? {

      return if (n.has(key)) {
        this.getBigInteger(n, key)
      } else null
    }

    /**
     * @param key A key assumed to be holding a value
     * @param n   A node
     *
     * @return A big integer value from key `key`
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getBigInteger(
      n: ObjectNode,
      key: String): BigInteger {

      val v = this.getNode(n, key)
      when (v.nodeType) {
        JsonNodeType.ARRAY,
        JsonNodeType.BINARY,
        JsonNodeType.BOOLEAN,
        JsonNodeType.MISSING,
        JsonNodeType.NULL,
        JsonNodeType.OBJECT,
        JsonNodeType.POJO,
        JsonNodeType.STRING -> {
          val sb = StringBuilder(128)
          sb.append("Expected: A key '")
          sb.append(key)
          sb.append("' with a value of type Integer\n")
          sb.append("Received: A value of type ")
          sb.append(v.nodeType)
          sb.append("\n")
          throw JSONParseException(sb.toString())
        }
        JsonNodeType.NUMBER -> {
          try {
            return BigInteger(v.asText())
          } catch (e: NumberFormatException) {
            throw JSONParseException(e)
          }
        }
      }
    }


    /**
     * @param key A key assumed to be holding a value
     * @param n   A node
     *
     * @return A big integer value from key `key`, if the key exists
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getDoubleOptional(
      n: ObjectNode,
      key: String): Double? {

      return if (n.has(key)) {
        this.getDouble(n, key)
      } else null
    }

    /**
     * @param key A key assumed to be holding a value
     * @param n   A node
     *
     * @return A big integer value from key `key`
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getDouble(
      n: ObjectNode,
      key: String): Double {

      val v = this.getNode(n, key)
      when (v.nodeType) {
        JsonNodeType.ARRAY,
        JsonNodeType.BINARY,
        JsonNodeType.BOOLEAN,
        JsonNodeType.MISSING,
        JsonNodeType.NULL,
        JsonNodeType.OBJECT,
        JsonNodeType.POJO,
        JsonNodeType.STRING -> {
          val sb = StringBuilder(128)
          sb.append("Expected: A key '")
          sb.append(key)
          sb.append("' with a value of type Double\n")
          sb.append("Received: A value of type ")
          sb.append(v.nodeType)
          sb.append("\n")
          throw JSONParseException(sb.toString())
        }
        JsonNodeType.NUMBER -> {
          return v.asDouble()
        }
      }
    }

    /**
     * @param n   A node
     *
     * @return A scalar value from the given node
     *
     * @throws JSONParseException On type errors
     */

    @Throws(JSONParseException::class)
    fun getScalar(
      n: ObjectNode,
      key: String): RawScalar? {

      val v = this.getNode(n, key)
      when (v.nodeType) {
        JsonNodeType.ARRAY,
        JsonNodeType.BINARY,
        JsonNodeType.MISSING,
        JsonNodeType.OBJECT,
        JsonNodeType.POJO -> {
          val sb = StringBuilder(128)
          sb.append("Expected: An object of a scalar type.\n")
          sb.append("Received: A value of type ")
          sb.append(v.nodeType)
          sb.append("\n")
          throw JSONParseException(sb.toString())
        }

        JsonNodeType.NULL -> return null
        JsonNodeType.BOOLEAN -> return RawScalar.RawScalarBoolean(v.asBoolean())
        JsonNodeType.NUMBER ->
          if (v.isIntegralNumber) {
            return RawScalar.RawScalarNumber.RawScalarInteger(v.asInt())
          } else {
            return RawScalar.RawScalarNumber.RawScalarReal(v.asDouble())
          }
        JsonNodeType.STRING -> return RawScalar.RawScalarString(v.asText())
      }
    }
  }
}