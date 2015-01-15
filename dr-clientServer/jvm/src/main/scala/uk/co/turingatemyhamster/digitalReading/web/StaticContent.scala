package uk.co.turingatemyhamster.digitalReading.web

import scalatags.Text.all._

/**
 *
 *
 * @author Matthew Pocock
 */

trait StaticContent {
  self : Templates =>

  val index = mainTemplate("Digital Reading")(
    h1("Digital Reading"),
    span(`class` := "subtitle")("Digital prosthetics for reading"),
    p("""Digital reading is an extension of distant reading to Big Data electronic corpuses.""")
  )

  val `digital_reading_proposal/index` = mainTemplate("Digital Reading Research Proposal Supporting Materials")(
    h1("Digital Reading Research Proposal Supporting Materials"),
    p(span(`class` := "warning")(
      "If you aren't reviewing the 'Digital Reading' research proposal, this site is probably not for you.")),
    p("Click ", a(href := "./wattpad100.html")("here"), " to browse the wattpad100 pilot study corpus.")
    )

  val `digital_reading_proposal/wattpad100` = mainTemplate("Wattpad100 Corpus")(
    script(src := "/public/javascript/dr-clientserver-opt.js", `type` := "text/javascript"),
//    script(src := "/public/javascript/dr-clientserver-fastopt.js", `type` := "text/javascript"),
    h1("Wattpad100 Corups"),
    div(id := "corpusBrowser"),
    div(id := "storyBrowser"),
    script(
      """CorpusBrowser().wire(
        |  document.getElementById('corpusBrowser'),
        |  document.getElementById('storyBrowser')
        |)""".stripMargin)
  )

}
