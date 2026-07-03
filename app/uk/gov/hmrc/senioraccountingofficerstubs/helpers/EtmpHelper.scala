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

import scala.util.Try

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

object EtmpHelper {

  private val headers = Seq("X-Transmitting-System", "X-Originating-System", "correlationid", "X-Receipt-Date")

  def validateHeaders(requestHeaders: Headers): Either[String, String] = {
    val headersMap = headers.foldLeft(Map.empty[String, String]) { (map, header) =>
      requestHeaders.get(header) match {
        case Some(headerVal) => map + (header -> headerVal)
        case None            => map
      }
    }

    for {
      transmittingSystem <- headersMap
        .get("X-Transmitting-System")
        .toRight("missing X-Transmitting-System header")
      _ <- {
        if transmittingSystem == "HIP" then Right(transmittingSystem)
        else Left("invalid X-Transmitting-System header")
      }

      originatingSystem <- headersMap
        .get("X-Originating-System")
        .toRight("missing X-Originating-System header")
      _ <- {
        if originatingSystem == "MDTP" then Right(originatingSystem)
        else Left("invalid X-Originating-System header")
      }

      receiptDate <- headersMap
        .get("X-Receipt-Date")
        .toRight("missing X-Receipt-Date header")
      instant <- Try(Instant.parse(receiptDate)).toEither.left.map(_ => "invalid X-Receipt-Date header")
      _       <- {
        if instant.truncatedTo(ChronoUnit.SECONDS).toString == receiptDate then Right(receiptDate)
        else Left("invalid X-Receipt-Date format")
      }

      correlationId <- headersMap
        .get("correlationid")
        .toRight("missing correlationid header")
        .flatMap(id => Try(UUID.fromString(id)).toEither.left.map(_ => "invalid correlationid header").map(_ => id))

    } yield (correlationId)
  }
}
