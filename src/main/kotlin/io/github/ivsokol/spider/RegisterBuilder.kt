package io.github.ivsokol.spider

/**
 * A builder class for registering dependencies in a Dependency Injection framework.
 *
 * This class provides a DSL for configuring and registering dependencies with various options such
 * as name, interface, creation policy, locking, instance type, dependencies, priority, and
 * provider.
 *
 * @param T The type of the dependency to be registered.
 * @property name The optional name of the dependency.
 * @property intf The optional interface class that the dependency implements.
 * @property createdAtStart A flag indicating whether the dependency should be created at the start.
 * @property isLocked A flag indicating whether the registration is locked and cannot be modified.
 * @property instanceType The type of instance to be used for the dependency (e.g., SINGLETON).
 * @property dependencies A list of dependencies required by this dependency.
 * @property priority The priority of the dependency registration.
 * @property provider A suspend function that provides the instance of the dependency.
 */
@DependencyRegistrationDsl
class RegisterBuilder<T : Any> {
  var name: String? = null
  var intf: Class<*>? = null
  var createdAtStart: Boolean = false
  var isLocked: Boolean = false
  var instanceType: InstanceType = InstanceType.SINGLETON
  var dependencies: List<String> = emptyList()
  var priority: Int = 0
  var provider: suspend (di: DependencyInjection) -> T = {
    throw IllegalArgumentException("Provider not set")
  }

  inline fun <reified T : Any> build(
      noinline sameProvider: suspend (di: DependencyInjection) -> T
  ): DependencyInjectionBuilder.Registration<T> =
      DependencyInjectionBuilder.Registration<T>(
          fromClass<T>(name, intf),
          createdAtStart,
          isLocked,
          instanceType,
          dependencies,
          priority,
          sameProvider)
}
