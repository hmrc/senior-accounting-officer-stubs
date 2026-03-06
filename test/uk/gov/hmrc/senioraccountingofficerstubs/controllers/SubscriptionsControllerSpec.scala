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

package uk.gov.hmrc.senioraccountingofficerstubs.controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class SubscriptionsControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val controller = app.injector.instanceOf[SubscriptionsController]

  "PUT /subscriptions" should {
    "return 200 for a valid request payload" in {
      val request = FakeRequest("PUT", "/subscriptions")
        .withJsonBody(Json.obj("subscription" -> Json.obj("name" -> "Test Data Ltd")))

      val result = controller.putSubscription(request)

      status(result) shouldBe Status.OK
    }

    "return 400 when the request payload is an empty object" in {
      val request = FakeRequest("PUT", "/subscriptions")
        .withJsonBody(Json.obj())

      val result = controller.putSubscription(request)

      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 400 when the request payload is not a JSON object" in {
      val request = FakeRequest("PUT", "/subscriptions")
        .withJsonBody(Json.arr("invalid"))

      val result = controller.putSubscription(request)

      status(result) shouldBe Status.BAD_REQUEST
    }
  }
}
