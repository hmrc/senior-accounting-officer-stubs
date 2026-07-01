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

package helpers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.Headers
import uk.gov.hmrc.senioraccountingofficerstubs.helpers.EtmpHelper.validateHeaders

class EtmpHelperSpec extends AnyWordSpec with Matchers {

  private val validRequestHeaders = Headers(
    "X-Transmitting-System" -> "HIP",
    "X-Originating-System"  -> "MDTP",
    "correlationid"         -> "f0bd1f32-de51-45cc-9b18-0520d6e3ab1a",
    "X-Receipt-Date"        -> "2026-05-05T12:05:45Z"
  )

  "validateHeaders" should {
    "return true when all required headers are found, and they are all valid" in {
      validateHeaders(validRequestHeaders) shouldBe true
    }

    "return true when all required headers valid, and there are also extra headers" in {
      val headers = validRequestHeaders.add("extra header" -> "extra value")
      validateHeaders(headers) shouldBe true
    }

    "return false when none of the required headers found" in {
      validateHeaders(Headers()) shouldBe false
    }
    "return false when the X-Transmitting-System header is not found, empty or invalid" in {
      val requestWithoutHeader     = validRequestHeaders.remove("X-Transmitting-System")
      val requestWithEmptyHeader   = requestWithoutHeader.add("X-Transmitting-System" -> "")
      val requestWithInvalidHeader = requestWithoutHeader.add("X-Transmitting-System" -> "Test")

      validateHeaders(requestWithoutHeader) shouldBe false
      validateHeaders(requestWithEmptyHeader) shouldBe false
      validateHeaders(requestWithInvalidHeader) shouldBe false
    }

    "return false when the X-Originating-System header is not found, empty or invalid" in {
      val requestWithoutHeader     = validRequestHeaders.remove("X-Originating-System")
      val requestWithEmptyHeader   = requestWithoutHeader.add("X-Originating-System" -> "")
      val requestWithInvalidHeader = requestWithoutHeader.add("X-Originating-System" -> "invalid")

      validateHeaders(requestWithoutHeader) shouldBe false
      validateHeaders(requestWithEmptyHeader) shouldBe false
      validateHeaders(requestWithInvalidHeader) shouldBe false
    }

    "return false when the correlationid header is not found, empty or invalid" in {
      val requestWithoutHeader     = validRequestHeaders.remove("correlationid")
      val requestWithEmptyHeader   = requestWithoutHeader.add("correlationid" -> "")
      val requestWithInvalidHeader = requestWithoutHeader.add("correlationid" -> "12341")

      validateHeaders(requestWithoutHeader) shouldBe false
      validateHeaders(requestWithEmptyHeader) shouldBe false
      validateHeaders(requestWithInvalidHeader) shouldBe false
    }

    "return false when the X-Receipt-Date header is not found, empty or invalid" in {
      val requestWithoutHeader     = validRequestHeaders.remove("X-Receipt-Date")
      val requestWithEmptyHeader   = requestWithoutHeader.add("X-Receipt-Date" -> "")
      val requestWithInvalidHeader = requestWithoutHeader.add("X-Receipt-Date" -> "abc")

      validateHeaders(requestWithoutHeader) shouldBe false
      validateHeaders(requestWithEmptyHeader) shouldBe false
      validateHeaders(requestWithInvalidHeader) shouldBe false
    }
  }
}
