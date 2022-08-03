# ScalafiedNBT

ScalafiedNBT is small, simple, intuitive and easy to use NBT library for Scala programming language.
  - Defines a small [DSL](http://wikipedia.org/wiki/Domain-specific_language) for simple and cool tag creation.
  - Uses [`scala.Dynamic`](http://www.scala-lang.org/api/current/index.html#scala.Dynamic) and syntax sugar for tag access.
  - Tags wrap around values functional-like, so long non-null getter chains are possible.
  - Tags are dynamically typed - gives much flexibility and controlled by runtime type validation.
  - Tags by default can be rendered to json, mojangson, code(yes, valid Scala-code which uses this DSL) and Notch-like structure from NBT specs.

NBT (Named Binary Tag) is a tag based binary format designed to carry large amounts of binary data with smaller amounts of additional data.

An NBT file consists of a single optionally GZIP'ped named tag of type TAG_Compound (more info about NBT [here](https://github.com/necauqua/ScalafiedNBT/blob/master/specs/README.markdown)).

### Licence
ScalafiedNBT is licenced under LGPLv3.

### Examples
Code examples can be found [here](https://github.com/necauqua/ScalafiedNBT/blob/master/src/example/scala/com/example).

### Contribution
Right now this library is considered complete, so contribution is not really needed. If any bugs found, welcome to the [issue page](https://github.com/necauqua/ScalafiedNBT/issues).

If you want to mess around with this code, then just clone this repo and import Gradle project into your IDE.

### Download
Latest versions can be found on [releases page](https://github.com/necauqua/ScalafiedNBT/releases).