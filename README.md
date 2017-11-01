# kotlin-openapi3-dsl

[ ![Download](https://api.bintray.com/packages/vileda/maven/kotlin-openapi3-dsl/images/download.svg) ](https://bintray.com/vileda/maven/kotlin-openapi3-dsl/_latestVersion)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/cc.vileda/kotlin-openapi3-dsl/badge.svg)](https://maven-badges.herokuapp.com/maven-central/cc.vileda/kotlin-openapi3-dsl)

Build your OpenApi3 spec in kotlin!

## import

kotlin-openapi3-dsl is available on maven central

### gradle

```groovy
compile "cc.vileda:kotlin-openapi3-dsl:0.15.0"
```

### maven
```xml
<dependency>
    <groupId>cc.vileda</groupId>
    <artifactId>kotlin-openapi3-dsl</artifactId>
    <version>0.15.0</version>
</dependency>
```

## example

for a complete example [look at the test](src/test/kotlin/OpenApi3BuilderTest.kt)

### minimal example 

```kotlin
data class HelloResponse(
        val message: String
)

val spec = openapi3 {
    info {
        title = "test api"
        version = "0.0.1"
    }

    paths {
        get("/hello") {
            operationId = "hello"
            code("200") {
                response<HelloResponse>("application/json")
            }
        }
    }
}
```

### complete vertx.io example

```kotlin
import cc.vileda.openapi3.SecurityScheme
import cc.vileda.openapi3.openapi3
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject.mapFrom
import io.vertx.ext.web.api.RequestParameters
import io.vertx.kotlin.core.http.HttpServerOptions
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.api.contract.openapi3.OpenAPI3RouterFactory
import io.vertx.reactivex.ext.web.handler.CorsHandler

private val vertx = Vertx.vertx()

data class HelloResponse(
        val message: String
)

data class HelloRequest(
        val message: String
)

private val api3 = openapi3 {
    info {
        title = "test api"
        version = "0.0.1"
    }

    securityScheme {
        name = "apiKey"
        type = SecurityScheme.Type.API_KEY
        `in` = SecurityScheme.In.HEADER
    }

    security {
        put("apiKey", emptyList())
    }

    paths {
        get("/hello") {
            tags = listOf("without params")
            operationId = "hello"
            description = "hello get"
            ok {
                description = "a 200 response"
                response<HelloResponse>("application/json")
            }
            code("401") {
                description = "apiKey invalid"
            }
            security {
                put("apiKey", emptyList())
            }
        }

        get("/hello/{name}") {
            tags = listOf("with params")
            operationId = "helloName"
            description = "hello get"
            parameter {
                name = "name"
                schema<String>()
            }
            ok {
                description = "a 200 response"
                response<HelloResponse>("application/json")
            }
        }

        post("/hello") {
            tags = listOf("without params")
            operationId = "postHello"
            description = "hello post"
            created {
                description = "a 200 response"
                requestBody {
                    description = "example request"
                    request<HelloRequest>("application/json")
                    request<HelloRequest>("application/xml")
                }
                response<HelloResponse>("application/json")
            }
        }
    }
}

fun main(args: Array<String>) {
    val apiFile = api3.asFile()
    println(api3.asJson().toString(2))
    OpenAPI3RouterFactory.rxCreateRouterFactoryFromFile(vertx, apiFile.absolutePath)
            .doOnError { it.printStackTrace() }
            .doOnSuccess(::createOperationHandlers)
            .doOnSubscribe { println("Server started") }
            .subscribe(::startServer)
}

fun startServer(routerFactory: OpenAPI3RouterFactory) {
    val mainRouter = Router.router(vertx)
    bindAdditionalHandlers(mainRouter)
    mainRouter.mountSubRouter("/", routerFactory.router)
    val server = vertx.createHttpServer(HttpServerOptions(
            port = 8080,
            host = "localhost"))
    server.requestHandler({ mainRouter.accept(it) }).listen(8080)
}

fun bindAdditionalHandlers(router: Router) {
    val create = CorsHandler.create("^.*$")
            .allowedHeaders(setOf(
                    "Content-Type",
                    "apiKey"
            ))
    router.route().handler(create)

    router.get("/spec.json").handler { routingContext ->
        routingContext.response()
                .putHeader("Content-Type", "application/json")
                .end(api3.asJson().toString(2))
    }
}

private fun createOperationHandlers(routerFactory: OpenAPI3RouterFactory) {
    routerFactory.addSecurityHandler("apiKey", Handler {
        if (it.request().getHeader("apiKey") == "foo") it.next()
        else it.fail(401)
    })

    routerFactory.addHandlerByOperationId("postHello", { routingContext ->
        routingContext.response()
                .putHeader("Content-Type", "application/json")
                .end(mapFrom(HelloResponse("Hello World!")).encode())
    })

    routerFactory.addHandlerByOperationId("hello", { routingContext ->
        routingContext.response()
                .putHeader("Content-Type", "application/json")
                .end(mapFrom(HelloResponse("Hello World!")).encode())
    })

    routerFactory.addHandlerByOperationId("helloName", { routingContext ->
        val params: RequestParameters = routingContext.get("parsedParameters")
        val name = params.pathParameter("name").string
        routingContext.response()
                .putHeader("Content-Type", "application/json")
                .end(mapFrom(HelloResponse("Hello $name!")).encode())
    })

    routerFactory.addFailureHandlerByOperationId("hello", { routingContext ->
        println("FAIL")
        routingContext.fail(500)
    })
}
```

## todo

- [x] Make compatible to vertx [OpenAPI3RouterFactory](src/main/kotlin/OpenApi3Vertx.kt)
- [ ] Implement all OpenApi3 fields
  - [ ] paths
    - [x] all HTTP methods
    - [x] minimal features
    - [x] security features
    - [ ] complete features
    - [ ] requestBody
       - [x] minimal features
       - [ ] examples
       - [ ] complete features
    - [ ] parameters
      - [x] minimal features
      - [ ] complete features
  - [x] components
  - [x] $ref to components
- [x] Publish on bintray
- [x] Publish on jcenter
- [x] Publish on maven central


## license
```
Copyright 2017 Tristan Leo

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```