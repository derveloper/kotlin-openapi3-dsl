# kotlin-openapi3-dsl

![Java CI with Gradle](https://github.com/derveloper/kotlin-openapi3-dsl/workflows/Java%20CI%20with%20Gradle/badge.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/cc.vileda/kotlin-openapi3-dsl/badge.svg)](https://maven-badges.herokuapp.com/maven-central/cc.vileda/kotlin-openapi3-dsl)

Build your OpenApi3 spec in kotlin!

## import

kotlin-openapi3-dsl is available on maven central

### gradle

```groovy
compile "cc.vileda:kotlin-openapi3-dsl:1.6.0"
```

### maven
```xml
<dependency>
    <groupId>cc.vileda</groupId>
    <artifactId>kotlin-openapi3-dsl</artifactId>
    <version>1.6.0</version>
</dependency>
```

## example

for a complete example [look at the test](src/test/kotlin/cc/vileda/openapi/dsl/OpenApiDslTest.kt)

### reusable components

The `components` block supports every OpenAPI 3.0 component type: schemas,
responses, parameters, examples, request bodies, headers, security schemes,
links, and callbacks. Reference helpers accept component names and create the
canonical `#/components/...` reference:

```kotlin
components {
    parameter("PageSize") {
        name = "pageSize"
        `in` = "query"
    }
    response("NotFound") {
        description = "resource not found"
    }
}

paths {
    path("/items") {
        get {
            parameterRef("PageSize")
            responses {
                responseRef("404", "NotFound")
            }
        }
    }
}
```

### recursive schemas

`mediaTypeRef<T>()` and `mediaTypeArrayOfRef<T>()` automatically add `T` and
all transitively referenced models to `components.schemas`. An explicit
components block is not required:

```kotlin
data class ErrorDetail(val message: String)
data class ErrorResponse(val detail: ErrorDetail)

content {
    mediaTypeRef<ErrorResponse>("application/json")
}
```

`components { schema<T>() }` also registers transitive models. Explicit schema
customisations take precedence over automatically discovered schemas.

### Kotlin required properties

Required-property inference is disabled by default. Enable it for one DSL build:

```kotlin
openapiDsl(
    OpenApiDslConfig(inferRequiredFromKotlinNullability = true)
) {
    // ...
}
```

Non-null Kotlin primary-constructor properties without default values are then
added to the schema's `required` list. Nullable and defaulted properties remain
optional. Explicit Swagger `requiredMode` annotations take precedence.

## output

`asJsonString()` and `asFile()` preserve the insertion order used by the DSL.
Use `asJsonString(pretty = true)` for formatted JSON without writing a file.
`asJson()` returns a `JSONObject`, whose key order is intentionally unspecified.

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
