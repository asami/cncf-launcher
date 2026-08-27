/*
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
package cncf.launcher

import org.scalatest.DoNotDiscover
import org.scalatest.wordspec.AnyWordSpec

@DoNotDiscover
final class CncfLauncherCommandSpec extends AnyWordSpec with CncfLauncherCommandSpecSupport {
  "cncf launcher" should {
    "command parsing" which {
      "parser" in {
        Given("the cncf launcher scenario: parser")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(parser())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "runtime catalog parse and selector resolution" in {
        Given("the cncf launcher scenario: runtime catalog parse and selector resolution")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(runtimeCatalogParseAndSelectorResolution())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev parser" in {
        Given("the cncf launcher scenario: dev parser")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devParser())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "dev server parser supports process management options" in {
        Given("the cncf launcher scenario: dev server parser supports process management options")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(devServerParserSupportsProcessManagementOptions())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

    }

    "component repository development discovery" which {
      "parse local and admitted development commands" in {
        Given("the cncf launcher scenario: componentRepositoryCommandParser")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(componentRepositoryCommandParser())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "prefer admitted development identity over local release identity" in {
        Given("the cncf launcher scenario: componentRepositoryDevelopmentOverridesLocalIdentity")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(componentRepositoryDevelopmentOverridesLocalIdentity())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "preserve namespace-qualified CAR identity through list and show selection" in {
        Given("the cncf launcher scenario: componentRepositoryPreservesNamespaceQualifiedCarIdentity")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(componentRepositoryPreservesNamespaceQualifiedCarIdentity())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "reject implicit development directory discovery" in {
        Given("the cncf launcher scenario: componentRepositoryRejectsImplicitDevelopmentDirectory")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(componentRepositoryRejectsImplicitDevelopmentDirectory())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "show a directory using descriptor identity" in {
        Given("the cncf launcher scenario: componentRepositoryShowUsesDescriptorIdentity")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(componentRepositoryShowUsesDescriptorIdentity())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "match Textus identity output columns" in {
        Given("the cncf launcher scenario: componentRepositoryOutputMatchesTextusIdentityColumns")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(componentRepositoryOutputMatchesTextusIdentityColumns())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "reject malformed and legacy local indexes without mutating the warehouse" in {
        Given("the cncf launcher scenario: componentRepositoryRejectsInvalidLocalIndex")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(componentRepositoryRejectsInvalidLocalIndex())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }

      "run repository discovery without loading CNCF runtime" in {
        Given("the cncf launcher scenario: componentRepositoryCommandDoesNotLoadCncfRuntime")
        When("the launcher behavior is exercised")
        val outcome = scala.util.Try(componentRepositoryCommandDoesNotLoadCncfRuntime())
        Then("the executable specification holds through scenario-specific expectations")
        outcome.get shouldBe ()
      }
    }
  }
}
