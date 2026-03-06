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
import play.api.mvc.Filter
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results.Unauthorized
import uk.gov.hmrc.senioraccountingofficerstubs.config.AppConfig

import scala.concurrent.Future

import java.util.Base64
import javax.inject.Inject

class AuthFilter @Inject() (appConfig: AppConfig)(using m: Materializer) extends Filter {

  override implicit def mat: Materializer = m

  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    if (isHealthCheck(requestHeader.path)) {
      return nextFilter(requestHeader)
    }

    val authorisationHeader = requestHeader.headers.get("Authorization")
    val validAuthorisation  = s"Basic $tokenBase64"

    authorisationHeader match {
      case Some(`validAuthorisation`) => nextFilter(requestHeader)
      case _                          => Future.successful(Unauthorized)
    }

  }
  
  private def tokenBase64: String =
    Base64.getEncoder.encodeToString(s"${appConfig.clientId}:${appConfig.clientSecret}".getBytes("UTF-8"))

  private def isHealthCheck(path: String): Boolean =
    path.startsWith("/ping/ping")

}
