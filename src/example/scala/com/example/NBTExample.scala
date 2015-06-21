package com.example

object NBTExample extends App{

  import net.anti344.scalafiednbt._
  import renderers.code

  val bigtest = readNBT(s"/home/${sys.props("user.name")}/nbt_test/bigtest.nbt") //Linux path, but you'll change it anyway

  println(bigtest._2.render(bigtest._1))

  val test = tag{
    "name" := "Bananrama"
  }

  test.list = list(1, 2, 3)
  test.list += 4 += 5
  test.dynamicallyTypedList = list()
  test.dynamicallyTypedList += "test"
  test.dynamicallyTypedList += "test" += "14"

  def canCauseTTE(code: => Any) =
    try code catch{ case e: TagTypeException => println(s"TagTypeExceprion was thrown: ${e.getMessage}") }

  canCauseTTE{ test.dynamicallyTypedList += 123 /* this code will throw an TagTypeException */ }
  canCauseTTE{ test.list += "string" /* this one too */ }

  test.tag = tag{
    "innerList" := list(14)
  }

  test.tag.innerList += -7 += 1537

  println(test.tag.innerList(0).to[Int].get) // to[T] returns an Option[T], using .get is bad, you should use pattern matching

  test.byte = 123.toByte

  // `-symbols are not included, thanks to Scala
  test.`name w/ spaces and symbols` = 156153112854L

  test.anotherTag = tag{}

  test.anotherTag += "name with spaces too" -> 1254.54656484
  test.anotherTag += 'symbol -> "test"

  println(test.anotherTag.`name with spaces too`.to[Double])

  println(test)
}