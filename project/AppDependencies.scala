import play.core.PlayVersion
import play.sbt.PlayImport.*
import sbt.Keys.{dependencyOverrides, libraryDependencies}
import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.13.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                     %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc"                     %% "domain-test-play-30"       % "13.0.0",
    "com.networknt"                    % "json-schema-validator"     % "2.0.0",
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml"   % "2.18.3",
    "com.fasterxml.jackson.module"    %% "jackson-module-scala"      % "2.18.3"
  )

  dependencyOverrides ++= Seq(
    "com.fasterxml.jackson.core"       % "jackson-core"            % "2.18.3",
    "com.fasterxml.jackson.core"       % "jackson-databind"        % "2.18.3",
    "com.fasterxml.jackson.core"       % "jackson-annotations"     % "2.18.3",
    "com.fasterxml.jackson.module"    %% "jackson-module-scala"    % "2.18.3",
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.18.3"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion % Test
  )

  val it: Seq[ModuleID] = Seq.empty
}
