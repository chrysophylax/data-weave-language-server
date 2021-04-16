package org.mule.weave.lsp.vfs

import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.sdk.ChainedWeaveResourceResolver
import org.mule.weave.v2.sdk.WeaveResourceResolver

import java.util.logging.Level
import java.util.logging.Logger

class ChainedVirtualFileSystem(modules: Seq[VirtualFileSystem]) extends VirtualFileSystem {

  private val logger: Logger = Logger.getLogger(getClass.getName)


  override def file(path: String): VirtualFile = {
    logger.log(Level.INFO, s"file ${path}")
    modules
      .toStream
      .flatMap((vfs) => {
        logger.log(Level.INFO, "Module:" + vfs)
        Option(vfs.file(path))
      })
      .headOption
      .orNull
  }


  override def changeListener(cl: ChangeListener): Unit = {
    modules.foreach(_.changeListener(cl))
  }

  override def onChanged(virtualFile: VirtualFile): Unit = {
    modules.foreach(_.onChanged(virtualFile))
  }

  override def removeChangeListener(service: ChangeListener): Unit = {
    modules.foreach(_.removeChangeListener(service))
  }

  override def asResourceResolver: WeaveResourceResolver = {
    new ChainedWeaveResourceResolver(modules.map(_.asResourceResolver))
  }

  override def listFilesByNameIdentifier(filter: String): Array[VirtualFile] = {
    modules.flatMap(_.listFilesByNameIdentifier(filter)).toArray
  }
}