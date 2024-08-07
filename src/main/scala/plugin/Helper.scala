package ch.elca.advisory
package plugin

import java.util.regex.Pattern
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object Helper {

  // All persistenceIds must be uuid
  val uuidPattern: Pattern = Pattern.compile(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
  )

  def getClassTag(className: String): Option[ClassTag[_]] = {
    Try {
      val clazz = Class.forName(className)
      ClassTag(clazz)
    } match {
      case Success(classTag) => Some(classTag)
      case Failure(_) => None
    }
  }

  def sequence[T](seq: Seq[Try[T]]): Try[Seq[T]] = {
    seq.foldRight(Try(Seq.empty[T])) { (tryElement, trySeq) =>
      for {
        seq <- trySeq
        element <- tryElement
      } yield element +: seq
    }
  }
}
