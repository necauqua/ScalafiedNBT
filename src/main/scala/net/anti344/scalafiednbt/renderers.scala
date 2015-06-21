package net.anti344.scalafiednbt

import scala.collection.mutable.{Buffer => MBuffer, LinkedHashMap => MMap}

/**
 * Licenced under LGPLv3
 * @author anti344
 **/
object renderers{

  /** Thing to be implicitly used by the render method. **/
  trait NBTRenderer{

    /**
      * Visualises given tag to a string.
      * @param tag tag to be visualised.
      * @return Visualized tag.
      **/
    def render(tag: Tag, name: String = ""): String = recursiveRender(tag, name, 1)

    protected def recursiveRender(tag: Tag, name: String, offset: Int): String =
      handleName(tag, name) + (tag.payload match{
        case map: MMap[String @unchecked, Tag @unchecked] => handleCompound(map, name, offset)
        case buf: MBuffer[Tag @unchecked] => handleList(buf, name, offset)
        case e => handlePrimitive(e, name)
      })

    protected def handleName(tag: Tag, name: String): String

    protected def handleList(buf: MBuffer[Tag], name: String, off: Int): String

    protected def handleCompound(map: MMap[String, Tag], name: String, off: Int): String

    protected def handlePrimitive(prim: Any, name: String): String
  }

  private[renderers] trait JsonLike extends NBTRenderer{

    def handleName(tag: Tag, name: String): String = if(name != "")"\"" + name.replaceAll("\"", "\\\\\"") + "\" : " else ""

    def handleCompound(map: MMap[String, Tag], name: String, off: Int): String =
      if(map.nonEmpty){
        map.map({case (k, v) => recursiveRender(v, k, off + 1)})
          .mkString("{\n" + "  " * off, ",\n" + "  " * off, "\n" + ("  " * (off - 1)) + "}")
      }else "{}"

    def handleList(buf: MBuffer[Tag], name: String, off: Int): String =
      if(buf.nonEmpty){
        if(9 to 10 contains buf.head.id){
          buf.map({e => recursiveRender(e, "", off + 1)})
            .mkString("[\n" + "  " * off, ",\n" + "  " * off, "\n" + ("  " * (off - 1))  + "]")
        }else buf.map({e => recursiveRender(e, "", 0)}).mkString("[", ", ", "]")
      }else "[]"
  }

  /**
    * Generated valid JSON.<br>
    * Arrays are represented as Json arrays(lists are too), but with "byte_array" or "int_array" mark at the beginning.
    **/
  implicit val json = new JsonLike{

    def handlePrimitive(prim: Any, name: String): String =
      prim match{
        case str: String => "\"" + str.replaceAll("\"", "\\\\\"") + "\""
        case ba: Array[Byte] => ba.mkString("[ \"byte_array\", ", ", ", "]")
        case ia: Array[Int] => ia.mkString("[ \"int_array\", ", ", ", "]")
        case e => e.toString
      }
  }

  /**
    * Generates valid Mojang's superset of JSON.<br>
    * Arrays are not supported, but still represented by their size.
    **/
  implicit val mojangson = new JsonLike{

    def handlePrimitive(prim: Any, name: String): String =
      prim match{
        case string: String => "\"" + string.replaceAll("\"", "\\\\\"") + "\""
        case array: Array[Byte] => s"[${array.length} bytes]"
        case array: Array[Int] => s"[${array.length} ints]"
        case i: Int => i.toString
        case d: Double => d.toString
        case e => e + e.getClass.getSimpleName.substring(0, 1).toLowerCase //Cheating
      }
  }

  /** Generates tag representation shown in original Notch's NBT specs. **/
  implicit val notch = new NBTRenderer{

    def handleName(tag: Tag, name: String): String = Tag.types(tag.id) + (if(name != "")"(\"" + name.replaceAll("\"", "\\\\\"") + "\")" else "") + ": "

    private def entries(num: Int): String = if(num == 1)"1 entry" else s"$num entries"

    def handleCompound(map: MMap[String, Tag], name: String, off: Int): String =
      s"${entries(map.size)}\n" + "   " * (off - 1) +
        (if(map.nonEmpty){
          map.map({case (k, v) => recursiveRender(v, k, off + 1)})
            .mkString("{\n" + "   " * off, "\n" + "   " * off, "\n" + ("   " * (off - 1)) + "}")
        }else "{}")

    def handleList(buf: MBuffer[Tag], name: String, off: Int): String =
      s"${entries(buf.size)}${buf.headOption.map({t => s" of type ${Tag.types(t.id)}"}).getOrElse("")}\n" + "   " * (off - 1) +
        (if(buf.nonEmpty){
          buf.map({e => recursiveRender(e, "", off + 1)})
            .mkString("{\n" + "   " * off, "\n" + "   " * off, "\n" + ("   " * (off - 1))  + "}")
        }else "{}")

    def handlePrimitive(prim: Any, name: String): String =
      prim match{
        case byteArray: Array[Byte] => s"[${byteArray.length} bytes]"
        case intArray: Array[Int] => s"[${intArray.length} ints]"
        case e => e.toString
      }
  }

  /** Generates valid Scala code (with DSL from this lib ofc), which is EXTREMELY COOL. **/
  implicit val code = new NBTRenderer{

    def handleName(tag: Tag, name: String): String = if(name != "")"\"" + name.replaceAll("\"", "\\\\\"") + "\" := " else ""

    def handleCompound(map: MMap[String, Tag], name: String, off: Int): String =
      if(map.nonEmpty){
        map.map({case (k, v) => recursiveRender(v, k, off + 1)})
          .mkString("tag{\n" + "  " * off, "\n" + "  " * off, "\n" + ("  " * (off - 1)) + "}")
      }else "tag{}"

    def handleList(buf: MBuffer[Tag], name: String, off: Int): String =
      if(buf.nonEmpty){
        if(9 to 10 contains buf.head.id){
          buf.map({e => recursiveRender(e, "", off + 1)})
            .mkString("list(\n" + "  " * off, ",\n" + "  " * off, "\n" + ("  " * (off - 1))  + ")")
        }else buf.map({e => recursiveRender(e, "", 0)}).mkString("list(", ", ", ")")
      }else "list()"

    def handlePrimitive(prim: Any, name: String): String =
      prim match{
        case string: String => "\"" + string.replaceAll("\"", "\\\\\"") + "\""
        case byteArray: Array[Byte] => byteArray.mkString("Array[Byte](", ", ", ")")
        case intArray: Array[Int] => intArray.mkString("Array(", ", ", ")")
        case b: Byte => b + ".toByte"
        case s: Short => s + ".toShort"
        case l: Long => l + "L"
        case f: Float => f + "F"
        case e => e.toString
      }
  }
}