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

  private val TransmittingSystem = "X-Transmitting-System"
  private val OriginatingSystem  = "X-Originating-System"
  private val CorrelationId      = "correlationid"
  private val ReceiptDate        = "X-Receipt-Date"
  private val headers            = Seq(TransmittingSystem, OriginatingSystem, CorrelationId, ReceiptDate)

  def validateHeaders(requestHeaders: Headers): Either[String, String] = {
    val headersMap = headers.foldLeft(Map.empty[String, String]) { (map, header) =>
      requestHeaders.get(header) match {
        case Some(headerVal) => map + (header -> headerVal)
        case None            => map
      }
    }

    for {
      transmittingSystem <- headersMap
        .get(TransmittingSystem)
        .toRight(s"missing $TransmittingSystem header")
      _ <- {
        if transmittingSystem == "HIP" then Right(transmittingSystem)
        else Left(s"invalid $TransmittingSystem header")
      }

      originatingSystem <- headersMap
        .get(OriginatingSystem)
        .toRight(s"missing $OriginatingSystem header")
      _ <- {
        if originatingSystem == "MDTP" then Right(originatingSystem)
        else Left(s"invalid $OriginatingSystem header")
      }

      receiptDate <- headersMap
        .get(ReceiptDate)
        .toRight(s"missing $ReceiptDate header")
      instant <- Try(Instant.parse(receiptDate)).toEither.left.map(_ => s"invalid $ReceiptDate header")
      _       <- {
        if instant.truncatedTo(ChronoUnit.SECONDS).toString == receiptDate then Right(receiptDate)
        else Left(s"invalid $ReceiptDate format")
      }

      correlationId <- headersMap
        .get(CorrelationId)
        .toRight(s"missing $CorrelationId header")
        .flatMap(id => Try(UUID.fromString(id)).toEither.left.map(_ => s"invalid $CorrelationId header").map(_ => id))

    } yield (correlationId)
  }
}
