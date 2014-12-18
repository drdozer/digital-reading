package uk.co.turingatemyhamster.digitalReading.web

import uk.co.turingatemyhamster.digitalReading.corpus.{Stories, StoryDB}

import scalatags.Text.all._
import upickle._


/**
 *
 *
 * @author Matthew Pocock
 */
trait RestApi {
  def corpus: StoryDB[StoryDB.Identity]

  def `api/stories` = write(corpus.all : Stories)
}
