package cc.vileda.openapi.dsl

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.callbacks.Callback
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.links.Link
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses

fun Components.response(name: String, init: ApiResponse.() -> Unit) {
    addResponses(name, ApiResponse().apply(init))
}

fun Components.parameter(name: String, init: Parameter.() -> Unit) {
    addParameters(name, Parameter().apply(init))
}

fun Components.example(name: String, init: Example.() -> Unit) {
    addExamples(name, Example().apply(init))
}

fun Components.requestBody(name: String, init: RequestBody.() -> Unit) {
    addRequestBodies(name, RequestBody().apply(init))
}

fun Components.header(name: String, init: Header.() -> Unit) {
    addHeaders(name, Header().apply(init))
}

fun Components.link(name: String, init: Link.() -> Unit) {
    addLinks(name, Link().apply(init))
}

fun Components.callback(name: String, init: Callback.() -> Unit) {
    addCallbacks(name, Callback().apply(init))
}

fun Operation.parameterRef(name: String) {
    addParametersItem(Parameter().apply { `$ref` = name })
}

fun ApiResponses.responseRef(status: String, name: String) {
    addApiResponse(status, ApiResponse().apply { `$ref` = name })
}

fun Operation.requestBodyRef(name: String) {
    requestBody = RequestBody().apply { `$ref` = name }
}

fun MediaType.exampleRef(name: String, componentName: String = name) {
    addExamples(name, Example().apply { `$ref` = componentName })
}

fun ApiResponse.headerRef(name: String, componentName: String = name) {
    addHeaderObject(name, Header().apply { `$ref` = componentName })
}

fun ApiResponse.linkRef(name: String, componentName: String = name) {
    addLink(name, Link().apply { `$ref` = componentName })
}

fun Operation.callbackRef(name: String, componentName: String = name) {
    addCallback(name, Callback().apply { `$ref` = componentName })
}
