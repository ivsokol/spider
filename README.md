# Spider - Dependency Injection Framework

## Overview

The Spider Dependency Injection Framework is a lightweight and flexible dependency injection container for Kotlin. It allows you to register, resolve, and manage dependencies in a structured and efficient manner.

## Features

- **Dependency Injection**: Register and resolve dependencies by class or name.
- **Object container**: Register and resolve dependencies by class or name from central object container.
- **Singleton and Factory Instances**: Support for singleton and factory instance types.
- **Dependency Locking**: Lock dependencies to prevent re-registration.
- **Module Support**: Add and manage submodules within the container.
- **Lifecycle Management**: Start, refresh, and destroy instances with lifecycle hooks.
- **DSL Support**: Use Kotlin DSL to configure and register dependencies.
- **Circular Dependency Detection**: Detect and handle circular dependencies.
- **Type Safety**: Strongly typed dependency resolution.
- **Test Support**: Includes tests to ensure the functionality of the container.

## Usage

### Setting Up the Dependency Injection Container

To set up the dependency injection container, use the `spiderDI` function to configure and register your dependencies.

```kotlin
val di = spiderDI {
    register<Foo> {
        instanceType = InstanceType.SINGLETON
        provider = { Foo("example") }
    }
}
```

### Registering Dependencies

You can register dependencies using the `register` function within the `spiderDI` block. Provider lambda function is minimally required.

```kotlin
register<Foo> {
    provider = { Foo("example") }
}
```

You can also register dependencies with following options:
- name: Provide a name for the dependency. Default is the class name.
- intf: Specify the interface class that the dependency implements. Default is the class itself.
- createdAtStart: Set to true to create the dependency at the start. Default is false.
- isLocked: Set to true to lock the registration and prevent further modifications. Default is false.
- instanceType: Specify the instance type (SINGLETON or FACTORY). Default is SINGLETON.
- dependencies: Specify a list of dependencies by name required by this dependency. 
These dependencies must be singletons and will be implicitly locked if listed here. 
They also must be created before this dependency.
- priority: Specify the priority of the dependency registration. Default is 0.
- **provider**: Provide the instance or factory creation function. Mandatory for all registrations.

```kotlin
register<Foo> {
    name = "foo"
    intf = Foo::class.java
    createdAtStart = true
    isLocked = true
    instanceType = InstanceType.SINGLETON
    dependencies = listOf("bar")
    priority = 1
    provider = { Foo("example") }    
}
```

### Resolving Dependencies

Resolve dependencies by class or name using the `resolve` or `resolveByName` functions.

```kotlin
// by class or interface
val fooInstance = di.resolve(Foo::class.java)
// or 
val fooInstance:Foo = di.resolve()
// or 
val fooInstance = di.resolve<Foo>()
// or 
val fooInstance = di.resolve() as Foo
// or by name
val fooInstance = di.resolveByName("foo")
// or by class name
val fooInstance = di.resolveByName(Foo::class.java.name)
// or fetch all instances of an interface
val allFooInstances = di.resolveAll<IFoo>()
```

### Resolving dependencies with circular dependency check

Resolve dependencies with circular dependency check using the `resolveWithCheck` or `resolveByNameWithCheck` function.

```kotlin
data class A(
    val b: B
)

data class B(
    val c: C
)

data class C(
    val a: A
)
// dependency set must be created before registration
val deps = mutableSetOf<String>()
val testDI = spiderDI {
    register<A> {
        provider = { di -> A(di.resolveWithCheck(deps)) }
    }
    register<B> {
        provider = { di -> B(di.resolveWithCheck(deps)) }
    }
    register<C> {
        provider = { di -> C(di.resolveWithCheck(deps)) }
    }
}
shouldThrow<IllegalStateException> {
    runTest {
        testDI.resolveWithCheck<A>(deps)
    }
}.message shouldContain "Circular dependency detected"
```

### Locking the Container

Lock the container to prevent further registrations.

```kotlin
di.lock()
```

### Managing Lifecycle

Start, refresh, and destroy the container using the provided methods.

```kotlin
// starts instances marked as createdAtStart. By default instances are created lazily, during the first resolve.
di.start()

// refresh the container with new DI instance.
di.refresh(newDI)

// checks registry for all instances that implements `Destroyable` and calls `destroy` on them.
di.destroy()
```

### Using Modules

Add submodules to the container for better organization.

```kotlin
val fooDI = spiderDI {
    register<Foo> {
        instanceType = InstanceType.FACTORY
        provider = { Foo(Random.nextInt().toString()) }
    }
}

val repoDI = spiderDI {
    module(fooDI)
    register<FooRepo> {
        intf = IFooRepo::class.java
        provider = { di -> FooRepo(di.resolve()) }
    }
}
val serviceDI =
    spiderDI {
        module(repoDI)
        register<FooService> {
            intf = IFooService::class.java
            createdAtStart = true
            dependencies = listOf(IFooRepo::class.java.name)
            provider = { di ->
                FooService(
                    foo = di.resolve(),
                    fooRepo = di.resolve(),
                    value = "value"
                )
            }
        }
    }
        .start()

val fooService = serviceDI.resolve() as IFooService
val fooRepo: IFooRepo = serviceDI.resolve()
```
### `SpiderContainer`

Singleton object for managing the `DependencyInjection` instance on application level. It supports following methods:

- `setUp(dependencyInjection: DependencyInjection, automaticStart: Boolean = true, automaticLock: Boolean = true)`: Sets up the `DependencyInjection` instance and starts it if `automaticStart` is true. If `automaticLock` is true, it locks the container after setup.
- `refresh(dependencyInjection: DependencyInjection, automaticStart: Boolean = true, automaticLock: Boolean = true)`: Refreshes the `DependencyInjection` instance and starts it if `automaticStart` is true. If `automaticLock` is true, it locks the container after refresh.
- `inject(): T` - Wrapper around `DependencyInjection.resolve()` method
- `inject(name: String): T` - Wrapper around `DependencyInjection.resolveByName(name)` method
- `injectAll(): T` - Wrapper around `DependencyInjection.resolveAll()` method
- `shutdown()` - Wrapper around `DependencyInjection.destroy()` method
- `registry(): Map<String, ServiceMetadata>` - Returns the dependency injection registry.
- `spider(): DependencyInjection` - Returns the `DependencyInjection` instance.

## Examples

More examples can be found in the [test](src/test/kotlin/io/github/ivsokol/spider) folder.

## License

This project is available as open source under the terms of [Apache 2.0 License](https://opensource.org/licenses/Apache-2.0).