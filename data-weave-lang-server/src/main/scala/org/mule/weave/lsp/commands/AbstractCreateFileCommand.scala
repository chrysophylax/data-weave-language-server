package org.mule.weave.lsp.commands

import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import org.eclipse.lsp4j
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.CreateFile
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.mule.weave.lsp.extension.client.OpenTextDocumentParams
import org.mule.weave.lsp.extension.client.WeaveInputBoxParams
import org.mule.weave.lsp.extension.client.WeaveInputBoxResult
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.utils.URLUtils.toLSPUrl

import java.io.File
import java.util

abstract class AbstractCreateFileCommand extends WeaveCommand {

  def project: ProjectKind

  def weaveLanguageClient: WeaveLanguageClient

  def getInputLabel: String

  def getDefaultName: String

  def getTemplate: String

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val modules = project.structure().modules
    if (modules.length == 1) {
      val maybeStructure = ProjectStructure.mainRoot(modules.head)
      maybeStructure match {
        case Some(rootSource) => {
          val defaultSourceFolder = rootSource.defaultSourceFolder
          val result: WeaveInputBoxResult = weaveLanguageClient.weaveInputBox(WeaveInputBoxParams(getInputLabel, getDefaultName)).get()
          if (!result.cancelled) {
            var name = result.value
            if (StringUtils.isEmpty(FilenameUtils.getExtension(name))) {
              name = name + ".dwl"
            }

            val mappingFile = toLSPUrl(new File(defaultSourceFolder, name))
            val createFile: Either[TextDocumentEdit, ResourceOperation] = Either.forRight[TextDocumentEdit, ResourceOperation](new CreateFile(mappingFile))

            val textEdit: TextEdit = new TextEdit(new lsp4j.Range(new Position(0, 0), new Position(0, 0)), getTemplate)
            val textDocumentEdit: TextDocumentEdit = new TextDocumentEdit(new VersionedTextDocumentIdentifier(mappingFile, 0), util.Arrays.asList(textEdit))
            val insertText = Either.forLeft[TextDocumentEdit, ResourceOperation](textDocumentEdit)

            val edits: util.List[Either[TextDocumentEdit, ResourceOperation]] = new util.ArrayList[Either[TextDocumentEdit, ResourceOperation]]()
            edits.add(createFile)
            edits.add(insertText)
            edits.addAll(util.Arrays.asList(project.newFile(defaultSourceFolder, name): _*))

            val response = weaveLanguageClient.applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(edits))).get()
            if (response.isApplied) {
              weaveLanguageClient.openTextDocument(OpenTextDocumentParams(mappingFile))
            }
          }
        }
        case None =>
      }
    }
    null
  }

}
