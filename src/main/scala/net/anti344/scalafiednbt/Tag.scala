package net.anti344.scalafiednbt

import java.io._
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

import net.anti344.scalafiednbt.Tag.Dummy
import net.anti344.scalafiednbt.renderers.NBTRenderer

import scala.collection.mutable.{Buffer => MBuffer, LinkedHashMap => MMap}
import scala.collection.{GenMap, GenTraversableOnce}
import scala.language.dynamics
import scala.reflect.{ClassTag, classTag}

/**
  * Licenced under LGPLv3
  * @author anti344
  **/
object Tag{

  val types = Array("End", "Byte", "Short", "Int", "Long", "Float", "Double", "Byte_Array", "String", "List", "Compound", "Int_Array").map({"TAG_" + _})

  /**
    * This function analyses the type of `any` and converts it to appropriate Tag instance if possible.<br>
    * Warning - it is recursive, so you may cause infinite loops if your objects are constructed poorly.
    * <p>
    * Acceptable types are:<br>
    * Tag, Byte, Short, Int, Long, Float, Double, Array[Byte], String, Array[Int]<br>
    * Any inheritor of GenMap will be coersed to TAG_Compound and any inheritor of GenTraversableOnce,
    * which is not a GenMap will be coersed to TAG_List
    * </p>
    *
    * @param any value to be coerced to Tag.
    * @throws NoTagForTypeException if given value cannot be coerced.
    **/
  def apply(any: Any): Tag = any match{
    case tag: Tag => tag
    case b: Boolean => new Tag(1, (if(b) 1 else 0) : Byte)
    case b: Byte => new Tag(1, b)
    case s: Short => new Tag(2, s)
    case i: Int => new Tag(3, i)
    case l: Long => new Tag(4, l)
    case f: Float => new Tag(5, f)
    case d: Double => new Tag(6, d)
    case ba: Array[Byte] => new Tag(7, ba)
    case s: String => new Tag(8, s)
    case map: GenMap[String @unchecked, Any @unchecked] => new Tag(10, MMap(map.map({case (k, v) => (k, apply(v))}).toList:_*))
    case coll: GenTraversableOnce[Any @unchecked] => new Tag(9, coll.toBuffer.map({apply}))
    case ia: Array[Int] => new Tag(11, ia)
    case _ => throw new NoTagForTypeException(any.getClass)
  }

  /**
    * Main reading method.
    * @return A tuple containing root name and root tag.
    **/
  def read(in: InputStream, gzipped: Boolean): NamedTag = {
    val din = new DataInputStream(if(gzipped)new GZIPInputStream(in) else in)
    val id = din.readByte
    NamedTag(new String(Array.fill(din.readShort)({din.readByte}), "UTF-8"), readPayload(din, id))
  }

  /**
    * Reads raw payload to Tag instance for gien tag id from given stream.
    * @param in input stream.
    * @param id tag id.
    * @return New Tag instance with given id and data from the stream.
    * @throws NoTagForIdException if given id is not in range [1, 11].
    **/
  def readPayload(in: InputStream, id: Byte): Tag = {
    val din = new DataInputStream(in)
    if(1 to 11 contains id){
      new Tag(id, id match{
        case 1 => din.readByte
        case 2 => din.readShort
        case 3 => din.readInt
        case 4 => din.readLong
        case 5 => din.readFloat
        case 6 => din.readDouble
        case 7 => Array.fill(din.readInt)({din.readByte})
        case 8 => new String(Array.fill(din.readShort)({din.readByte}), "UTF-8")
        case 9 =>
          val id = din.readByte
          val size = din.readInt
          MBuffer.fill(size)({readPayload(din, id)})
        case 10 =>
          val map = MMap[String, Tag]()
          var id = din.readByte
          while(id != 0){
            map += new String(Array.fill(din.readShort)({din.readByte})) -> readPayload(din, id)
            id = din.readByte
          }
          map
        case 11 => Array.fill(din.readInt)(din.readInt)
      })
    }else throw new NoTagForIdException(id)
  }

  /**
    * Dummy tag is required for long not-null chains, so you simply get `None` from `to` method,
    * or `true` from `isDummy` and occured (if so) exception from `error`.<br>
    * BE WARNED: Only getters may return this thing, any modifying methods may throw exceptions!
    **/
  final private[scalafiednbt] class Dummy extends Tag(-1, null){
    override val isDummy = true
    override def apply(pos: Int): Tag = this
    override def to[T: ClassTag]: Option[T] = None
    override val toString: String = "TAG_Dummy"
  }
}

