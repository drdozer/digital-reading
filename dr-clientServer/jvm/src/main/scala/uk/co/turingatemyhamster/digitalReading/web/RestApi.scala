package uk.co.turingatemyhamster.digitalReading.web

import uk.co.turingatemyhamster.digitalReading.corpus.{Stories, StoryDB}

import scala.concurrent.{ExecutionContext, Future}
import scalatags.Text.all._
import upickle._


/**
 *
 *
 * @author Matthew Pocock
 */
trait RestApi {
  implicit def ec: ExecutionContext
  def corpus: StoryDB[StoryDB.Identity]

  def `api/stories` =
    Future { write(corpus.all : Stories) }
  def `api/chapter_word_counts`(chapterId: Long, preserveCase: Boolean) =
    Future { write(corpus.chapterWordCounts(chapterId, preserveCase)) }
  def `api/all_word_counts`(preserveCase: Boolean) =
    Future { write(corpus.allWordCounts(preserveCase)) }
  def `api/all_mean_stdev` =
    Future { write(corpus.allMeanStdev) }
}
