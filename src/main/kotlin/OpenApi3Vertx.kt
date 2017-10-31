import io.vertx.core.json.JsonObject.mapFrom
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

    paths {
        get("/hello") {
            operationId = "hello"
            description = "hello get"
            code("200") {
                description = "a 200 response"
                response<HelloResponse>("application/json")
            }
        }

        post("/hello") {
            operationId = "postHello"
            description = "hello post"
            code("200") {
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
                    "Content-Type"
            ))
    router.route().handler(create)

    router.get("/spec.json").handler { routingContext ->
        routingContext.response()
                .putHeader("Content-Type", "application/json")
                .end(api3.asJson().toString(2))
    }
}

private fun createOperationHandlers(routerFactory: OpenAPI3RouterFactory) {
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

    routerFactory.addFailureHandlerByOperationId("hello", { routingContext ->
        println("FAIL")
        routingContext.fail(500)
    })
}