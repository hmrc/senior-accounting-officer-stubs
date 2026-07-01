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

class EtmpHelper {}

object EtmpHelper {

  private val headers = Seq("X-Transmitting-System", "X-Originating-System", "correlationid", "X-Receipt-Date")

  def validateHeaders(requestHeaders: Headers): Boolean = {
    val headersMap = headers.foldLeft(Map.empty[String, String]) { (map, header) =>
      requestHeaders.get(header) match {
        case Some(headerVal) => map + (header -> headerVal)
        case None            => map
      }
    }

    headersMap.size == headers.size &&
    validateXTransmittingSystem(headersMap) &&
    validateXOriginatingSystem(headersMap) &&
    validateCorrelationid(headersMap) &&
    validateReceiptDate(headersMap)
  }

  private def validateXTransmittingSystem(headersMap: Map[String, String]): Boolean = {
    headersMap.get("X-Transmitting-System") match {
      case Some("HIP") => true
      case _           => false
    }
  }
  private def validateXOriginatingSystem(headersMap: Map[String, String]): Boolean = {
    headersMap.get("X-Originating-System") match {
      case Some("MDTP") => true
      case _            => false
    }
  }
  private def validateCorrelationid(headersMap: Map[String, String]): Boolean = {
    headersMap
      .get("correlationid")
      .exists(correlationId => Try(UUID.fromString(correlationId)).isSuccess)
  }
  private def validateReceiptDate(headersMap: Map[String, String]): Boolean = {
    headersMap
      .get("X-Receipt-Date")
      .exists(datetime =>
        Try(Instant.parse(datetime)).toOption
          .exists(instant => instant == instant.truncatedTo(ChronoUnit.SECONDS))
      )
  }
}
