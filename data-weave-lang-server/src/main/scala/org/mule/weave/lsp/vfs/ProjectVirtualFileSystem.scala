package org.mule.weave.lsp.vfs

import org.mule.weave.lsp.services.ProjectDefinition
import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.sdk.ChainedWeaveResourceResolver
import org.mule.weave.v2.sdk.NameIdentifierHelper
import org.mule.weave.v2.sdk.WeaveResource
import org.mule.weave.v2.sdk.WeaveResourceResolver

import java.io.File
import java.net.URL
import java.nio.file.Paths
import java.util.logging.Level
import java.util.logging.Logger
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

/**
 * This Virtual File System handles the project interactions.
 *
 * There are two kind of files:
 *
 *  - InMemory ones, this is when a File was either modified or created by still not being saved in the persisted FS.
 *  - Persisted Files, this are the files that are haven't been modified and are the one persisted in the FS
 *
 * @param projectDefinition
 */
class ProjectVirtualFileSystem(projectDefinition: ProjectDefinition) extends VirtualFileSystem {

  private val inMemoryFiles: mutable.Map[String, ProjectVirtualFile] = mutable.Map[String, ProjectVirtualFile]()

  private val vfsChangeListeners: ArrayBuffer[ChangeListener] = ArrayBuffer[ChangeListener]()

  private val logger: Logger = Logger.getLogger(getClass.getName)


  def update(uri: String, content: String): Unit = {
    logger.log(Level.INFO, s"update ${uri} -> ${content}")

    Option(file(uri)) match {
      case Some(vf) => {
        val written = vf.write(content)
        if (written) {
          triggerChanges(vf)
        }
      }
      case None => {
        val virtualFile = new ProjectVirtualFile(this, uri, None, Some(content))
        inMemoryFiles.put(uri, virtualFile)
        triggerChanges(virtualFile)
      }
    }
  }


  /**
   * Mark the specified Uri as closed. All memory representation should be cleaned
   *
   * @param uri The Uri of the file
   */
  def closed(uri: String): Unit = {
    logger.log(Level.INFO, s"closed ${uri}")
    inMemoryFiles.remove(uri)
  }

  /**
   * Mark the given uri as saved into the persisted FS
   *
   * @param uri The uri to be marked as saved
   */
  def saved(uri: String): Unit = {
    inMemoryFiles.get(uri).map(_.save())
  }

  /**
   * Mark a given uri was changed by an external even
   *
   * @param uri The uri to be marked as changed
   */
  def changed(uri: String): Unit = {
    logger.log(Level.INFO, s"logger.log(Level.INFO, changed ${uri}")
    val virtualFile = file(uri)
    triggerChanges(virtualFile)
    //TODO should we remove the inMemoryRepresentation!
    vfsChangeListeners.foreach(_.onChanged(virtualFile))
  }

  /**
   * Mark a given uri was deleted by an external event
   *
   * @param uri The uri to be deleted
   */
  def deleted(uri: String): Unit = {
    logger.log(Level.INFO, s"deleted ${uri}")
    inMemoryFiles.remove(uri)
    val virtualFile = new ProjectVirtualFile(this, uri, None)
    triggerChanges(virtualFile)
    vfsChangeListeners.foreach((listener) => {
      listener.onDeleted(virtualFile)
    })
  }

  def created(uri: String): Unit = {
    logger.log(Level.INFO, s"created ${uri}")
    val virtualFile = new ProjectVirtualFile(this, uri, None)
    triggerChanges(virtualFile)
    vfsChangeListeners.foreach((listener) => {
      listener.onCreated(virtualFile)
    })
  }

  override def changeListener(cl: ChangeListener): Unit = {
    vfsChangeListeners.+=(cl)
  }

  override def onChanged(vf: VirtualFile): Unit = {
    triggerChanges(vf)
  }

  private def triggerChanges(vf: VirtualFile): Unit = {
    vfsChangeListeners.foreach((listener) => {
      listener.onChanged(vf)
    })
  }

  override def removeChangeListener(service: ChangeListener): Unit = {
    vfsChangeListeners.remove(vfsChangeListeners.indexOf(service))
  }


  def sourceRoot: Option[File] = {
    //TODO: implement support for multi source folder
    projectDefinition.sourceFolder().headOption
  }

  override def file(path: String): VirtualFile = {
    logger.log(Level.INFO, s"file ${path}")
    //absolute path
    if (inMemoryFiles.contains(path)) {
      inMemoryFiles(path)
    } else {
      //It may not be a valid url then just try on nextone
      val maybeFile = Try(Paths.get(new URL(path).toURI).toFile).toOption
      if (maybeFile.isEmpty) {
        null
      } else {
        if (maybeFile.get.exists()) {
          val virtualFile = new ProjectVirtualFile(this, path, Some(maybeFile.get))
          inMemoryFiles.put(path, virtualFile)
          virtualFile
        } else {
          null
        }
      }
    }
  }

  override def asResourceResolver: WeaveResourceResolver = {
    val resourceResolver = sourceRoot match {
      case Some(rootFile) => {
        val folderWeaveResourceResolver = new FolderWeaveResourceResolver(rootFile, this)
        val inMemoryResourceResolver = new InMemoryVirtualFileResourceResolver(sourceRoot, inMemoryFiles)
        new ChainedWeaveResourceResolver(Seq(inMemoryResourceResolver, folderWeaveResourceResolver))
      }
      case None => {
        new InMemoryVirtualFileResourceResolver(sourceRoot, inMemoryFiles)
      }
    }
    resourceResolver
  }

  override def listFilesByNameIdentifier(filter: String): Array[VirtualFile] = {
    sourceRoot match {
      case Some(rootFolder) => {
        val parts = filter.split("::")
        val headAndLast = parts.splitAt(parts.length - 1)
        val container = new File(rootFolder, headAndLast._1.mkString(File.separator))
        val files: Array[File] = container.listFiles((f) => {
          f.getName.contains(headAndLast._2.head)
        })
        if (files != null) {
          files
            .map((f) => {
              file(FileUtils.toUrl(f))
            })
        } else {
          inMemoryFiles.values.filter((vfs) => {
            vfs.getNameIdentifier.toString().contains(filter)
          }).toArray
        }
      }
      case None => {
        inMemoryFiles.values.filter((vfs) => {
          vfs.getNameIdentifier.toString().contains(filter)
        }).toArray
      }
    }
  }
}

class InMemoryVirtualFileResourceResolver(rootFile: Option[File], inMemoryFiles: mutable.Map[String, ProjectVirtualFile]) extends WeaveResourceResolver {

  override def resolvePath(path: String): Option[WeaveResource] = {
    val str = rootFile.map((rf) => {
      val relativePath = if (path.startsWith("/")) {
        path.substring(1)
      } else {
        path
      }
      Paths.get(rf.getPath).resolve(relativePath).toUri.toString
    }).getOrElse(path)
    this.inMemoryFiles.get(str).map((f) => WeaveResource(f))
  }

  override def resolveAll(name: NameIdentifier): Seq[WeaveResource] = {
    super.resolveAll(name)
  }

  override def resolve(name: NameIdentifier): Option[WeaveResource] = {
    val path: String = NameIdentifierHelper.toWeaveFilePath(name, "/")
    resolvePath(path)
  }
}

object FileUtils {
  /**
   * Build the url according to vscode standard
   *
   * @param theFile The file to get the url from
   * @return
   */
  def toUrl(theFile: File): String = {
    "file://" + theFile.toURI.toURL.getPath
  }
}


