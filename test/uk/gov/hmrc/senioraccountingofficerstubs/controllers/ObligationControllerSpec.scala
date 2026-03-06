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
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class ObligationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val fakeGETRequest = FakeRequest("GET", "/obligation")
  private val controller     = app.injector.instanceOf[ObligationController]

  private val knownId   = "123"
  private val unknownId = "567"

  "GET /obligation/:saoSubscriptionId" should {
    "return 200 and obligation for a known saoSubscriptionId" in {
      val result = controller.getObligation(knownId)(fakeGETRequest)

      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.obj(
        "saoSubscriptionId" -> knownId,
        "subscription"      -> Json.obj(
          "subscriptionTimestamp"     -> "2021-01-01T00:00:00Z",
          "companyRegistrationNumber" -> "01234567",
          "uniqueTaxReference"        -> "1234567890",
          "companyName"               -> "Stub Global",
          "contacts"                  -> Json.arr(Json.obj("name" -> "jacob", "email" -> "example@example.com"))
        ),
        "submissions" -> Json.arr(
          Json.obj(
            "financialYearEnd" -> 2025,
            "notification" -> Json.obj("id" -> "notificationId", "notificationTimestamp" -> "notificationTimestamp"),
            "certificate"  -> Json.obj("id" -> "certificateId", "certificateTimestamp" -> "certificateTimestamp")
          )
        )
      )
    }

    "return a 404 for an unknown saoSubscriptionId" in {
      val result = controller.getObligation(unknownId)(fakeGETRequest)
      status(result) shouldBe Status.NOT_FOUND
    }
  }

}
