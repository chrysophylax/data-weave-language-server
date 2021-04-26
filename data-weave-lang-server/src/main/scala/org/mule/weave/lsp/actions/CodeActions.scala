package org.mule.weave.lsp.actions

import org.eclipse.lsp4j.CodeActionParams
import org.mule.weave.lsp.services.ValidationServices

class CodeActions(weaveService: ValidationServices) {

  private val actions = Seq(
    new QuickFixAction(weaveService),
    new InsertDocumentationAction(weaveService)
  )

  def actionsFor(params: CodeActionParams): Seq[CodeActionProvider] = {
    actions.filter(_.handles(params))
  }

}
