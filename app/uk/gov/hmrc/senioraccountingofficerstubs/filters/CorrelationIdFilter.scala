/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.senioraccountingofficerstubs.filters

import org.apache.pekko.stream.Materializer
import play.api.libs.json.*
import play.api.mvc.Results.*
import play.api.mvc.*
import play.api.routing.Router
import play.api.routing.Router.RequestImplicits.WithHandlerDef
import uk.gov.hmrc.senioraccountingofficerstubs.filters.CorrelationIdFilter.*
import uk.gov.hmrc.senioraccountingofficerstubs.models.hip.*

import scala.concurrent.Future

import javax.inject.Inject

class CorrelationIdFilter @Inject() ()(using m: Materializer) extends Filter {

  override implicit def mat: Materializer = m

  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    if isHealthCheck(requestHeader.path) || requestHeader.hasRouteModifier("noauth") then {
      nextFilter(requestHeader)
    } else {
      val authorisationHeader = requestHeader.headers.get("correlationId")

      authorisationHeader match {
        case Some(correlationId) if correlationId.matches(hipCorrelationIdRegex) =>
          nextFilter(requestHeader)
        case None => nextFilter(requestHeader)
        case _    =>
          Future.successful(
            BadRequest(
              Json.toJson(
                StandardHipFailures(
                  origin = "HIP",
                  failures = Seq(
                    Failure(
                      `type` = "header.CorrelationId",
                      reason = "The request parameter header.CorrelationId failed validation."
                    )
                  )
                )
              )
            )
          )
      }
    }
  }

  private def isHealthCheck(path: String): Boolean =
    path.startsWith("/ping/")

}

object CorrelationIdFilter {
  def hipCorrelationIdRegex: String =
    "^[0-9a-fA-F]{8}[-][0-9a-fA-F]{4}[-][0-9a-fA-F]{4}[-][0-9a-fA-F]{4}[-][0-9a-fA-F]{12}$"
}
