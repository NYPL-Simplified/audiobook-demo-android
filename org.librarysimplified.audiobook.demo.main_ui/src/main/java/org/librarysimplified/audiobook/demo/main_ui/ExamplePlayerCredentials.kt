package org.librarysimplified.audiobook.demo.main_ui

import java.io.Serializable

sealed class ExamplePlayerCredentials : Serializable {

  object None : ExamplePlayerCredentials()

  data class Basic(
    val userName: String,
    val password: String
  ) : ExamplePlayerCredentials()

  data class Feedbooks(
    val userName: String,
    val password: String,
    val bearerTokenSecret: ByteArray,
    val issuerURL: String
  ) : ExamplePlayerCredentials()
}
