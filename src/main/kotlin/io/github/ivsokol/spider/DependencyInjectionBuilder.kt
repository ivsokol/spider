package io.github.ivsokol.spider

/**
 * A builder class for constructing a Dependency Injection (DI) container.
 *
 * This class allows for the registration of dependencies and sub-modules, facilitating the creation
 * of a structured DI setup. It provides methods to register individual dependencies and to include
 * other DI modules, enabling modular and scalable application architecture.
 *
 * Usage:
 * - Use the `spiderDI` function to create a new DI container by providing a configuration block.
 * - Register dependencies using the `register` method within the block.
 * - Add sub-modules using the `module` or `modules` methods.
 *
 * Example:
 *
 * val di = spiderDI { register<MyService> { // configuration for MyService } module(otherModule) }
 */
fun spiderDI(block: DependencyInjectionBuilder.() -> Unit): DependencyInjection {
  val builder = DependencyInjectionBuilder()
  builder.apply(block)
  return builder.build()
}

@DependencyInjectionDsl
class DependencyInjectionBuilder {
  private val subModules = mutableListOf<DependencyInjection>()
  val registrations = mutableListOf<Registration<*>>()

  data class Registration<T : Any>(
      val classMeta: ClassMeta,
      val createdAtStart: Boolean,
      val isLocked: Boolean,
      val instanceType: InstanceType,
      val dependencies: List<String>,
      val priority: Int,
      val provider: suspend (di: DependencyInjection) -> T
  )

  inline fun <reified T : Any> register(block: RegisterBuilder<T>.() -> Unit) {
    val builder = RegisterBuilder<T>()
    builder.apply(block)
    registrations.add(builder.build<T>(builder.provider))
  }

  fun module(subModule: DependencyInjection) {
    subModules.add(subModule)
  }

  fun modules(vararg subModules: DependencyInjection) {
    this.subModules.addAll(subModules)
  }

  fun build(): DependencyInjection {
    val di = DependencyInjection()
    subModules.forEach { di.modules(listOf(it)) }
    registrations.forEach { reg ->
      di.register(
          reg.classMeta,
          reg.createdAtStart,
          reg.isLocked,
          reg.instanceType,
          reg.dependencies,
          reg.priority,
          reg.provider)
    }
    return di
  }
}
