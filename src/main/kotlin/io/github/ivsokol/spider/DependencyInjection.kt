package io.github.ivsokol.spider

import java.time.LocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DependencyInjection {
  private val instances = mutableMapOf<String, Pair<Any, LocalDateTime>>()
  private val registry = mutableMapOf<String, ServiceMetadata>()
  private var isStarted: Boolean = false
  private var isDILocked: Boolean = false
  val logger: Logger = LoggerFactory.getLogger(DependencyInjection::class.java)

  /**
   * Register a class with the dependency injection container. Invoking this method only registers a
   * class, but does not create an instance of it.
   *
   * @param classMeta ClassMeta object containing class name and class definition
   * @param createdAtStart Boolean flag indicating if the instance should be created at start.
   *   Default is false.
   * @param isLocked Boolean flag indicating if the instance should be locked. Default is false.
   * @param instanceType InstanceType enum indicating if the instance is a singleton or a factory.
   *   Default is singleton.
   * @param dependencies List of class names that this instance depends on. Default is empty list.
   * @param priority Integer value indicating the priority of the instance. Default is 0. Lower
   *   number means higher priority when resolving classes that implements same interface.
   * @param provider Lambda function that creates an instance of the class. Has dependency injection
   *   instance as a parameter.
   * @throws IllegalStateException if the class is already registered and locked
   * @throws IllegalArgumentException if the dependency is not found
   * @see ClassMeta
   * @see InstanceType
   * @see ServiceMetadata
   */
  fun <T : Any> register(
      classMeta: ClassMeta,
      createdAtStart: Boolean = false,
      isLocked: Boolean = false,
      instanceType: InstanceType = InstanceType.SINGLETON,
      dependencies: List<String>? = null,
      priority: Int = 0,
      provider: suspend (di: DependencyInjection) -> T
  ) {
    if (isDILocked) throw IllegalStateException("Cannot register new classes when locked")
    logger.debug("Registering instance {} of class {}", classMeta.name, classMeta.className)
    val key = classMeta.name
    val className = classMeta.className
    // check if class is already registered
    if (registry.containsKey(key) && (registry[key]!!.isLocked || registry[key]!!.dependencyLock)) {
      throw IllegalStateException("Class $key is locked and cannot be registered again.")
    }
    // register class
    val metadata =
        ServiceMetadata(
            name = classMeta.name,
            className = classMeta.className,
            implements = classMeta.clazz,
            createdAtStart = createdAtStart,
            isLocked = isLocked,
            dependencyLock = false,
            instanceType = instanceType,
            dependencies = dependencies ?: emptyList(),
            priority = priority,
            provider = provider)
    registry[key] = metadata

    applyLocking(key, metadata)
    logger.debug("Instance {} of class {} registered", key, className)
  }

  /**
   * Destroys all instances managed by the dependency injection container. This method will call the
   * `destroy` method on each instance that implements the `Destroyable` interface. After calling
   * `destroy` on all instances, it will clear the instances map.
   */
  suspend fun destroy() {
    logger.debug("Destroying all instances")
    instances.values
        .sortedByDescending { it.second }
        .forEach { (obj, _) ->
          if (obj is Destroyable) {
            logger.debug("Destroying instance {}", obj)
            obj.destroy()
          }
        }
    instances.clear()
    logger.debug("All instances destroyed")
  }

  /**
   * Resolve a class by name from the dependency injection container.
   *
   * @param name Name of the class to resolve
   * @return Instance of the resolved class
   * @throws IllegalStateException if the class is not found
   * @throws IllegalArgumentException if the class cannot be cast to the requested type
   */
  suspend inline fun <reified T : Any> resolveByName(name: String): T {
    logger.debug("Resolving class {} by name", T::class.java.name)
    val metadata = this.registry()[name.trim()]
    checkNotNull(metadata) { "Class not found for $name" }

    val instance =
        getInstanceFromMetadata(metadata) as? T
            ?: throw IllegalArgumentException(
                "Class cast to ${T::class.java.name} failed for $name")
    return instance
  }

  /**
   * Resolve a class by name from the dependency injection container while checking for circular
   * dependencies.
   *
   * @param name Name of the class to resolve
   * @param deps MutableSet of strings representing the circular dependency path
   * @return Instance of the class
   * @throws IllegalArgumentException if the class is not found or if a circular dependency is
   *   detected
   * @see resolveByName
   */
  suspend inline fun <reified T : Any> resolveByNameWithCheck(
      name: String,
      deps: MutableSet<String>
  ): T {
    if (deps.contains(name)) {
      throw IllegalArgumentException("Circular dependency detected for $name")
    } else {
      logger.debug("Adding $name to circular dependency set")
      deps.add(name)
    }
    return resolveByName<T>(name)
  }

  /**
   * Resolve a class by class definition from the dependency injection container.
   *
   * @return Instance of the resolved class
   * @throws IllegalStateException if the class is not found
   * @throws IllegalArgumentException if the class cannot be cast to the requested type
   */
  suspend inline fun <reified T : Any> resolve(): T {
    logger.debug("Resolving class {} by class definition", T::class.java.name)
    val definition =
        registry()
            .filter {
              it.key == T::class.java.name ||
                  it.value.name == T::class.java.name ||
                  it.value.implements.name == T::class.java.name
            }
            .values
            .minByOrNull { it.priority }

    check(definition != null) { "Class definition not found for ${T::class.java.name}" }
    val instance =
        getInstanceFromMetadata(definition) as? T
            ?: throw IllegalArgumentException("Class cast to ${T::class.java.name} failed")
    return instance
  }

  /**
   * Resolve a class by class definition from the dependency injection container while checking for
   * circular dependencies.
   *
   * @param deps MutableSet of strings representing the circular dependency path
   * @return Instance of the resolved class
   * @throws IllegalArgumentException if a circular dependency is detected
   * @see resolve
   */
  suspend inline fun <reified T : Any> resolveWithCheck(deps: MutableSet<String>): T {
    if (deps.contains(T::class.java.name)) {
      throw IllegalArgumentException("Circular dependency detected for ${T::class.java.name}")
    } else {
      logger.debug("Adding ${T::class.java.name} to circular dependency set")
      deps.add(T::class.java.name)
    }
    return resolve<T>()
  }

  /**
   * Resolve all classes by class definition (usually an interface) from the dependency injection
   * container. It will also return empty list if no classes are found.
   */
  suspend inline fun <reified T : Any> resolveAll(): List<T> {
    logger.debug("Resolving all classes {} by class or interface definition", T::class.java.name)
    val beans =
        registry()
            .filter {
              it.key == T::class.java.name ||
                  it.value.name == T::class.java.name ||
                  it.value.implements.name == T::class.java.name
            }
            .values
            .sortedBy { it.priority }

    if (beans.isEmpty()) {
      return emptyList()
    }

    return beans.map { getInstanceFromMetadata(it) as T }
  }

  /**
   * Start the dependency injection container. This method will create all instances that are marked
   * to be created at start.
   *
   * @return The DependencyInjection instance
   * @throws IllegalStateException if the container is already started
   */
  suspend fun start(): DependencyInjection {
    check(!isStarted) { "DependencyInjection already started" }
    registry.forEach { (key, metadata) ->
      if (metadata.createdAtStart) {
        logger.debug("Creating at start instance {} of class {}", key, metadata.className)
        try {
          val instance = metadata.provider.invoke(this)
          instances[key] = instance to LocalDateTime.now()
        } catch (e: Exception) {
          logger.error("Error creating instance {} of class {}", key, metadata.className, e)
          error(
              "Error creating instance $key of class ${metadata.className} with error: ${e.message}")
        }
      }
    }
    isStarted = true
    return this
  }

  /**
   * Lock the dependency injection container. This method will prevent any new classes from being
   * registered.
   */
  fun lock() {
    isDILocked = true
  }

  /** Return class registry as a read only map */
  fun registry(): Map<String, ServiceMetadata> = registry.toMap()

  /**
   * Adds submodules to the dependency injection container. This method will add all classes from
   * the submodules to the registry. If there is a class with the same name in the registry, it will
   * throw an exception if the class is locked. If class in the registry is not locked, it will be
   * overridden. Created instances in submodules are ignored and created again.
   */
  fun modules(subModules: List<DependencyInjection>) {
    if (isDILocked) throw IllegalStateException("Cannot register modules when locked")
    subModules.forEachIndexed { idx, subModule ->
      logger.debug("Loading module #{}", idx)
      // validate if locked
      val lockedKeys = registry.filter { it.value.isLocked || it.value.dependencyLock }.keys
      if (lockedKeys.isNotEmpty() && subModule.registry.keys.any { it in lockedKeys }) {
        subModule.registry().keys.forEach { key ->
          if (key in lockedKeys) {
            throw IllegalStateException("Class $key is locked and cannot be overridden.")
          }
        }
      }
      subModule.registry().forEach { (key, metadata) ->
        registry[key] = metadata
        applyLocking(key, metadata)
      }
    }
  }

  /**
   * Apply locking to the dependency injection container. This method will lock all dependencies
   *
   * @param key The key of the class to lock
   * @param metadata The metadata of the class to lock
   */
  private fun applyLocking(key: String, metadata: ServiceMetadata) {
    // lock dependencies if created at start
    if (metadata.createdAtStart) {
      logger.debug("Registering at start instance {} of class {}", key, metadata.className)
      if (metadata.dependencies.isNotEmpty() && metadata.instanceType == InstanceType.SINGLETON) {
        logger.debug(
            "Implicitly locking dependencies for instance {} of class {}", key, metadata.className)
        lockDependencies(metadata.dependencies)
      }
    }
    // lock dependencies if locked and not created at start
    if (metadata.isLocked &&
        metadata.instanceType == InstanceType.SINGLETON &&
        !metadata.createdAtStart &&
        metadata.dependencies.isNotEmpty()) {
      logger.debug(
          "Explicitly locking dependencies for instance {} of class {}", key, metadata.className)
      lockDependencies(metadata.dependencies)
    }
  }

  /** Recursive function to lock dependencies. Only singletons can be locked. */
  private fun lockDependencies(dependencies: List<String>) {
    dependencies.forEach { dep ->
      val deps =
          registry.filter { r ->
            r.key == dep || r.value.className == dep || r.value.implements.name == dep
          }
      if (deps.isNotEmpty()) {
        deps.forEach { (k, v) ->
          check(v.instanceType == InstanceType.SINGLETON) { "Dependency $dep must be a singleton" }
          v.dependencyLock = true
          if (v.dependencies.isNotEmpty()) lockDependencies(v.dependencies)
          registry[k] = v
        }
      } else {
        throw IllegalArgumentException("Dependency $dep not found")
      }
    }
  }

  /**
   * Instantiates a class from the metadata. If the class is a factory, it will call the provider
   * function to create an instance every time. If the class is a singleton, it will return the
   * existing instance if it exists, otherwise it will create a new instance and store it in the
   * instances map.
   *
   * @param metadata The metadata of the class to instantiate
   * @return The instance of the class
   */
  suspend fun getInstanceFromMetadata(metadata: ServiceMetadata): Any {
    // if factory, always create new instance
    if (metadata.instanceType == InstanceType.FACTORY) {
      logger.debug("Creating factory instance {} of class {}", metadata.name, metadata.className)
      return try {
        metadata.provider.invoke(this)
      } catch (e: Exception) {
        logger.error("Error creating instance {} of class {}", metadata.name, metadata.className, e)
        error(
            "Error creating instance ${metadata.name} of class ${metadata.className} with error: ${e.message}")
      }
    }

    // if already created, return existing instance
    if (instances.containsKey(metadata.name)) {
      logger.debug("Returning existing instance {} of class {}", metadata.name, metadata.className)
      return instances[metadata.name]!!.first
    }

    logger.debug("Creating lazily instance {} of class {}", metadata.name, metadata.className)
    val instance =
        try {
          metadata.provider.invoke(this)
        } catch (e: Exception) {
          logger.error(
              "Error creating instance {} of class {}", metadata.name, metadata.className, e)
          error(
              "Error creating instance ${metadata.name} of class ${metadata.className} with error: ${e.message}")
        }
    instances[metadata.name] = instance to LocalDateTime.now()
    return instance
  }
}
