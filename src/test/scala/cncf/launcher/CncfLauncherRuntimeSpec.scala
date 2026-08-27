/*
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
package cncf.launcher

import org.scalatest.DoNotDiscover
import org.scalatest.wordspec.AnyWordSpec

@DoNotDiscover
final class CncfLauncherRuntimeSpec extends AnyWordSpec with CncfLauncherRuntimeSpecSupport {
  "cncf launcher" should {
    "configuration and launcher metadata" which {
      "launcher version" in {
        Given("the cncf launcher scenario: launcher version")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(launcherVersion())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "config merge" in {
        Given("the cncf launcher scenario: config merge")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(configMerge())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "launcher dev dir delegates to development launcher" in {
        Given("the cncf launcher scenario: launcher dev dir delegates to development launcher")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(launcherDevDirDelegatesToDevelopmentLauncher())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "launcher dev dir rejects stale development classpath" in {
        Given("the cncf launcher scenario: launcher dev dir rejects stale development classpath")
        When("the launcher development classpath does not contain the launcher main class")
        val outcome = scala.util.Try(launcherDevDirRejectsStaleDevelopmentClasspath())
        Then("the launcher reports the stale classpath before spawning the delegated process")
        outcome.get shouldBe ()
      }

      "config supports additional rdf namespaces" in {
        Given("the cncf launcher scenario: config supports additional rdf namespaces")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(configSupportsAdditionalRdfNamespaces())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "config file option overrides project config" in {
        Given("the cncf launcher scenario: config file option overrides project config")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(configFileOptionOverridesProjectConfig())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "workspace root config applies to nested cwd" in {
        Given("the cncf launcher scenario: workspace root config applies to nested cwd")
        When("the launcher loads config from a nested sample directory")
        val outcome = scala.util.Try(workspaceRootConfigAppliesToNestedCwd())
        Then("the executable specification holds through inherited root config")
        outcome.get shouldBe ()
      }

      "launcher config controls development runtime" in {
        Given("the cncf launcher scenario: launcherConfigControlsDevelopmentRuntime")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(launcherConfigControlsDevelopmentRuntime())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "launcher config supports properties and conf files" in {
        Given("the cncf launcher scenario: launcher config supports properties and conf files")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(launcherConfigSupportsPropertiesAndConfFiles())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "default runtime config files are forwarded" in {
        Given("the cncf launcher scenario: default runtime config files are forwarded")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(defaultRuntimeConfigFilesAreForwarded())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "config file project dev survives target cwd switch" in {
        Given("the cncf launcher scenario: config file project dev survives target cwd switch")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(configFileProjectDevSurvivesTargetCwdSwitch())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "cncf config option is forwarded to runtime" in {
        Given("the cncf launcher scenario: cncf config option is forwarded to runtime")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(cncfConfigOptionIsForwardedToRuntime())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "config file option requires existing file" in {
        Given("the cncf launcher scenario: config file option requires existing file")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(configFileOptionRequiresExistingFile())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev config can select execution profile" in {
        Given("the cncf launcher scenario: dev config can select execution profile")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devConfigCanSelectExecutionProfile())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev project loads target project config" in {
        Given("the cncf launcher scenario: dev project loads target project config")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devProjectLoadsTargetProjectConfig())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

    }

    "runtime selection and catalog operations" which {
      "runtime version" in {
        Given("the cncf launcher scenario: runtime version")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(runtimeVersion())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "runtime help" in {
        Given("the cncf launcher scenario: runtime help")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(runtimeHelp())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "runtime version precedence" in {
        Given("the cncf launcher scenario: runtime version precedence")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(runtimeVersionPrecedence())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "canonical development target selects a compatible installed runtime" in {
        Given("a CAR target requiring a newer installed SNAPSHOT than the stored runtime")
        When("the canonical target-first server command selects its runtime")
        val outcome = scala.util.Try(canonicalDevelopmentTargetSelectsCompatibleInstalledRuntime())
        Then("the target requirement supersedes the incompatible stored runtime")
        outcome.get shouldBe ()
      }

      "runtime use writes expected files" in {
        Given("the cncf launcher scenario: runtime use writes expected files")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(runtimeUseWritesExpectedFiles())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "runtime use selects an exact local snapshot without a catalog entry" in {
        Given("an exact locally available SNAPSHOT runtime version")
        When("the developer selects it as the global runtime")
        val outcome = scala.util.Try(runtimeUseSelectsExactLocalSnapshot())
        Then("the exact SNAPSHOT version is retained for subsequent development commands")
        outcome.get shouldBe ()
      }

      "snapshot runtime refreshes its cached classpath" in {
        Given("a cached classpath for a locally republished SNAPSHOT runtime")
        When("the runtime resolver resolves that SNAPSHOT again")
        val outcome = scala.util.Try(snapshotRuntimeRefreshesCachedClasspath())
        Then("the resolver fetches current dependency metadata instead of reusing the stale classpath")
        outcome.get shouldBe ()
      }

      "runtime use auto selects project when cncf directory exists" in {
        Given("the cncf launcher scenario: runtime use auto selects project when cncf directory exists")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(runtimeUseAutoSelectsProjectWhenCncfDirectoryExists())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "install cli writes development command" in {
        Given("launcher configuration selects the runtime for a development command")
        When("the launcher installs a development command")
        val outcome = scala.util.Try(installCliWritesDevelopmentCommand())
        Then("the command delegates runtime selection to launcher configuration")
        outcome.get shouldBe ()
      }

      "install cli pins an explicitly requested runtime version" in {
        Given("an explicit runtime version for a development command")
        When("the launcher installs the development command")
        val outcome = scala.util.Try(installCliPinsExplicitRuntimeVersion())
        Then("the wrapper retains the explicit runtime version")
        outcome.get shouldBe ()
      }

      "install cli pins an explicit development runtime without a runtime catalog" in {
        Given("a development runtime and component requirements with compatible mixed minimum versions")
        When("the launcher installs a development command")
        val outcome = scala.util.Try(installCliPinsDevelopmentRuntimeWithoutCatalog())
        Then("the wrapper pins the runtime directory without selecting from the normal catalog")
        outcome.get shouldBe ()
      }

      "install cli rejects an explicit incompatible development runtime" in {
        Given("a development runtime below the target component minimum version")
        When("the launcher installs a development command")
        val outcome = scala.util.Try(installCliRejectsIncompatibleDevelopmentRuntime())
        Then("the incompatible runtime is rejected before a wrapper is written")
        outcome.get shouldBe ()
      }

      "canonical server commands report one Textus Control Center lifecycle" in {
        Given("the cncf launcher scenario: textusControlCenterRegistrationLifecycle")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(textusControlCenterRegistrationLifecycle())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "Control Center configuration applies only to the current project server" in {
        Given("the cncf launcher scenario: controlCenterConfigAppliesOnlyToCurrentProject")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(controlCenterConfigAppliesOnlyToCurrentProject())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "retain bounded shared evidence and preserve malformed evidence for recovery" in {
        Given("the cncf launcher scenario: localServerEvidenceRetentionAndRecovery")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(localServerEvidenceRetentionAndRecovery())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "canonical server commands resolve one standalone Control Center locator" in {
        Given("the cncf launcher scenario: standaloneControlCenterLocatorLifecycle")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(standaloneControlCenterLocatorLifecycle())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "Textus Control Center reporter calls automatic REST operations" in {
        Given("the cncf launcher scenario: textusControlCenterRegistrationHttpLifecycle")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(textusControlCenterRegistrationHttpLifecycle())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "Textus Control Center authorization rejection is isolated from server lifecycle" in {
        Given("the cncf launcher scenario: textusControlCenterRegistrationHttpFailureIsolation")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(textusControlCenterRegistrationHttpFailureIsolation())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "target-first execution delegates to runtime" in {
        Given("the cncf launcher scenario: target-first execution delegates to runtime")
        When("the launcher receives canonical target-first syntax")
        val outcome = scala.util.Try(executeTargetFirstDelegatesToRuntime())
        Then("the runtime receives expanded runtime activation arguments")
        outcome.get shouldBe ()
      }

      "HOME is the shared launcher and CNCF runtime home boundary" in {
        Given("the cncf launcher scenario: launcherHomeEnvironmentPropagatesToRuntime")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(launcherHomeEnvironmentPropagatesToRuntime())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "canonical target and runtime boundary" which {
        "CAR development targets use the configured CNCF development runtime" in {
          Given("the cncf launcher scenario: canonicalDevelopmentTargetsUseConfiguredRuntime")
          When("the launcher behavior is exercised")
          val outcome = scala.util.Try(canonicalDevelopmentTargetsUseConfiguredRuntime())
          Then("the executable specification holds through scenario-specific expectations")
          outcome.get shouldBe ()
        }

        "a stale catalog is refreshed when the target requires a newer runtime" in {
          Given("the cncf launcher scenario: canonicalDevelopmentTargetRefreshesStaleRuntimeCatalog")
          When("the launcher behavior is exercised")
          val outcome = scala.util.Try(canonicalDevelopmentTargetRefreshesStaleRuntimeCatalog())
          Then("the executable specification holds through scenario-specific expectations")
          outcome.get shouldBe ()
        }

        "a stale catalog remains usable when its refresh source is unavailable" in {
          Given("the cncf launcher scenario: canonicalDevelopmentTargetRetainsStaleRuntimeCatalogWhenRefreshFails")
          When("the launcher behavior is exercised")
          val outcome = scala.util.Try(canonicalDevelopmentTargetRetainsStaleRuntimeCatalogWhenRefreshFails())
          Then("the executable specification holds through scenario-specific expectations")
          outcome.get shouldBe ()
        }

        "an explicit runtime version overrides a configured development runtime" in {
          Given("the cncf launcher scenario: canonicalDevelopmentTargetUsesExplicitRuntimeVersion")
          When("the launcher behavior is exercised")
          val outcome = scala.util.Try(canonicalDevelopmentTargetUsesExplicitRuntimeVersion())
          Then("the executable specification holds through scenario-specific expectations")
          outcome.get shouldBe ()
        }

        "an explicit development runtime retains the CAR execution target" in {
          Given("the cncf launcher scenario: canonicalDevelopmentTargetUsesExplicitDevelopmentRuntime")
          When("the launcher behavior is exercised")
          val outcome = scala.util.Try(canonicalDevelopmentTargetUsesExplicitDevelopmentRuntime())
          Then("the executable specification holds through scenario-specific expectations")
          outcome.get shouldBe ()
        }

        "a CNCF runtime checkout is rejected as a positional component target" in {
          Given("the cncf launcher scenario: canonicalServerRejectsRuntimeCheckoutTarget")
          When("the launcher behavior is exercised")
          val outcome = scala.util.Try(canonicalServerRejectsRuntimeCheckoutTarget())
          Then("the executable specification holds through scenario-specific expectations")
          outcome.get shouldBe ()
        }

        "deprecated dev execution keeps its target and runtime classpaths separate" in {
          Given("the cncf launcher scenario: deprecatedDevSeparatesDevelopmentTargetFromRuntime")
          When("the launcher behavior is exercised")
          val outcome = scala.util.Try(deprecatedDevSeparatesDevelopmentTargetFromRuntime())
          Then("the executable specification holds through scenario-specific expectations")
          outcome.get shouldBe ()
        }
      }

      "server execution delegates default port resolution to the runtime" in {
        Given("the cncf launcher scenario: serverExecutionDelegatesDefaultPortResolutionToRuntime")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(serverExecutionDelegatesDefaultPortResolutionToRuntime())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "runtime catalog commands" in {
        Given("the cncf launcher scenario: runtime catalog commands")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(runtimeCatalogCommands())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "runtime current warns when cached recommended is stale" in {
        Given("the cncf launcher scenario: runtime current warns when cached recommended is stale")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(runtimeCurrentWarnsWhenCachedRecommendedIsStale())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "runtime descriptor commands" in {
        Given("the cncf launcher scenario: runtime descriptor commands")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(runtimeDescriptorCommands())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "runtime descriptor prefers runtime jar descriptor" in {
        Given("the cncf launcher scenario: runtime descriptor prefers runtime jar descriptor")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(runtimeDescriptorPrefersRuntimeJarDescriptor())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "runtime command does not load cncf" in {
        Given("the cncf launcher scenario: runtime command does not load cncf")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(runtimeCommandDoesNotLoadCncf())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "latest runtime is concrete" in {
        Given("the cncf launcher scenario: latest runtime is concrete")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(latestRuntimeIsConcrete())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

    }
  }
}
