/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.senioraccountingofficerstubs.controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.FakeRequest

class ContactDetailsControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val fakeGETRequest = FakeRequest("GET", "/contact-details")
  private val fakePUTRequest = FakeRequest("PUT", "/contact-details")
  private val controller     = app.injector.instanceOf[ContactDetailsController]

  private val knownId   = "123"
  private val unknownId = "567"

  "GET /contact-details/:saoSubscriptionId" should {
    "return 200 and contact details for a known saoSubscriptionId" in {
      val result = controller.getContactDetails(knownId)(fakeGETRequest)

      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.obj(
        "saoSubscriptionId" -> knownId,
        "name"              -> "Jane Doe",
        "email"             -> "jane.doe@acme.example"
      )
    }

    "return a 404 for an unknown saoSubscriptionId" in {
      val result = controller.getContactDetails(unknownId)(fakeGETRequest)
      status(result) shouldBe Status.NOT_FOUND
    }
  }

  "PUT /contact-details/:saoSubscriptionId" should {
    "return 204 for a known saoSubscriptionId" in {
      val result = controller.putContactDetails(knownId)(fakePUTRequest)

      status(result) shouldBe Status.NO_CONTENT
    }

    "return a 404 for an unknown saoSubscriptionId" in {
      val result = controller.putContactDetails(unknownId)(fakePUTRequest)
      status(result) shouldBe Status.NOT_FOUND
    }
  }

}
