package io.github.ivsokol.spider

inline fun <reified T : Any> fromClass(
    name: String? = null,
    intf: Class<*>? = null,
): ClassMeta {
  val className = T::class.java.name
  val key = name?.trim() ?: T::class.java.name
  val clazz = intf ?: T::class.java

  if (intf != null) {
    // check if class implements interface
    if (intf.isAssignableFrom(T::class.java).not()) {
      throw IllegalArgumentException(
          "Class ${T::class.java.name} does not implement interface ${intf.name}")
    }
  }
  return ClassMeta(key, className, clazz)
}
