package io.github.ivsokol.spider

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlin.random.Random
import kotlinx.coroutines.test.runTest

object DummyObject {
  var flag = false

  fun setFlag() {
    flag = true
  }

  fun resetFlag() {
    flag = false
  }
}

data class Foo(val bar: String)

interface IFooRepo {
  fun getFoo(): Foo
}

class FooRepo(private val foo: Foo) : IFooRepo {
  override fun getFoo(): Foo = foo
}

class DestroyableFooRepo(private val foo: Foo) : IFooRepo, Destroyable {
  override fun getFoo(): Foo = foo

  override suspend fun destroy() {
    DummyObject.setFlag()
  }
}

interface IFooService {
  fun getFooRepo(): IFooRepo

  fun getFoo(): Foo
}

class FooService(private val foo: Foo, private val fooRepo: IFooRepo, val value: String) :
    IFooService, IFooRepo {
  override fun getFooRepo(): IFooRepo = fooRepo

  override fun getFoo(): Foo = foo
}

data class A(val b: B)

data class B(val c: C)

data class C(val a: A)

class DependencyInjectionTest :
    FunSpec({
      context("registration") {
        test("testRegisterSingleton") {
          val testDI =
              spiderDI {
                    register<Foo> {
                      instanceType = InstanceType.SINGLETON
                      provider = { Foo(Random.nextInt().toString()) }
                    }
                  }
                  .start()
          val instance = testDI.resolve<Foo>()
          val instance2: Foo = testDI.resolve()
          instance.shouldNotBeNull()
          instance2.shouldNotBeNull()
          instance.bar shouldBe instance2.bar
        }

        test("test register factory") {
          val testDI =
              spiderDI {
                    register<Foo> {
                      instanceType = InstanceType.FACTORY
                      provider = { Foo(Random.nextInt().toString()) }
                    }
                  }
                  .start()
          val instance1 = testDI.resolve<Foo>()
          val instance2 = testDI.resolve() as Foo
          instance1.shouldNotBeNull()
          instance2.shouldNotBeNull()
          instance1.bar shouldNotBe instance2.bar
        }

        test("locked service cannot be registered again") {
          shouldThrow<IllegalStateException> {
                spiderDI {
                  register<Foo> {
                    isLocked = true
                    provider = { Foo(Random.nextInt().toString()) }
                  }
                  register<Foo> {
                    isLocked = true
                    provider = { Foo(Random.nextInt().toString()) }
                  }
                }
              }
              .message shouldBe
              "Class io.github.ivsokol.spider.Foo is locked and cannot be registered again."
        }

        test("unlocked services can be registered again") {
          val testDI =
              spiderDI {
                    register<Foo> {
                      isLocked = false
                      provider = { Foo("1") }
                    }
                    register<Foo> {
                      isLocked = false
                      provider = { Foo("2") }
                    }
                  }
                  .start()
          val instance1 = testDI.resolve<Foo>()
          instance1.shouldNotBeNull()
          instance1.bar shouldBe "2"
        }

        test("start should initialize createdAtStart instances") {
          val testDI =
              spiderDI {
                    register<Foo> {
                      createdAtStart = true
                      provider = { Foo("start") }
                    }
                  }
                  .start()

          val instance = testDI.resolve() as Foo
          instance.bar shouldBe "start"
        }

        test("should throw if it doesn't implement interface") {
          shouldThrow<IllegalArgumentException> {
                spiderDI {
                      register<Foo> {
                        createdAtStart = true
                        intf = IFooRepo::class.java
                        provider = { Foo("1") }
                      }
                    }
                    .start()
              }
              .message shouldContain
              "Class io.github.ivsokol.spider.Foo does not implement interface io.github.ivsokol.spider.IFooRepo"
        }
      }

      context("resolution") {
        test("resolve by class") {
          val testDI =
              spiderDI {
                    register<Foo> {
                      instanceType = InstanceType.FACTORY
                      provider = { Foo(Random.nextInt().toString()) }
                    }
                  }
                  .start()
          val instance = testDI.resolve<Foo>()
          instance.shouldNotBeNull()
        }

        test("resolve all by class") {
          val testDI =
              spiderDI {
                    register<Foo> {
                      instanceType = InstanceType.SINGLETON
                      provider = { Foo("1") }
                      name = "Foo1"
                      priority = 3
                    }
                    register<Foo> {
                      instanceType = InstanceType.SINGLETON
                      provider = { Foo("2") }
                      name = "Foo2"
                      priority = 2
                    }
                  }
                  .start()
          val instances = testDI.resolveAll<Foo>()
          instances.shouldNotBeNull()
          instances.size shouldBe 2
          instances[0].bar shouldBe "2"
          instances[1].bar shouldBe "1"

          val firstInstance = testDI.resolve<Foo>() as Foo
          firstInstance.shouldNotBeNull()
          firstInstance.bar shouldBe "2"
        }

        test("resolve by name") {
          val testDI =
              spiderDI {
                    register<Foo> {
                      name = "single"
                      instanceType = InstanceType.FACTORY
                      provider = { Foo(Random.nextInt().toString()) }
                    }
                  }
                  .start()
          val instance = testDI.resolveByName<Foo>("single")
          instance.shouldNotBeNull()
        }

        test("resolve by class name") {
          val testDI =
              spiderDI {
                    register<Foo> {
                      instanceType = InstanceType.FACTORY
                      provider = { Foo(Random.nextInt().toString()) }
                    }
                  }
                  .start()
          val instance = testDI.resolveByName<Foo>(Foo::class.java.name)
          instance.shouldNotBeNull()
        }

        test("testDependencyInjectionForInterfaces") {
          runTest {
            val testDI =
                spiderDI {
                      register<Foo> {
                        instanceType = InstanceType.FACTORY
                        provider = { Foo(Random.nextInt().toString()) }
                      }
                      register<FooRepo> {
                        intf = IFooRepo::class.java
                        provider = { di -> FooRepo(di.resolve()) }
                      }
                      register<FooService> {
                        intf = IFooService::class.java
                        provider = { di ->
                          FooService(
                              foo = di.resolveByName(Foo::class.java.name),
                              fooRepo = di.resolve(),
                              value = "value")
                        }
                      }
                    }
                    .start()
            val fooService = testDI.resolve<IFooService>()
            val fooRepo = testDI.resolve() as IFooRepo
            fooService.getFooRepo() shouldBe fooRepo
            fooService.getFoo() shouldNotBe fooRepo.getFoo()
          }
        }

        test("resolve method when class is not found") {
          val testDI = spiderDI {}.start()
          shouldThrow<IllegalStateException> { runTest { testDI.resolve() as Foo } }
              .message shouldBe "Class definition not found for io.github.ivsokol.spider.Foo"
        }

        test("resolveAll method when no classes are found") {
          val testDI = spiderDI {}.start()
          runTest {
            val instances = testDI.resolveAll<Foo>()
            instances.size shouldBe 0
          }
        }

        test("resolve by name when interface is wrong") {
          runTest {
            val testDI =
                spiderDI {
                      register<Foo> {
                        instanceType = InstanceType.FACTORY
                        provider = { Foo(Random.nextInt().toString()) }
                      }
                      register<FooRepo> {
                        intf = IFooRepo::class.java
                        provider = { di -> FooRepo(di.resolve()) }
                      }
                      register<FooService> {
                        intf = IFooService::class.java
                        provider = { di ->
                          FooService(
                              foo = di.resolveByName(FooRepo::class.java.name),
                              fooRepo = di.resolveByName(Foo::class.java.name),
                              value = "value")
                        }
                      }
                    }
                    .start()
            shouldThrow<IllegalStateException> { testDI.resolve() as IFooService }
                .message shouldContain
                "Class cast to io.github.ivsokol.spider.Foo failed for io.github.ivsokol.spider.FooRepo"
          }
        }

        test("createdAtStart when interface is wrong") {
          runTest {
            shouldThrow<IllegalStateException> {
                  spiderDI {
                        register<Foo> {
                          instanceType = InstanceType.FACTORY
                          provider = { Foo(Random.nextInt().toString()) }
                        }
                        register<FooRepo> {
                          intf = IFooRepo::class.java
                          provider = { di -> FooRepo(di.resolve()) }
                        }
                        register<FooService> {
                          intf = IFooService::class.java
                          createdAtStart = true
                          provider = { di ->
                            FooService(
                                foo = di.resolveByName(FooRepo::class.java.name),
                                fooRepo = di.resolveByName(Foo::class.java.name),
                                value = "value")
                          }
                        }
                      }
                      .start()
                }
                .message shouldContain
                "Class cast to io.github.ivsokol.spider.Foo failed for io.github.ivsokol.spider.FooRepo"
          }
        }

        test("resolve by name class cast exception") {
          val testDI =
              spiderDI {
                    register<Foo> {
                      name = "single"
                      instanceType = InstanceType.SINGLETON
                      provider = { Foo(Random.nextInt().toString()) }
                    }
                  }
                  .start()
          val instance = testDI.resolveByName<Foo>("single")
          instance.shouldNotBeNull()
          shouldThrow<IllegalArgumentException> { testDI.resolveByName<FooRepo>("single") }
              .message shouldBe "Class cast to io.github.ivsokol.spider.FooRepo failed for single"
        }

        test("resolveAll should return all instances") {
          val testDI =
              spiderDI {
                    register<Foo> {
                      name = "foo1"
                      instanceType = InstanceType.FACTORY
                      provider = { Foo("foo1") }
                    }
                    register<Foo> {
                      name = "foo2"
                      instanceType = InstanceType.FACTORY
                      provider = { Foo("foo2") }
                    }
                  }
                  .start()

          val instances = testDI.resolveAll<Foo>()
          instances.size shouldBe 2
          instances[0].bar shouldNotBe instances[1].bar
        }
      }

      context("dependency Lock") {
        test("test dependency lock") {
          runTest {
            val testDI =
                spiderDI {
                      register<Foo> {
                        instanceType = InstanceType.FACTORY
                        provider = { Foo(Random.nextInt().toString()) }
                      }
                      register<FooRepo> {
                        intf = IFooRepo::class.java
                        provider = { di -> FooRepo(di.resolve()) }
                      }
                      register<FooService> {
                        intf = IFooService::class.java
                        isLocked = true
                        dependencies = listOf(IFooRepo::class.java.name)
                        provider = { di ->
                          FooService(
                              foo = di.resolveByName(Foo::class.java.name),
                              fooRepo = di.resolve(),
                              value = "value")
                        }
                      }
                    }
                    .start()
            val fooService = testDI.resolve() as IFooService
            val fooRepo: IFooRepo = testDI.resolve()
            fooService.getFooRepo() shouldBe fooRepo
            fooService.getFoo() shouldNotBe fooRepo.getFoo()
            val reg =
                testDI.registry().values.firstOrNull { it.className == FooRepo::class.java.name }
            reg.shouldNotBeNull()
            reg.dependencyLock shouldBe true
          }
        }

        test("check dependency lock over non existing class") {
          shouldThrow<IllegalArgumentException> {
                spiderDI {
                      register<Foo> {
                        instanceType = InstanceType.FACTORY
                        provider = { Foo(Random.nextInt().toString()) }
                      }
                      register<FooRepo> {
                        isLocked = true
                        intf = IFooRepo::class.java
                        dependencies = listOf("NotFound")
                        provider = { di -> FooRepo(di.resolve()) }
                      }
                    }
                    .start()
              }
              .message shouldBe "Dependency NotFound not found"
        }

        test("createAtStart dependency lock") {
          runTest {
            val testDI =
                spiderDI {
                      register<Foo> {
                        instanceType = InstanceType.FACTORY
                        provider = { Foo(Random.nextInt().toString()) }
                      }
                      register<FooRepo> {
                        intf = IFooRepo::class.java
                        provider = { di -> FooRepo(di.resolve()) }
                      }
                      register<FooService> {
                        intf = IFooService::class.java
                        createdAtStart = true
                        dependencies = listOf(IFooRepo::class.java.name)
                        provider = { di ->
                          FooService(foo = di.resolve(), fooRepo = di.resolve(), value = "value")
                        }
                      }
                    }
                    .start()
            val fooService = testDI.resolve() as IFooService
            val fooRepo: IFooRepo = testDI.resolve()
            fooService.getFooRepo() shouldBe fooRepo
            fooService.getFoo() shouldNotBe fooRepo.getFoo()
            val reg =
                testDI.registry().values.firstOrNull { it.className == FooRepo::class.java.name }
            reg.shouldNotBeNull()
            reg.dependencyLock shouldBe true
          }
        }
        test("dependency check should throw if it cannot find dependency") {
          val testDI =
              spiderDI {
                    register<FooRepo> {
                      intf = IFooRepo::class.java
                      dependencies = listOf("Foo")
                      provider = { di ->
                        FooRepo(
                            foo = di.resolve(),
                        )
                      }
                    }
                  }
                  .start()
          shouldThrow<IllegalStateException> { testDI.resolve<IFooRepo>() }.message shouldContain
              "Class definition not found for io.github.ivsokol.spider.Foo"
        }
      }

      context("module") {
        test("loading test") {
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
                        FooService(foo = di.resolve(), fooRepo = di.resolve(), value = "value")
                      }
                    }
                  }
                  .start()

          val fooService = serviceDI.resolve() as IFooService
          val fooRepo: IFooRepo = serviceDI.resolve()
          fooService.getFooRepo() shouldBe fooRepo
          fooService.getFoo() shouldNotBe fooRepo.getFoo()
          val reg =
              serviceDI.registry().values.firstOrNull { it.className == FooRepo::class.java.name }
          reg.shouldNotBeNull()
          reg.dependencyLock shouldBe true
        }
        test("multi module loading test") {
          val fooDI = spiderDI {
            register<Foo> {
              instanceType = InstanceType.FACTORY
              provider = { Foo(Random.nextInt().toString()) }
            }
          }

          val repoDI = spiderDI {
            register<FooRepo> {
              intf = IFooRepo::class.java
              provider = { di -> FooRepo(di.resolve()) }
            }
          }
          val serviceDI =
              spiderDI {
                    modules(fooDI, repoDI)
                    register<FooService> {
                      intf = IFooService::class.java
                      createdAtStart = true
                      dependencies = listOf(IFooRepo::class.java.name)
                      provider = { di ->
                        FooService(foo = di.resolve(), fooRepo = di.resolve(), value = "value")
                      }
                    }
                  }
                  .start()

          val fooService = serviceDI.resolve() as IFooService
          val fooRepo: IFooRepo = serviceDI.resolve()
          fooService.getFooRepo() shouldBe fooRepo
          fooService.getFoo() shouldNotBe fooRepo.getFoo()
          val reg =
              serviceDI.registry().values.firstOrNull { it.className == FooRepo::class.java.name }
          reg.shouldNotBeNull()
          reg.dependencyLock shouldBe true
        }
        test("should fail when loading dependency locked classes") {
          val repoDI = spiderDI {
            register<Foo> {
              instanceType = InstanceType.SINGLETON
              isLocked = true
              provider = { Foo(Random.nextInt().toString()) }
            }
          }

          shouldThrow<IllegalStateException> {
                spiderDI {
                  modules(repoDI)
                  register<Foo> {
                    instanceType = InstanceType.FACTORY
                    provider = { Foo(Random.nextInt().toString()) }
                  }
                }
              }
              .message shouldBe
              "Class io.github.ivsokol.spider.Foo is locked and cannot be registered again."
        }
        test("should fail when loading dependency locked classes in root") {
          val repoDI = spiderDI {
            register<Foo> {
              instanceType = InstanceType.SINGLETON
              provider = { Foo(Random.nextInt().toString()) }
            }
            register<FooRepo> {
              intf = IFooRepo::class.java
              createdAtStart = true
              dependencies = listOf(Foo::class.java.name)
              provider = { di -> FooRepo(di.resolve()) }
            }
          }

          val repoDI2 = spiderDI {
            register<Foo> {
              instanceType = InstanceType.FACTORY
              provider = { Foo(Random.nextInt().toString()) }
            }
          }
          shouldThrow<IllegalStateException> { spiderDI { modules(repoDI, repoDI2) } }
              .message shouldBe
              "Class io.github.ivsokol.spider.Foo is locked and cannot be overridden."
        }
      }

      context("lock") {
        test("should not register new classes after DI lock") {
          val testDI =
              spiderDI {
                    register<Foo> {
                      instanceType = InstanceType.FACTORY
                      provider = { Foo(Random.nextInt().toString()) }
                    }
                  }
                  .start()
          testDI.lock()
          shouldThrow<IllegalStateException> {
                testDI.register(
                    ClassMeta("foo", Foo::class.java.name, Foo::class.java),
                    false,
                    false,
                    InstanceType.FACTORY,
                    emptyList(),
                    0) {
                      Foo(Random.nextInt().toString())
                    }
              }
              .message shouldBe "Cannot register new classes when locked"
        }

        test("should not register new module when locked") {
          val testDI =
              spiderDI {
                    register<Foo> {
                      instanceType = InstanceType.FACTORY
                      provider = { Foo(Random.nextInt().toString()) }
                    }
                  }
                  .start()
          testDI.lock()
          shouldThrow<IllegalStateException> {
                testDI.modules(
                    listOf(
                        spiderDI {
                          register<Foo> {
                            instanceType = InstanceType.FACTORY
                            provider = { Foo(Random.nextInt().toString()) }
                          }
                        }))
              }
              .message shouldBe "Cannot register modules when locked"
        }

        test("modules method with conflicting locked classes") {
          val fooDI = spiderDI {
            register<Foo> {
              instanceType = InstanceType.SINGLETON
              isLocked = true
              provider = { Foo("example") }
            }
          }

          val conflictingDI = spiderDI {
            register<Foo> {
              instanceType = InstanceType.SINGLETON
              provider = { Foo("conflict") }
            }
          }

          val mainDI = spiderDI { module(fooDI) }.start()

          shouldThrow<IllegalStateException> { mainDI.modules(listOf(conflictingDI)) }
              .message shouldBe
              "Class io.github.ivsokol.spider.Foo is locked and cannot be overridden."
        }
      }

      context("destory") {
        test("invoke destroy() method") {
          runTest {
            DummyObject.resetFlag()
            val testDI =
                spiderDI {
                      register<Foo> {
                        instanceType = InstanceType.FACTORY
                        provider = { Foo(Random.nextInt().toString()) }
                      }
                      register<DestroyableFooRepo> {
                        intf = IFooRepo::class.java
                        provider = { di -> DestroyableFooRepo(di.resolve()) }
                      }
                      register<FooService> {
                        intf = IFooService::class.java
                        dependencies = listOf(IFooRepo::class.java.name)
                        provider = { di ->
                          FooService(foo = di.resolve(), fooRepo = di.resolve(), value = "value")
                        }
                      }
                    }
                    .start()
            DummyObject.flag shouldBe false
            val fooService = testDI.resolve() as IFooService
            val fooRepo = testDI.resolve() as IFooRepo
            fooService.getFooRepo() shouldBe fooRepo
            fooService.getFoo() shouldNotBe fooRepo.getFoo()
            DummyObject.flag shouldBe false
            testDI.destroy()
            DummyObject.flag shouldBe true
            DummyObject.resetFlag()
          }
        }

        test("destroy method when no instances are present") {
          val testDI = spiderDI {}.start()
          runTest { testDI.destroy() }
        }
      }

      context("circular dependencies") {
        test("circular dependency by class should throw exception") {
          val deps = mutableSetOf<String>()
          val testDI = spiderDI {
            register<A> { provider = { di -> A(di.resolveWithCheck(deps)) } }
            register<B> { provider = { di -> B(di.resolveWithCheck(deps)) } }
            register<C> { provider = { di -> C(di.resolveWithCheck(deps)) } }
          }

          shouldThrow<IllegalStateException> { runTest { testDI.resolveWithCheck<A>(deps) } }
              .message shouldContain "Circular dependency detected"
        }
        test("circular dependency by name should throw exception") {
          val deps = mutableSetOf<String>()
          val testDI = spiderDI {
            register<A> {
              name = "a"
              provider = { di -> A(di.resolveByNameWithCheck("b", deps)) }
            }
            register<B> {
              name = "b"
              provider = { di -> B(di.resolveByNameWithCheck("c", deps)) }
            }
            register<C> {
              name = "c"
              provider = { di -> C(di.resolveByNameWithCheck("a", deps)) }
            }
          }

          shouldThrow<IllegalStateException> {
                runTest { testDI.resolveByNameWithCheck("a", deps) }
              }
              .message shouldContain "Circular dependency detected"
        }
      }
    })
