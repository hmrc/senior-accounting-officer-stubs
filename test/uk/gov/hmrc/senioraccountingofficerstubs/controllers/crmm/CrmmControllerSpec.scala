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

package uk.gov.hmrc.senioraccountingofficerstubs.controllers.crmm

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

class EtmpControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {
  // - TODO: test invalid headers
  // - TODO: test valid headers and invalid json
  //   - TODO: test no utr or crn provided
  // - TODO: test valid headers valid json and no config
  // - TODO: test valid headers valid json and some config
}
