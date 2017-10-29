# kotlin-openapi3-dsl

Build your OpenApi3 spec in kotlin!

## example

for a complete example [look at the test](src/test/kotlin/OpenApi3BuilderTest.kt)

given this code

```kotlin
val spec = openapi3 {
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
```

transforms to
```json
{
  "components": {"schemas": {}},
  "info": {
    "title": "test api",
    "version": "0.0.1"
  },
  "openapi": "3.0.0",
  "paths": {
    "/hello": {
      "get": {
        "description": "",
        "operationId": "hello",
        "responses": {
          "200": {
            "content": {
              "text/plain": {
                "schema": {"type": "string"}
               }
            },
            "description": ""
          }
        }
      }
    }
  }
}
```

## todo

- [] Implement all OpenApi3 fields
- [] Make compatible to vertx OpenAPI3RouterFactory
- [] Publish on maven central


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