package io.github.ivsokol.spider

/**
 * SpiderContainer is a singleton object that manages the DependencyInjection instance. It provides
 * methods to set up, refresh, and start the dependency injection, as well as methods to inject
 * dependencies by class or name and to retrieve the service registry.
 */
object SpiderContainer {
  private var di: DependencyInjection? = null

  /**
   * Sets up and starts the DependencyInjection instance.
   *
   * @param dependencyInjection the DependencyInjection instance to set up and start
   * @param automaticStart whether to automatically start the DependencyInjection instance
   * @param automaticLock whether to automatically lock the DependencyInjection instance
   * @throws IllegalStateException if the DependencyInjection instance is already set up
   */
  suspend fun setUp(
      dependencyInjection: DependencyInjection,
      automaticStart: Boolean = true,
      automaticLock: Boolean = true
  ) {
    check(di == null) { "DependencyInjection instance already set up" }
    if (automaticStart) dependencyInjection.start()
    if (automaticLock) dependencyInjection.lock()
    di = dependencyInjection
  }

  /**
   * Refreshes and starts the DependencyInjection instance.
   *
   * @param dependencyInjection the DependencyInjection instance to refresh and start
   * @param automaticStart whether to automatically start the DependencyInjection instance
   * @param automaticLock whether to automatically lock the DependencyInjection instance
   * @throws IllegalStateException if the DependencyInjection instance is not set up
   */
  suspend fun refresh(
      dependencyInjection: DependencyInjection,
      automaticStart: Boolean = true,
      automaticLock: Boolean = true
  ) {
    checkNotNull(di) { "DependencyInjection instance not set up" }
    if (automaticStart) dependencyInjection.start()
    if (automaticLock) dependencyInjection.lock()
    di = dependencyInjection
  }

  /**
   * Injects a dependency by its class type.
   *
   * @param <T> the type of the dependency
   * @return the injected dependency
   */
  suspend inline fun <reified T : Any> inject(): T = spider().resolve<T>()

  /**
   * Injects a started dependency by its class type.
   *
   * @param <T> the type of the dependency
   * @return the injected dependency
   * @throws IllegalStateException if the dependency is not started
   */
  inline fun <reified T : Any> injectStarted(): T = spider().resolveStarted<T>()

  /**
   * Injects all dependencies by their class type.
   *
   * @param <T> the type of the dependencies
   * @return the list of injected dependencies
   */
  suspend inline fun <reified T : Any> injectAll(): List<T> = spider().resolveAll<T>()

  /**
   * Injects all started dependencies by their class type.
   *
   * @param <T> the type of the dependencies
   * @return the list of injected dependencies
   * @throws IllegalStateException if any dependency is not started
   */
  inline fun <reified T : Any> injectAllStarted(): List<T> = spider().resolveAllStarted<T>()

  /**
   * Injects a dependency by its name.
   *
   * @param name the name of the dependency to inject
   * @param <T> the type of the dependency
   * @return the injected dependency
   */
  suspend inline fun <reified T : Any> inject(name: String): T = spider().resolveByName<T>(name)

  /**
   * Injects a started dependency by its name.
   *
   * @param name the name of the dependency to inject
   * @param <T> the type of the dependency
   * @return the injected dependency
   * @throws IllegalStateException if the dependency is not started
   */
  inline fun <reified T : Any> injectStarted(name: String): T =
      spider().resolveStartedByName<T>(name)

  /**
   * Retrieves the DependencyInjection instance.
   *
   * @return the DependencyInjection instance
   * @throws IllegalStateException if the DependencyInjection instance is not set
   */
  fun spider(): DependencyInjection {
    checkNotNull(di) { "DependencyInjection instance is not set" }
    return di!!
  }

  /**
   * Retrieves the service registry.
   *
   * @return a map of service metadata
   */
  fun registry(): Map<String, ServiceMetadata> = spider().registry()

  /** Shuts down the DependencyInjection instance. */
  suspend fun shutdown() = spider().destroy()
}