/**
  * A class to be used instead of tuple in Tag#read method.<br>
  * Used only for nice `render` method and nice getters for root name and the tag itself.
  **/
final case class NamedTag(name: String, tag: Tag){

  /** Render shortcut with name from this NamedTag **/
  def render(implicit renderer: NBTRenderer): String = tag.render(name)

  override def toString: String = render(renderers.notch)
}

sealed case class Tag private(id: Byte, private[scalafiednbt] val payload: Any) extends Dynamic{

  /** If this returns `true` then something went wrong. **/
  val isDummy = false

  /** If this is a compound, then dynamically gets tag with name `name`. **/
  def selectDynamic(name: String): Tag = get(name).getOrElse(new Dummy)

  /**
    * If this is a compound and tag `name` within it is a list,
    * then dynamically gets tag at pos `pos` from list `name` within it.
    **/
  def applyDynamic(name: String)(pos: Int): Tag = get(name).getOrElse(new Dummy).apply(pos)

  /** If this is a compound, then dynamically sets given value as tag with name `name`. **/
  def updateDynamic(name: String)(value: Any) =
    to[MMap[String, Tag]] match{
      case Some(tags) => tags(name) = Tag(value)
      case _ => throw new TagTypeException(Tags.compound, id)
    }

  /** If this is a list, then sets given value as tag at pos `pos`. **/
  def update(pos: Int, value: Any) =
    to[MBuffer[Tag]] match{
      case Some(tags) => tags(pos) = Tag(value)
      case _ => throw new TagTypeException(Tags.list, id)
    }

  /** If this is a list, then gets tag at pos `pos`. **/
  def apply(pos: Int): Tag =
    to[MBuffer[Tag]] match{
      case Some(tags) if tags.isDefinedAt(pos) => tags(pos)
      case _ => new Tag.Dummy
    }

  /**
    * If this is a list, then tryes to coerce given param to Tag and append it to the end.<br>
    * Does the same thing if this is a compound and given param is tuple of String and Any.
    * @param value value to be coerced and appended.
    * @return Itself.
    **/
  def += (value: Any): Tag =
    (value match{
      case (sym: Symbol, v) => (sym.name, v) //symbol support
      case e => e
    }) match{
      case (s: String, v) =>
        to[MMap[String, Tag]] match{
          case Some(tags) =>
            tags += s -> Tag(v)
            this
          case _ => throw new TagTypeException(Tags.compound, id)
        }
      case v =>
        to[MBuffer[Tag]] match{
          case Some(tags) =>
            val t = Tag(v)
            if(tags.headOption.map({_.id == t.id}).getOrElse(true)){
              tags += Tag(v)
              this
            }else throw new TagTypeException(tags.head.id, t.id)
          case _ => throw new TagTypeException(Tags.list, id)
        }
    }

  /**
    * If this is a list tag then tries to remove given value from it.<br>
    * Tag is case-class, so equivalence check works properly, and you could
    * use newly created tags to do the removal.
    * @param value value or tag to be removed
    * @return Itself.
    **/
  def -= (value: Any): Tag =
    (to[MBuffer[Tag]], Tag(value)) match{
      case (Some(tags), tag) =>
        tags -= tag
        this
      case _ => throw new TagTypeException(Tags.list, id)
    }

  /**
    * If this is a compound tag then checks tag with given name withing it for existence.
    * @param name name of the tag to check.
    * @return true if this is a TAG_Compound and tag with given name is present in it.
    **/
  def has(name: String): Boolean =
    to[MMap[String, Tag]] match{
      case Some(tags) => tags.contains(name)
      case _ => throw new TagTypeException(Tags.compound, id)
    }

  /**
    * Same as above, but also checks tag id.
    * @param name name of the tag to check.
    * @param id id of the tag to check.
    * @return true if this is a TAG_Compound and tag with given name is present in it and has required id.
    **/
  def has(name: String, id: Int): Boolean =
    to[MMap[String, Tag]] match{
      case Some(tags) => tags.contains(name) && tags(name).id == id
      case _ => throw new TagTypeException(Tags.compound, id)
    }

  /**
    * Explicit getter for compound tags. Instead of failing or returning dummy it uses Option[Tag]
    * @param name name of the tag to get.
    * @return optional tag from this compound (if this is a compound).
    **/
  def get(name: String): Option[Tag] =
    to[MMap[String, Tag]] match{
      case Some(tags) if tags.contains(name) => Some(tags(name))
      case _ => None
    }

  /**
    * If this is a compound tag then tries to remove a tag with given name from it.
    * @param name name of the tag to remove.
    * @return Some(removed tag) if it was present, None otherwise.
    **/
  def remove(name: String): Option[Tag] =
    to[MMap[String, Tag]] match{
      case Some(tags) => remove(name)
      case _ => throw new TagTypeException(Tags.compound, id)
    }

  /**
    * If `payload` is of type `T` then this method returns Some(it casted), None otherwise.
    **/
  def to[T : ClassTag]: Option[T] = {
    val name = classTag[T].runtimeClass.getName
    if(name == "boolean" || name == "scala.Boolean"){ //hax
      payload match{
        case t: Byte => Some((t == 1).asInstanceOf[T])
        case _ => None
      }
    }else payload match{
      case t: T => Some(t)
      case _ => None
    }
  }

  /**
    * Writes this tag to the given stream.
    * @param out stream to write data to.
    * @param name root name of the tag.
    * @param gzip will the data be GZIP'ped or not.
    **/
  def writeToStream(out: OutputStream, name: String = "", gzip: Boolean = false) = {
    val d = new DataOutputStream(if(gzip)new GZIPOutputStream(out, true) else out)
    write(d, name)
    d.flush()
  }

  /** File-shortcut for writeToStream **/
  def writeToFile(file: File, name: String = "", gzip: Boolean = false): Unit = writeToStream(new FileOutputStream(file), name, gzip)

  /** Filename-shortcut for writeToFile **/
  def writeToFilename(fileName: String, name: String = "", gzip: Boolean = false): Unit = writeToFile(new File(fileName), name, gzip)

  /** So the stream wouldn't be wrapped by GZIPOutputStream more then once **/
  private def write(out: DataOutputStream, name: String) = {
    out.writeByte(id)
    out.writeShort(name.length)
    out.write(name.getBytes("UTF-8"))
    writePayload(out)
  }

  /**
    * Writes only payload of this tag to the stream. Meant for internal use, but left public for whatever you might need it.
    * @param out stream to write data to.
    **/
  def writePayload(out: DataOutputStream): Unit =
    payload match{
      case b: Byte => out.writeByte(b)
      case s: Short => out.writeShort(s)
      case i: Int => out.writeInt(i)
      case l: Long => out.writeLong(l)
      case f: Float => out.writeFloat(f)
      case d: Double => out.writeDouble(d)
      case a: Array[Byte] =>
        out.writeInt(a.length)
        out.write(a)
      case s: String =>
        out.writeShort(s.length)
        out.write(s.getBytes("UTF-8"))
      case l: MBuffer[Tag @unchecked] =>
        out.writeByte(l.headOption.map({_.id}).getOrElse[Byte](0))
        out.writeInt(l.size)
        l.foreach({_.writePayload(out)})
      case m: MMap[String @unchecked, Tag @unchecked] =>
        m.foreach({case (k, v) => v.write(out, k)})
        out.writeByte(0)
      case a: Array[Int] =>
        out.writeInt(a.length)
        a.foreach({out.writeInt})
    }

  /**
    * Util method to automatically convert this tag to array of bytes.
    * @param name optional root name of this tag.
    * @param gzip is this thing should be GZIP'ped.
    **/
  def toBytes(name: String = "", gzip: Boolean = false): Array[Byte] = {
    val out = new ByteArrayOutputStream
    writeToStream(out, name, gzip)
    out.toByteArray
  }

  /**
    * Converts this tag to a string representation of it.<br>
    * By default, there is four renderers available to be
    * imported from renderers object - notch, json, mojangson and code.
    * @param rootName optional root name.
    * @param renderer implicit NBT renderer.
    * @return tag, rendered as string.
    **/
  def render(rootName: String = "")(implicit renderer: NBTRenderer): String = renderer.render(this, rootName)

  override def toString: String = render()(renderers.notch)
}