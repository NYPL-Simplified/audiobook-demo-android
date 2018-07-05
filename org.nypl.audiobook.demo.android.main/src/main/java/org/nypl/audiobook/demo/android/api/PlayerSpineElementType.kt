package org.nypl.audiobook.demo.android.api

interface PlayerSpineElementType {

  val id: String

  val title: String

  val chapter: PlayerChapterLocationType

  val status: PlayerSpineElementStatus

  val downloadTask: PlayerDownloadTaskType

}