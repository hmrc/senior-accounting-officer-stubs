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

package uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly

import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.play.json.Union
import uk.gov.hmrc.senioraccountingofficerstubs.models.getsubscription.*

import java.time.Instant

sealed abstract class GetSubscriptionConfigUnion(val api: String)

final case class GetSubscriptionOnlyConfig(
    status: Int,
    defaultBodyOverride: Option[String] = None
) extends GetSubscriptionConfigUnion(api = "GetSubscription")

object GetSubscriptionOnlyConfig {
  given OFormat[GetSubscriptionOnlyConfig] = Json.format[GetSubscriptionOnlyConfig]
}

object EmailStatus {
  // opt not to make this an enum so that we can stub with unknown statuses as well
  val Valid       = "valid"
  val Unreachable = "unreachable"
}

final case class GetSubscriptionConfig(
    utr: String,
    crn: Option[String] = None,
    name: Option[String] = None,
    contacts: List[Contact] = List.empty
)

object GetSubscriptionConfig {
  given OFormat[GetSubscriptionConfig] = Json.format[GetSubscriptionConfig]
}

final case class PostRetrieveCustomerIdConfig(
    getSubscription: GetSubscriptionConfig,
    status: Int,
    defaultBodyOverride: Option[String] = None
) extends GetSubscriptionConfigUnion(api = "PostRetrieveCustomerId")

object PostRetrieveCustomerIdConfig {
  given OFormat[PostRetrieveCustomerIdConfig] = Json.format[PostRetrieveCustomerIdConfig]
}

final case class PostSignupStubConfiguration(
    subscriptionId: String,
    getSubscriptionAndPostRetrieveCustomerId: Option[GetSubscriptionConfigUnion] = None,
    postNotification: Option[NoneDefaultApiConfiguration] = None,
    postCertificate: Option[NoneDefaultApiConfiguration] = None,
    lastUpdated: Instant = Instant.now
)

object PostSignupStubConfiguration {
  given Format[Instant]                    = MongoJavatimeFormats.instantFormat
  given Format[GetSubscriptionConfigUnion] = Union
    .from[GetSubscriptionConfigUnion](typeField = "api")
    .and[GetSubscriptionOnlyConfig](typeTag = "GetSubscription")
    .and[PostRetrieveCustomerIdConfig](typeTag = "PostRetrieveCustomerId")
    .format
  given format: OFormat[PostSignupStubConfiguration] = Json.format[PostSignupStubConfiguration]
}
