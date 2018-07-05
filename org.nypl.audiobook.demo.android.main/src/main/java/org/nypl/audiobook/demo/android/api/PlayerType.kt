package org.nypl.audiobook.demo.android.api

interface PlayerType {

  val key: String

  val downloadTask: PlayerDownloadTaskType

  val chapter: PlayerChapterLocationType

}