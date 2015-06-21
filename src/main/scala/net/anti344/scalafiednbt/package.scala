package net.anti344

import java.io._
import java.util.zip.GZIPInputStream

import scala.util.DynamicVariable

/**
  * Licenced under LGPLv3
  * @author anti344
  **/
package object scalafiednbt{

  import scala.language.implicitConversions

  private[scalafiednbt] val currentTag = new DynamicVariable[Tag](null)

  /** Just a set of constants to use `Tags.compound` instead of 10 **/
  object Tags{

    val end       : Byte = 0
    val byte      : Byte = 1
    val short     : Byte = 2
    val int       : Byte = 3
    val long      : Byte = 4
    val float     : Byte = 5
    val double    : Byte = 6
    val byteArray : Byte = 7
    val string    : Byte = 8
    val list      : Byte = 9
    val compound  : Byte = 10
    val intArray  : Byte = 11
  }

  /**
    * This method creates a tag that will contain all tags assigned by `:=` inside of `block` param.
    * @param block block of code with all `:=` calls that should be appended to this tag.
    * @return A new TAG_Compound tag.
    **/
  def tag(block: => Any): Tag = currentTag.withValue(Tag(Map()))({ block; currentTag.value })

  /**
    * This method converts given params to tags and adds them to one tag list.<br>
    * Also it checks for type validity (lists can contain only one type of tags by NBT specs).
    * @param stuff Values (or tags) to be converted to tags and added to the list.
    * @return A new TAG_List tag.
    **/
  def list(stuff: Any*): Tag = {
    val mapped = stuff.map({Tag.apply})
    val id = mapped.headOption.map({_.id}).getOrElse[Byte](0)
    mapped.find({_.id != id}).map({_.id}) match{
      case Some(g) => throw new TagTypeException(id, g)
      case _ => Tag(mapped)
    }
  }

  implicit class NBTIdent(str: String){

    /** Creates and appends to current tag block a new tag by analyzing type of `any`. **/
    def := (any: Any): Unit =
      if(currentTag.value != null){
        currentTag.value += str -> Tag(any)
      }else throw new IllegalStateException("Not inside `tag` block!")
  }

  /** Additional implicit constructor for NBTIdent to use Symbol's too. **/
  implicit def symbol2ident(s: Symbol): NBTIdent = new NBTIdent(s.name)

  /**
    * This reading method automatically checks for GZIP's header magic number
    * to determine whether unzip this stream or not before parsing the NBT data.
    * @return Tuple containing root tag name and the tag itself.
    **/
  def readNBT(in: InputStream): NamedTag = {
    val pbs = new PushbackInputStream(in, 2)
    val sign = new Array[Byte](2)
    val header = GZIPInputStream.GZIP_MAGIC
    pbs.read(sign)
    pbs.unread(sign)
    if(sign(0) == header.toByte && sign(1) == (header >> 8).toByte){
      readPackedNBT(pbs)
    }else readUnpackedNBT(pbs)
  }

  /** Shortcut to #readNBT(InputStream) **/
  def readNBT(in: File): NamedTag = readNBT(new FileInputStream(in))

  /** Shortcut to #readNBT(InputStream) **/
  def readNBT(in: Array[Byte]): NamedTag = readNBT(new ByteArrayInputStream(in))

  /** Shortcut to #readNBT(InputStream) **/
  def readNBT(in: String): NamedTag = readNBT(new File(in))

  /**
    * This reading method reads raw NBT data, parsing it to `Tag` instance.
    * @return Tuple containing root tag name and the tag itself.
    **/
  def readUnpackedNBT(in: InputStream): NamedTag = Tag.read(in, gzipped = false)

  /** Shortcut to #readUnpackedNBT(InputStream) **/
  def readUnpackedNBT(in: File): NamedTag = readUnpackedNBT(new FileInputStream(in))

  /** Shortcut to #readUnpackedNBT(InputStream) **/
  def readUnpackedNBT(in: Array[Byte]): NamedTag = readUnpackedNBT(new ByteArrayInputStream(in))

  /** Shortcut to #readUnpackedNBT(InputStream) **/
  def readUnpackedNBT(in: String): NamedTag = readUnpackedNBT(new File(in))

  /**
    * This reading method explicitly unzips the stream before parsing any data.
    * @return Tuple containing root tag name and the tag itself.
    **/
  def readPackedNBT(in: InputStream): NamedTag = Tag.read(in, gzipped = true)

  /** Shortcut to #readPackedNBT(InputStream) **/
  def readPackedNBT(in: File): NamedTag = readPackedNBT(new FileInputStream(in))

  /** Shortcut to #readPackedNBT(InputStream) **/
  def readPackedNBT(in: Array[Byte]): NamedTag = readPackedNBT(new ByteArrayInputStream(in))

  /** Shortcut to #readPackedNBT(InputStream) **/
  def readPackedNBT(in: String): NamedTag = readPackedNBT(new File(in))

  /**
    * Exception to be thrown from methods dedicated to only one type of tag.
    * @param expected expected tag type.
    * @param got given tag type.
    **/
  class TagTypeException(expected: Int, got: Int) extends Exception(s"Wrong tag type! Expected `${Tag.types(expected)}`, got `${Tag.types(got)}`!")

  /**
    * Exception for coersing real-types to nbt-types.
    * @param tpe unallowed type.
    * @see [[net.anti344.scalafiednbt.Tag#apply Tag#apply]]
    **/
  class NoTagForTypeException(tpe: Class[_]) extends Exception(s"No tag for type `${tpe.getName}`!")

  /**
    * Same as above, but for tag ids.
    * @param id unallowed tag id.
    * @see [[net.anti344.scalafiednbt.Tag#readPayload Tag#readPayload]]
    **/
  class NoTagForIdException(id: Int) extends Exception(s"No tag for id $id!")
}