package org.mule.weave.lsp.weave

import org.mule.weave.lsp.DWLspServerUtils.getSimpleProjectWorkspace
import org.mule.weave.lsp.DWProject
import org.scalatest.FreeSpec
import org.scalatest.Matchers

class WeaveValidationTest extends FreeSpec with Matchers {
  val MyMappingPath = "src/MyMapping.dwl"
  val MyNewModulePath = "src/MyModule.dwl"

  "Validation" - {

    "It should validate projects correctly" in {
      val project: DWProject = getSimpleProjectWorkspace()
      project.open(MyMappingPath)
      val errors = project.errorsFor(MyMappingPath)
      errors.size() shouldBe 0
      project.update(MyMappingPath,
        """
          |%dw 2.0
          |---
          |1 to 100 map $ +
          |""".stripMargin)
      val newErrors = project.errorsFor(MyMappingPath)
      newErrors.size() shouldBe (1)
    }


    "It should validate new files correctly" in {
      val project: DWProject = getSimpleProjectWorkspace()

      project.update(MyMappingPath,
        """
          |%dw 2.0
          |---
          |MyModule::test()
          |""".stripMargin)
      val mappingErrors = project.errorsFor(MyMappingPath)
      assert(mappingErrors.size() == 1)

      // We clean the local diagnostics as it should re trigger the build
      project.cleanDiagnostics()

      project.update(MyNewModulePath,
        """
          |%dw 2.0
          |fun test() = 1
          |
          |""".stripMargin)



      val moduleErrors = project.errorsFor(MyNewModulePath)
      assert(moduleErrors.size() == 0)

      val newMappingErrors = project.errorsFor(MyMappingPath)
      assert(newMappingErrors.size() == 0)

    }
  }


}
