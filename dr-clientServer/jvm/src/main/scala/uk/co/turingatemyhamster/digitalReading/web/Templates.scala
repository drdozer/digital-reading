package uk.co.turingatemyhamster.digitalReading.web

import scalatags.Text.all._

/**
 *
 *
 * @author Matthew Pocock
 */
trait Templates {

  protected lazy val title = "title".tag(scalatags.generic.Namespace.htmlNamespaceConfig)
  protected lazy val media = "media".attr

  def mainTemplate(titleTxt: String)(htmlBits: Frag*) =
    html(
      head(
        title(titleTxt),
        link(rel := "stylesheet", media := "screen", href := "/public/stylesheets/main.css")
      ),
      body(
        htmlBits
      )
    )

}
