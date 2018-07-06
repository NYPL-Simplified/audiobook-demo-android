package org.nypl.audiobook.demo.android.api

interface PlayerSpineElementType {

  val id: String

  val title: String

  val position: PlayerPosition

  val status: PlayerSpineElementStatus

  val downloadTask: PlayerDownloadTaskType

}