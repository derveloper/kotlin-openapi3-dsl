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

private val api3 = openapi3 {
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
    router.route().handler(CorsHandler.create("^.*$"))

    router.get("/spec.json").handler { routingContext ->
        routingContext.response()
                .putHeader("Content-Type", "application/json")
                .end(api3.asJson().toString(2))
    }
}

private fun createOperationHandlers(routerFactory: OpenAPI3RouterFactory) {
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