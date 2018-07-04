package org.nypl.audiobook.demo.android

/**
 * A result type - either success or failure.
 */

sealed class Result<out S : Any, out F : Any> {
  data class Success<out S : Any, out F : Any>(val result: S) : Result<S, F>()
  data class Failure<out S : Any, out F : Any>(val failure: F) : Result<S, F>()
}
