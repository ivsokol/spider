package io.github.ivsokol.spider

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlin.random.Random
import kotlinx.coroutines.test.runTest

class SpiderContainerTest :
    StringSpec({
      val testDI = spiderDI {
        register<Foo> {
          instanceType = InstanceType.FACTORY
          provider = { Foo(Random.nextInt().toString()) }
        }
        register<DestroyableFooRepo> {
          intf = IFooRepo::class.java
          name = "myRepo"
          createdAtStart = true
          provider = { di -> DestroyableFooRepo(di.resolve()) }
        }
        register<FooService> {
          intf = IFooService::class.java
          provider = { di ->
            FooService(
                foo = di.resolveByName(Foo::class.java.name),
                fooRepo = di.resolve(),
                value = "value",
            )
          }
        }
      }

      testExecutionMode = TestExecutionMode.Sequential
      isolationMode = IsolationMode.InstancePerLeaf

      beforeTest {
        try {
          SpiderContainer.setUp(testDI)
        } catch (_: IllegalStateException) {
          // ignore
        }
      }

      "should set up and start DependencyInjection instance" {
        SpiderContainer.registry().size shouldBe 3
      }

      "should refresh and start DependencyInjection instance" {
        val testDI2 = spiderDI {
          register<Foo> {
            instanceType = InstanceType.FACTORY
            provider = { Foo(Random.nextInt().toString()) }
          }
          register<FooRepo> {
            intf = IFooRepo::class.java
            provider = { di -> FooRepo(di.resolve()) }
          }
        }
        SpiderContainer.refresh(testDI2)
        SpiderContainer.registry().size shouldBe 2
      }

      "should inject dependency by class type" {
        SpiderContainer.refresh(testDI)
        val result = SpiderContainer.inject<IFooService>()

        result.getFoo().bar shouldNotBe null
        (result as FooService).value shouldBe "value"
      }

      "should inject all dependencies by class type" {
        val testDI3 = spiderDI {
          register<Foo> {
            instanceType = InstanceType.SINGLETON
            name = "foo1"
            priority = 1
            provider = { Foo("1") }
          }
          register<Foo> {
            instanceType = InstanceType.SINGLETON
            name = "foo2"
            provider = { Foo("2") }
          }
        }
        SpiderContainer.refresh(testDI3)
        val result = SpiderContainer.injectAll<Foo>()

        result.size shouldBe 2
        result[0].bar shouldBe "2"
      }

      "should inject dependency by name" {
        SpiderContainer.refresh(testDI)
        val result: IFooRepo = SpiderContainer.inject("myRepo")

        result.getFoo().bar shouldNotBe null
      }

      "should inject started dependency by class type" {
        val testDI4 = spiderDI {
          register<Foo> {
            createdAtStart = true
            provider = { Foo("started") }
          }
        }
        SpiderContainer.refresh(testDI4)
        val result = SpiderContainer.injectStarted<Foo>()
        result.bar shouldBe "started"
      }

      "should throw when injecting not started dependency by class type" {
        val testDI5 = spiderDI {
          register<Foo> {
            createdAtStart = false
            provider = { Foo("not started") }
          }
        }
        SpiderContainer.refresh(testDI5)
        shouldThrow<IllegalStateException> { SpiderContainer.injectStarted<Foo>() }
            .message shouldContain "is not started"
      }

      "should inject all started dependencies by class type" {
        val testDI6 = spiderDI {
          register<Foo> {
            name = "foo1"
            createdAtStart = true
            provider = { Foo("1") }
          }
          register<Foo> {
            name = "foo2"
            createdAtStart = true
            provider = { Foo("2") }
          }
        }
        SpiderContainer.refresh(testDI6)
        val result = SpiderContainer.injectAllStarted<Foo>()
        result.size shouldBe 2
      }

      "should throw when injecting all started dependencies if one is not started" {
        val testDI7 = spiderDI {
          register<Foo> {
            name = "foo1"
            createdAtStart = true
            provider = { Foo("1") }
          }
          register<Foo> {
            name = "foo2"
            createdAtStart = false
            provider = { Foo("2") }
          }
        }
        SpiderContainer.refresh(testDI7)
        shouldThrow<IllegalStateException> { SpiderContainer.injectAllStarted<Foo>() }
            .message shouldContain "is not started"
      }

      "should inject started dependency by name" {
        val testDI8 = spiderDI {
          register<Foo> {
            name = "foo"
            createdAtStart = true
            provider = { Foo("started") }
          }
        }
        SpiderContainer.refresh(testDI8)
        val result = SpiderContainer.injectStarted<Foo>("foo")
        result.bar shouldBe "started"
      }

      "should throw when injecting not started dependency by name" {
        val testDI9 = spiderDI {
          register<Foo> {
            name = "foo"
            createdAtStart = false
            provider = { Foo("not started") }
          }
        }
        SpiderContainer.refresh(testDI9)
        shouldThrow<IllegalStateException> { SpiderContainer.injectStarted<Foo>("foo") }
            .message shouldContain "is not started"
      }

      "should retrieve service registry" {
        SpiderContainer.refresh(testDI)
        val result = SpiderContainer.registry()

        result.size shouldBe 3
      }

      "should throw on circular dependency" {
        val deps = mutableSetOf<String>()
        val testDI = spiderDI {
          register<A> { provider = { di -> A(di.resolveWithCheck(deps)) } }
          register<B> { provider = { di -> B(di.resolveWithCheck(deps)) } }
          register<C> { provider = { di -> C(di.resolveWithCheck(deps)) } }
        }
        SpiderContainer.refresh(testDI)
        shouldThrow<IllegalStateException> { runTest { SpiderContainer.inject<A>() } }
            .message shouldContain "Circular dependency detected"
      }

      "should shutdown DependencyInjection instance" {
        DummyObject.resetFlag()
        SpiderContainer.refresh(testDI)
        DummyObject.flag shouldBe false
        SpiderContainer.shutdown()
        DummyObject.flag shouldBe true
        DummyObject.resetFlag()
      }
    })
