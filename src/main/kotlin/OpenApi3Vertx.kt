
import io.vertx.core.Vertx
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory
import io.vertx.kotlin.core.http.HttpServerOptions

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

    println(api3.asJson())

    val vertx = Vertx.vertx()

    val apiFile = api3.asFile()
    println(apiFile.absolutePath)

    OpenAPI3RouterFactory.createRouterFactoryFromFile(vertx, apiFile.absolutePath, { ar ->
        if (ar.succeeded()) {
            // Spec loaded with success
            val routerFactory = ar.result()
            val router = routerFactory.router

            routerFactory.addHandlerByOperationId("hello", { routingContext ->
                routingContext.response().end("Hello World!")
            })

            routerFactory.addFailureHandlerByOperationId("hello", { routingContext ->
                println("FAIL")
                routingContext.fail(500)
            })

            val server = vertx.createHttpServer(HttpServerOptions(
                    port = 8080,
                    host = "localhost"))
            server.requestHandler({ router.accept(it) }).listen(8080)
        }
        else {
            ar.cause()
        }
    })
}