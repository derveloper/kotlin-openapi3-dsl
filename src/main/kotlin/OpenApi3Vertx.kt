
import io.vertx.kotlin.core.http.HttpServerOptions
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.api.contract.openapi3.OpenAPI3RouterFactory

private val vertx = Vertx.vertx()

fun main(args: Array<String>) {
    val api3 = openapi3 {
        info {
            title = "test api"
            version = "0.0.1"
        }

        paths {
            get("/hello") {
                operationId = "hello"
                code("200") {
                    response<String>("text/plain")
                }
            }
        }
    }

    val apiFile = api3.asFile()
    OpenAPI3RouterFactory.rxCreateRouterFactoryFromFile(vertx, apiFile.absolutePath)
            .doOnError { it.printStackTrace() }
            .doOnSuccess(::createOperationHandlers)
            .doOnSubscribe { println("Server started") }
            .subscribe(::startServer)
}

fun startServer(routerFactory: OpenAPI3RouterFactory) {
    val server = vertx.createHttpServer(HttpServerOptions(
            port = 8080,
            host = "localhost"))
    val router = routerFactory.router
    server.requestHandler({ router.accept(it) }).listen(8080)
}

private fun createOperationHandlers(routerFactory: OpenAPI3RouterFactory) {
    routerFactory.addHandlerByOperationId("hello", { routingContext ->
        routingContext.response().end("Hello World!")
    })

    routerFactory.addFailureHandlerByOperationId("hello", { routingContext ->
        println("FAIL")
        routingContext.fail(500)
    })
}