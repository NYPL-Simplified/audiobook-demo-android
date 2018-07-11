package org.nypl.audiobook.demo.android.api

/**
 * A spine item.
 */

interface PlayerSpineElementType {

  /**
   * The index of the spine item within the spine.
   *
   * The first item in the spine, if it exists, is guaranteed to have index = 0.
   * The next spine item, if it exists, is guaranteed to be at index + 1.
   */

  val index: Int

  /**
   * The next spine item, if one exists. This is null if and only if the current spine element
   * is the last one in the book.
   */

  val next : PlayerSpineElementType?

  val id: String

  val title: String

  val position: PlayerPosition

  val status: PlayerSpineElementStatus

  val downloadTask: PlayerDownloadTaskType
}