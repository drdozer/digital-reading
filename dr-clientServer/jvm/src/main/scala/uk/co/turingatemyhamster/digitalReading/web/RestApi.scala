package uk.co.turingatemyhamster.digitalReading.web

import uk.co.turingatemyhamster.digitalReading.corpus._

import upickle._

import scala.concurrent.ExecutionContext


/**
 *
 *
 * @author Matthew Pocock
 */
trait RestApi {
  implicit def ec: ExecutionContext

  def stopWordsDB: StopWordsDB
  def corpusDB: CorpusDB
  def rawWordsDB: WordsDB
  def filteredWordsDB: WordsDB
  def rawStatsDB: CorpusStatsDB
  def filteredStatsDB: CorpusStatsDB
  def rawSurpriseDB: SurpriseDB

  object AutowireServer extends autowire.Server[String, upickle.Reader, upickle.Writer] {
    def write[Result: Writer](r: Result) = upickle.write(r)
    def read[Result: Reader](p: String) = upickle.read[Result](p)

    val stopWordsDB         = route[StopWordsDB](RestApi.this.stopWordsDB)
    val corpusDB            = route[CorpusDB](RestApi.this.corpusDB)
    val rawWordsDB          = route[WordsDB](RestApi.this.rawWordsDB)
    val filteredWordsDB     = route[WordsDB](RestApi.this.filteredWordsDB)
    val rawStatsDB          = route[CorpusStatsDB](RestApi.this.rawStatsDB)
    val filteredStatsDB     = route[CorpusStatsDB](RestApi.this.filteredStatsDB)
    val rawSurpriseDB       = route[SurpriseDB](RestApi.this.rawSurpriseDB)
  }
}
