# Named Binary Tag specification

NBT (Named Binary Tag) is a tag based binary format designed to carry large amounts of binary data with smaller amounts of additional data.

An NBT file consists of a single optionally GZIP'ped Named Tag of type `TAG_Compound`.

A Named Tag has the following format:
- byte `tagType`
- TAG_String `name`
- `payload`

The `tagType` is a single byte defining the contents of the payload of the tag.

The `name` is a descriptive name, and can be anything (eg "cat", "banana", "Hello World!"). It has nothing to do with the `tagType`.

The purpose for this name is to name tags so parsing is easier and can be made to only look for certain recognized tag names.

Exception: If `tagType` is `TAG_End`, the name is skipped and assumed to be "".

The `payload` varies by `tagType`.

Note that ONLY Named Tags carry the `name` and `tagType`. Explicitly identified Tags (such as `TAG_String` above) only contain the payload.

The tag types and respective payloads are:

 Type | Name           | Payload                                                                                                              | Note 
 ---- | -------------- | -------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 0    | TAG_End        | None                                                                                                                 | This tag is used to mark the end of a list. <br> Basically in bytes it is represented as simple one-byte 0.
 1    | TAG_Byte       | A single signed byte (8 bits)                                                                                        |
 2    | TAG_Short      | A signed short (16 bits, big endian)                                                                                 |
 3    | TAG_Int        | A signed integer (32 bits, big endian)                                                                               |
 4    | TAG_Long       | A signed long (64 bits, big endian)                                                                                  |
 5    | TAG_Float      | A floating point value (32 bits, big endian, IEEE 754-2008, binary32)                                                |
 6    | TAG_Double     | A floating point value (64 bits, big endian, IEEE 754-2008, binary64)                                                |
 7    | TAG_Byte_Array | TAG_Int `length` <br> Array of bytes of unspecified format with length `length`.                                     |
 8    | TAG_String     | TAG_Short `length` <br> Array of bytes defining a string in UTF-8 format with length `length`.                       |
 9    | TAG_List       | TAG_Byte `tagId` <br> TAG_Int `length` <br> A sequential list of tag payloads of type `typeId` with length `length`. | All tags share the same type.
 10   | TAG_Compound   | A sequential list of Named Tags. This list keeps going until a TAG_End is found. <br> TAG_End end.                   | Nested TAG_Compound's have their own TAG_End's. <br> The names of the named tags have to be unique within each TAG_Compound. <br> The order of the tags is not guaranteed.
 11   | TAG_Int_Array  | TAG_Int `length` <br> Array of TAG_Int's with length `length`.                                                       | This type was introduced later, thus it has this weird type id.


Decoding example:
(Use [test.nbt](https://raw.githubusercontent.com/anti344/ScalafiedNBT/master/specs/test.nbt) to test your implementation)

First we start by reading a Named Tag.
After unzipping the stream, the first byte is a 10. That means the tag is a TAG_Compound (as expected by the specification).

The next two bytes are 0 and 11, meaning the name string consists of 11 UTF-8 characters. In this case, they happen to be "hello world".
That means our root tag is named "hello world". We can now move on to the payload.

From the specification, we see that TAG_Compound consists of a series of Named Tags, so we read another byte to find the tagType.
It happens to be an 8. The name is 4 letters long, and happens to be "name". Type 8 is TAG_String, meaning we read another two bytes to get the length,
then read that many bytes to get the contents. In this case, it's "Bananrama".

So now we know the TAG_Compound contains a TAG_String named "name" with the content "Bananrama"

We move on to reading the next Named Tag, and get a 0. This is TAG_End, which always has an implied name of "". That means that the list of entries
in the TAG_Compound is over, and indeed all of the NBT file.

So we ended up with this:

```
TAG_Compound("hello world"): 1 entries
{
   TAG_String("name"): Bananrama
}
```

For a slightly longer test, download [bigtest.nbt](https://raw.githubusercontent.com/anti344/ScalafiedNBT/master/specs/bigtest.nbt).
You should end up with this:

```
TAG_Compound("Level"): 11 entries
{
   TAG_Short("shortTest"): 32767
   TAG_Long("longTest"): 9223372036854775807
   TAG_Float("floatTest"): 0.49823147
   TAG_String("stringTest"): HELLO WORLD THIS IS A TEST STRING ÅÄÖ!
   TAG_Int("intTest"): 2147483647
   TAG_Compound("nested compound test"): 2 entries
   {
      TAG_Compound("ham"): 2 entries
      {
         TAG_String("name"): Hampus
         TAG_Float("value"): 0.75
      }
      TAG_Compound("egg"): 2 entries
      {
         TAG_String("name"): Eggbert
         TAG_Float("value"): 0.5
      }
   }
   TAG_List("listTest (long)"): 5 entries of type TAG_Long
   {
      TAG_Long: 11
      TAG_Long: 12
      TAG_Long: 13
      TAG_Long: 14
      TAG_Long: 15
   }
   TAG_Byte("byteTest"): 127
   TAG_List("listTest (compound)"): 2 entries of type TAG_Compound
   {
      TAG_Compound: 2 entries
      {
         TAG_String("name"): Compound tag #0
         TAG_Long("created-on"): 1264099775885
      }
      TAG_Compound: 2 entries
      {
         TAG_String("name"): Compound tag #1
         TAG_Long("created-on"): 1264099775885
      }
   }
   TAG_Byte_Array("byteArrayTest (the first 1000 values of (n*n*255+n*7)%100, starting with n=0 (0, 62, 34, 16, 8, ...))"): [1000 bytes]
   TAG_Double("doubleTest"): 0.4931287132182315
}
```