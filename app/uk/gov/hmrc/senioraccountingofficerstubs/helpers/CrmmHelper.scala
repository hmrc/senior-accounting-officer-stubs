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

package uk.gov.hmrc.senioraccountingofficerstubs.helpers

import play.api.mvc.Headers

object CrmmHelper {

  private val correlationIdHeader = "correlationId"
  private val sourceSysRefHeader  = "sourceSysRef"
  private val headers             = Seq(correlationIdHeader, sourceSysRefHeader)

  def validateHeaders(requestHeaders: Headers): Either[String, String] = {
    val headersMap = headers.foldLeft(Map.empty[String, String]) { (map, header) =>
      requestHeaders.get(header) match {
        case Some(headerVal) => map + (header -> headerVal)
        case None            => map
      }
    }

    for {
      sourceSystemReference <- headersMap
        .get(sourceSysRefHeader)
        .toRight(s"missing $sourceSysRefHeader header")
      _ <- {
        if sourceSystemReference == "HIP" then Right(sourceSystemReference)
        else Left(s"invalid $sourceSysRefHeader header")
      }
      correlationId <- headersMap
        .get(correlationIdHeader)
        .filter(_.nonEmpty)
        .toRight(s"missing $correlationIdHeader header")
    } yield (correlationId)
  }
}
