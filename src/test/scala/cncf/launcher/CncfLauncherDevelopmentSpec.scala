/*
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
package cncf.launcher

import org.scalatest.DoNotDiscover
import org.scalatest.wordspec.AnyWordSpec

@DoNotDiscover
final class CncfLauncherDevelopmentSpec extends AnyWordSpec with CncfLauncherDevelopmentSpecSupport {
  "cncf launcher" should {
    "development runtime operations" which {
      "dev server rewrites to cncf args" in {
        Given("the cncf launcher scenario: dev server rewrites to cncf args")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devServerRewritesToCncfArgs())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev server writes state during invocation" in {
        Given("the cncf launcher scenario: dev server writes state during invocation")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devServerWritesStateDuringInvocation())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev server rejects alive existing state" in {
        Given("the cncf launcher scenario: dev server rejects alive existing state")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devServerRejectsAliveExistingState())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev server stop existing before invocation" in {
        Given("the cncf launcher scenario: dev server stop existing before invocation")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devServerStopExistingBeforeInvocation())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev stop stops existing without invocation" in {
        Given("the cncf launcher scenario: dev stop stops existing without invocation")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devStopStopsExistingWithoutInvocation())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev stop uses recorded port when port is omitted" in {
        Given("the cncf launcher scenario: dev stop uses recorded port when port is omitted")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devStopUsesRecordedPortWhenPortIsOmitted())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev server force stops after graceful failure" in {
        Given("the cncf launcher scenario: dev server force stops after graceful failure")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devServerForceStopsAfterGracefulFailure())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev server requires force for ambiguous alive state" in {
        Given("the cncf launcher scenario: dev server requires force for ambiguous alive state")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devServerRequiresForceForAmbiguousAliveState())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev server rejects pid reuse without force" in {
        Given("the cncf launcher scenario: dev server rejects pid reuse without force")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devServerRejectsPidReuseWithoutForce())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev server profile adds local persistent sqlite args" in {
        Given("the cncf launcher scenario: dev server profile adds local persistent sqlite args")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devServerProfileAddsLocalPersistentSqliteArgs())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev server uses runtime development directory" in {
        Given("the cncf launcher scenario: dev server uses runtime development directory")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devServerUsesRuntimeDevelopmentDirectory())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev server uses runtime development catalog for selection" in {
        Given("the cncf launcher scenario: dev server uses runtime development catalog for selection")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devServerUsesRuntimeDevelopmentCatalogForSelection())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev command passes runtime leading args" in {
        Given("the cncf launcher scenario: dev command passes runtime leading args")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devCommandPassesRuntimeLeadingArgs())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev command keeps sample main class value as runtime arg" in {
        Given("the cncf launcher scenario: dev command keeps sample main class value as runtime arg")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devCommandKeepsSampleMainClassValueAsRuntimeArg())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev command can disable project classpath" in {
        Given("the cncf launcher scenario: dev command can disable project classpath")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devCommandCanDisableProjectClasspath())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev command can disable project component dev dir" in {
        Given("the cncf launcher scenario: dev command can disable project component dev dir")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devCommandCanDisableProjectComponentDevDir())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev command does not auto activate component dir artifacts" in {
        Given("the cncf launcher scenario: dev command does not auto activate component dir artifacts")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devCommandDoesNotAutoActivateComponentDirArtifacts())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev target options are mutually exclusive" in {
        Given("the cncf launcher scenario: dev target options are mutually exclusive")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devTargetOptionsAreMutuallyExclusive())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev name target uses local snapshot only" in {
        Given("the cncf launcher scenario: dev name target uses local snapshot only")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devNameTargetUsesLocalSnapshotOnly())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev name target snapshot bypasses release catalog" in {
        Given("the cncf launcher scenario: dev name target snapshot bypasses release catalog")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devNameTargetSnapshotBypassesReleaseCatalog())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev name target uses release repositories" in {
        Given("the cncf launcher scenario: dev name target uses release repositories")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devNameTargetUsesReleaseRepositories())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev name target uses only its selected artifact repository" in {
        Given("the same released CAR in local and cache repositories")
        When("a deprecated dev name target selects the cache repository explicitly")
        val outcome = scala.util.Try(devNameTargetUsesSelectedArtifactRepository())
        Then("resolution and runtime arguments retain only the selected repository without defaults")
        outcome.get shouldBe ()
      }

      "dev artifact repository rejects missing name or empty selector" in {
        Given("a deprecated dev artifact repository option")
        When("it is supplied without a name target or without a value")
        val outcome = scala.util.Try(devArtifactRepositoryRejectsInvalidTarget())
        Then("the parser rejects the invalid combination before runtime invocation")
        outcome.get shouldBe ()
      }

      "dev server emulation rewrites to cncf args" in {
        Given("the cncf launcher scenario: dev server emulation rewrites to cncf args")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devServerEmulationRewritesToCncfArgs())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev help explains resolution model" in {
        Given("the cncf launcher scenario: dev help explains resolution model")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devHelpExplainsResolutionModel())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev check reports main target and dependency resolution" in {
        Given("the cncf launcher scenario: dev check reports main target and dependency resolution")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devCheckReportsMainTargetAndDependencyResolution())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev check reports dev server state" in {
        Given("the cncf launcher scenario: dev check reports dev server state")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devCheckReportsDevServerState())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev check treats missing main target classpath as error" in {
        Given("the cncf launcher scenario: dev check treats missing main target classpath as error")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devCheckTreatsMissingMainTargetClasspathAsError())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev check treats missing dependency classpath as error" in {
        Given("the cncf launcher scenario: dev check treats missing dependency classpath as error")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devCheckTreatsMissingDependencyClasspathAsError())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev server requires prepared main target classpath" in {
        Given("the cncf launcher scenario: dev server requires prepared main target classpath")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devServerRequiresPreparedMainTargetClasspath())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "runtime development requires prepared classpath" in {
        Given("the cncf launcher scenario: runtime development requires prepared classpath")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(runtimeDevelopmentRequiresPreparedClasspath())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "runtime development reads newline-delimited classpath entries in order" in {
        Given("the cncf launcher scenario: runtime development reads newline-delimited classpath entries in order")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(runtimeDevelopmentReadsNewlineDelimitedClasspath())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev uses current compatible runtime by default" in {
        Given("the cncf launcher scenario: dev uses current compatible runtime by default")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devUsesCurrentCompatibleRuntimeByDefault())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev can select latest tested runtime" in {
        Given("the cncf launcher scenario: dev can select latest tested runtime")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devCanSelectLatestTestedRuntime())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev can select latest compatible runtime" in {
        Given("the cncf launcher scenario: dev can select latest compatible runtime")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devCanSelectLatestCompatibleRuntime())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev can select newest compatible runtime" in {
        Given("the cncf launcher scenario: dev can select newest compatible runtime")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devCanSelectNewestCompatibleRuntime())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev parses inline runtime requirement lists" in {
        Given("the cncf launcher scenario: dev parses inline runtime requirement lists")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devParsesInlineRuntimeRequirementLists())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev selects common runtime across project and dependency" in {
        Given("the cncf launcher scenario: dev selects common runtime across project and dependency")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devSelectsCommonRuntimeAcrossProjectAndDependency())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev runtime conflict defaults to error" in {
        Given("the cncf launcher scenario: dev runtime conflict defaults to error")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devRuntimeConflictDefaultsToError())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev runtime conflict can use newest policy" in {
        Given("the cncf launcher scenario: dev runtime conflict can use newest policy")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devRuntimeConflictCanUseNewestPolicy())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

    }

    "packaging boundaries" which {
      "no runtime library dependencies" in {
        Given("the cncf launcher scenario: no runtime library dependencies")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(noCncfRuntimeLibraryDependencies())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

    }

  }
  }
