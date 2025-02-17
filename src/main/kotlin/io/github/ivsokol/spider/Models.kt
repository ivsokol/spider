package io.github.ivsokol.spider

/**
 * This file contains data models and interfaces used for defining service metadata and class
 * metadata within the application. It also includes an enumeration for instance types and an
 * interface for destroyable resources.
 * - `ServiceMetadata`: Represents metadata for a service, including its name, class information,
 *   dependencies, and lifecycle management details.
 * - `ClassMeta`: Holds metadata about a class, including its name and class type.
 * - `InstanceType`: Enum defining the types of instances a service can have, such as SINGLETON or
 *   FACTORY.
 * - `Destroyable`: Interface for resources that require a destroy operation, typically for cleanup
 *   purposes.
 */
data class ServiceMetadata(
    val name: String,
    val className: String,
    val implements: Class<*>,
    val createdAtStart: Boolean,
    val isLocked: Boolean,
    var dependencyLock: Boolean,
    val instanceType: InstanceType,
    val dependencies: List<String>,
    val priority: Int,
    val provider: suspend (di: DependencyInjection) -> Any
)

data class ClassMeta(val name: String, val className: String, val clazz: Class<*>)

enum class InstanceType {
  SINGLETON,
  FACTORY
}

interface Destroyable {
  suspend fun destroy()
}
