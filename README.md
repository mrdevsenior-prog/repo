# Audit Monolith: Log Audit y dashboard MongoDB

Envío de eventos a la API del producer Log Audit y consulta de los eventos que la POC almacena en MongoDB. Angular, Angular Material, el API de consulta y el envío se empaquetan dentro del mismo JAR de Spring Boot: una aplicación, un puerto y un despliegue. El navegador consume `/api/v1/audit`; la conexión MongoDB y la URL del producer se configuran en el backend.

Versiones estables verificadas en npm el 13 de noviembre de 2026: Angular **22.2.6**, Angular Material/CDK **22.2.7**, CLI/build **22.2.7** y TypeScript **6.0.x**, fijadas mediante `package-lock.json`. Backend: Java **25**, Spring Boot **4.0.9** y Gradle Wrapper **9.1.0**, siguiendo la POC existente.

## Funcionalidad

- `POST /api/v1/audit/security/log`: envía el evento completo al producer configurado en `AUDIT_API_URL`, reutilizando la POC reactiva con WebClient, MapStruct, dominio y puertos hexagonales.
- Conserva las cuatro cabeceras, los veinte detalles del ejemplo, las fechas con su zona y los campos dinámicos de `channel_payload_header`. Incluye validación, timeouts, manejo de rechazos y ausencia de reintentos o redirecciones del POST.
- Consulta de `lineage_audit.tapp_audit_events`, configurable por entorno.
- Búsqueda literal, sin distinguir mayúsculas, en UID, usuario, sesión, operación, documento y cuenta BT.
- Filtros combinados por aplicación, prioridad e intervalo `[desde, hasta)`.
- Fechas mostradas y seleccionadas en hora de Lima, UTC−05. El resumen «hoy» utiliza `AUDIT_TIMEZONE`, por defecto `America/Lima`.
- Paginación y ordenamiento en MongoDB, con desempate por `_id`.
- Resúmenes calculados sobre todos los resultados filtrados, no solo la página visible.
- Dashboard inicial de los últimos siete días con indicadores de volumen, aplicaciones, alta prioridad y actividad de hoy.
- Cinco gráficos calculados en MongoDB: barras de actividad diaria, distribución de prioridades, ranking de aplicaciones, acciones y distribución por hora. Pulsar un día, una prioridad o una aplicación aplica ese filtro a indicadores, gráficos y eventos.
- Selector de períodos, filtros adicionales desplegables y exportación JSON de las estadísticas. Navegación entre **Dashboard** y **Eventos**, con la vista y los filtros conservados en la URL.
- Catálogos de aplicación y prioridad leídos de la colección.
- Botón visible **Ver detalle** en cada fila, fijado al extremo derecho de la tabla durante el desplazamiento horizontal.
- Detalle compacto en tres pestañas: **Detalle**, **Documento completo** y **JSON**. La primera muestra un formulario de solo lectura con usuario, aplicación, operación, prioridad, fecha del evento y fecha de registro, además de la sesión y las listas del documento en tablas de Angular Material.
- Las tablas de arreglos generan sus columnas a partir de todos los elementos y conservan valores nulos, campos dinámicos y listas anidadas. **Documento completo** permite recorrer todos los campos; **JSON** permite consultar y descargar el documento original convertido desde BSON.
- Se conservan los campos dinámicos de `event_message`, incluidos los dos formatos de `channel_payload_detail` usados por la POC. La trazabilidad presenta las fechas del documento seleccionado; no infiere eventos de otros servicios ni una fecha real de eliminación a partir de `expires_at`.
- Descarga JSON de la página actual y del documento seleccionado.
- Estados de carga, error, lista vacía y conexión. Filtros conservados en la URL.
- Interfaz adaptable, accesible por teclado, con tabla, formularios, selección, diálogo, pestañas y paginador de Angular Material.

El dashboard y los endpoints de consulta son de solo lectura sobre MongoDB. El endpoint de envío hace POST al producer; no inserta directamente en MongoDB ni consume Kafka. La persistencia continúa por el flujo existente del producer y el consumer, por lo que un envío exitoso puede tardar en aparecer en el dashboard. La aplicación no crea índices, colecciones ni datos al iniciar, ni cambia TTL o retención.

Todo el backend está en un único proyecto Gradle y en `src/main/java/pe/com/scotiabank/tapp/audit/`, incluidas las constantes HTTP, DTOs, mappers, servicios y cliente del producer. Hay una sola clase de inicio, `AuditApplication`, y un solo servidor WebFlux/Netty. Los fuentes y las pruebas de la POC de envío están integrados en ese mismo proyecto.

El POST conserva el flujo reactivo con WebClient. Las consultas usan `MongoTemplate` y se ejecutan en `Schedulers.boundedElastic()`, fuera de los hilos del servidor Netty. Angular se mantiene en `frontend/` como código fuente de la interfaz y se incorpora al mismo JAR al compilar.

## Compilar y ejecutar con tu MongoDB

Requisitos: JDK 25, MongoDB y Node.js **24.15.0 o superior dentro de la rama 24**, **22.22.3 o superior dentro de la rama 22**, o **26+**, conforme a la compatibilidad de Angular 22. Node 24.14.1 no cumple el mínimo.

Abre esta carpeta como proyecto Gradle en IntelliJ y selecciona JDK 25 como Gradle JVM. En PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# URI y base del MongoDB donde el consumer ya escribe.
$env:MONGODB_URI = 'mongodb://localhost:27017/lineage_audit?retryWrites=false&serverSelectionTimeoutMS=3000&connectTimeoutMS=3000&socketTimeoutMS=10000'
$env:MONGODB_DATABASE = 'lineage_audit'
$env:MONGODB_COLLECTION = 'tapp_audit_events'

# API destino del producer; usa su URL real en tu entorno.
$env:AUDIT_API_URL = 'http://localhost:8181/api/v1/audit/security/log'

.\gradlew.bat build
java -jar build/libs/audit-query-monolith-1.0.0.jar
```

Abre **http://localhost:8080**. Gradle ejecuta `npm ci`, compila Angular y copia sus archivos al JAR. En Linux/macOS utiliza `./gradlew` y la sintaxis de variables de tu shell. `npmExecutable` permite seleccionar otro ejecutable npm: `-PnpmExecutable=/ruta/a/npm`.

El JAR ya compilado requiere Java 25 y acceso a MongoDB para las consultas, y acceso al producer para los envíos; Node y Gradle se utilizan al compilar. `.env.example` documenta las variables, pero Spring Boot no carga archivos `.env` automáticamente.

El repositorio contiene el código fuente del frontend y del backend. Tras compilar con `build`, ejecuta el JAR generado:

```powershell
java -jar build/libs/audit-query-monolith-1.0.0.jar
```

Para MongoDB autenticado, establece `MONGODB_URI` en el entorno de ejecución y utiliza una cuenta con rol `read` sobre la base elegida. `MONGODB_DATABASE` debe indicar explícitamente la base correspondiente. No incluyas credenciales en Angular ni en el repositorio. En producción integra la autenticación corporativa en el backend o en el gateway antes de ampliar el acceso; la POC escucha en loopback por defecto.

## Enviar Log Audit al producer

Importa `postman/LogAuditConsumer.postman_collection.json` y `postman/LogAuditConsumer-Local.postman_environment.json`, y selecciona **Audit Monolith - Local 8080**. La colección incluye el POST original con sus veinte detalles y los GET de consulta, detalle y estadísticas.

También puedes enviar el ejemplo desde PowerShell:

```powershell
curl.exe --request POST "http://localhost:8080/api/v1/audit/security/log" --header "x-api-version: apiversion" --header "x-channel-id: channel" --header "x-originating-appl-code: originating" --header "x-country-code: country" --header "Content-Type: application/json" --data-binary "@examples/audit-request.json"
```

Sustituye las cabeceras y los valores de muestra por los admitidos en tu API. Las IP del ejemplo original no son IPv4 válidas. `AUDIT_API_URL` configura el destino del backend; cambiar `base_url` de Postman solo cambia la aplicación que recibe la petición.

| Variable | Predeterminado |
| --- | --- |
| `AUDIT_API_URL` | `http://localhost:8181/api/v1/audit/security/log` |
| `AUDIT_API_CONNECT_TIMEOUT` | `3s` |
| `AUDIT_API_READ_TIMEOUT` | `10s` |

El cliente limita las respuestas a 256 KiB. Conserva los HTTP 2xx con respuesta válida y los rechazos 4xx del producer; transforma errores 5xx, redirecciones y respuestas incompatibles en 502, fallos de conexión en 503 y timeouts en 504. Los rechazos incluyen `upstream_status`, sin divulgar el cuerpo del error del proveedor. No hay envíos automáticos al iniciar ni al consultar el dashboard.

## Desarrollo de la interfaz

En una terminal, con Node compatible:

```powershell
cd frontend
npm ci
npm start
```

En otra terminal, desde la raíz del proyecto:

```powershell
.\gradlew.bat bootRun -PskipFrontend
```

Abre http://localhost:4200. El proxy de desarrollo envía `/api/**` al backend en `127.0.0.1:8080`. `-PskipFrontend` omite la instalación y compilación de Angular; en el empaquetado final utiliza `build` sin esa opción, salvo que ya hayas compilado la interfaz.

## Datos sintéticos opcionales

La carga de ejemplo es una herramienta separada del JAR de la aplicación. Solo acepta bases cuyo nombre termine en `_demo`, no elimina documentos y utiliza upsert por UID.

```powershell
$env:MONGODB_URI = 'mongodb://localhost:27017/?serverSelectionTimeoutMS=3000'
$env:MONGODB_DATABASE = 'lineage_audit_demo'
.\gradlew.bat seedDemo -PskipFrontend
java -jar build/libs/audit-query-monolith-1.0.0.jar
```

Se insertan 36 eventos identificados como sintéticos, con fechas relativas al momento de carga. Ejecutar de nuevo conserva la primera copia. Para consultar los datos reales, restablece `MONGODB_DATABASE=lineage_audit` y la URI correspondiente.

## Docker Compose

Con Docker en ejecución:

```powershell
docker compose up --build -d
```

Abre http://localhost:8080. Este entorno utiliza su propia instancia MongoDB y la base **`lineage_audit_demo`**, inicialmente vacía. MongoDB se expone en `localhost:27018`, evitando el puerto 27017 del consumer. Para poblarlo, ejecuta desde el host con Java 25:

```powershell
$env:MONGODB_URI = 'mongodb://localhost:27018/?serverSelectionTimeoutMS=3000'
$env:MONGODB_DATABASE = 'lineage_audit_demo'
.\gradlew.bat seedDemo -PskipFrontend
```

El contenedor de la aplicación ejecuta un solo JAR con un usuario sin privilegios. MongoDB es la dependencia de datos, no un segundo backend.

Dentro de Compose, el destino predeterminado del producer es `http://host.docker.internal:8181/api/v1/audit/security/log`. Puedes cambiarlo mediante `AUDIT_API_URL` antes de iniciar Compose. El producer existente debe estar disponible para enviar; Compose no lo simula ni lo despliega.

## API de envío y consulta

| Método y ruta | Resultado |
| --- | --- |
| `POST /api/v1/audit/security/log` | Envía el evento al producer mediante WebClient |
| `GET /api/v1/audit/health` | Ping real a MongoDB: 200 o 503 |
| `GET /api/v1/audit/events` | Página de eventos |
| `GET /api/v1/audit/events/{uid}` | Documento completo; 404 si no existe |
| `GET /api/v1/audit/summary` | Total, aplicaciones, alta prioridad y eventos de hoy |
| `GET /api/v1/audit/analytics` | Indicadores y series completas por día, aplicación, prioridad, acción y hora |
| `GET /api/v1/audit/catalogs` | Aplicaciones y prioridades presentes |

Ejemplo:

```text
/api/v1/audit/events?application=TAPP&priority=HIGH&q=TXN&page=0&size=10&sort=timestamp&direction=desc
```

`from` y `to` aceptan ISO 8601 con zona, por ejemplo `2026-11-13T00:00:00-05:00`; codifica `+` si utilizas offsets positivos. `to` es exclusivo. `page` empieza en 0, `size` admite 1–100 y `sort` admite `timestamp`, `account`, `application` o `priority`. Los valores de prioridad se preservan; «alta prioridad» cuenta `HIGH` y `CRITICAL`.

`analytics` acepta los mismos filtros `q`, `application`, `priority`, `from` y `to`, y agrega todos los documentos coincidentes, independientemente de la paginación. Las series temporales utilizan `AUDIT_TIMEZONE` (`America/Lima` por defecto). Los eventos sin una fecha BSON válida se cuentan como `undated` y se indican en la interfaz; no se les inventa una fecha. Las categorías ausentes aparecen como «Sin dato».

Las entradas inválidas devuelven 400. Los fallos MongoDB devuelven 503 con un mensaje sin URI, credenciales ni detalles internos. Las consultas de datos tienen un límite de ejecución de cinco segundos. La URI de ejemplo limita también la selección de servidor y la conexión. No se acepta un filtro MongoDB arbitrario enviado desde el navegador.

Para colecciones grandes, un DBA puede aplicar manualmente `scripts/create-query-indexes.js`, ajustando la colección si corresponde. Si ya existe un índice equivalente, conserva el del consumer y omite esa instrucción. La búsqueda libre usa una expresión literal escapada; no es una búsqueda Atlas Search y puede requerir acotar el intervalo en colecciones extensas.

## Pruebas

```powershell
.\gradlew.bat test
```

Las pruebas de dominio verifican límites, ordenamiento permitido y fechas. Para ejecutar también las pruebas HTTP de integración con MongoDB real:

```powershell
$env:MONGODB_TEST_URI = 'mongodb://localhost:27017/?serverSelectionTimeoutMS=3000'
.\gradlew.bat test --rerun-tasks
```

La suite MongoDB crea una base temporal `audit_query_test_<UUID>`, carga documentos sintéticos y prueba filtros, fechas, orden, paginación, resumen, estadísticas, detalle dinámico, catálogos, errores y servicio de Angular; finalmente elimina solo su propia base. Las estadísticas se comprueban sobre el conjunto completo y con filtros combinados, agrupación horaria de Lima, categorías ausentes y fechas inválidas. La conexión de test necesita permiso para crear y borrar esa base temporal. Si `MONGODB_TEST_URI` no está definida, esas pruebas se omiten explícitamente. Reporte: `build/reports/tests/test/index.html`.

Las 188 pruebas de integración del POST usan un producer HTTP simulado en un puerto aleatorio, sin enviar datos a una API real. Incluyen los casos recuperados de la POC y comprueban JSON completo, veinte detalles, cabeceras, fechas, validación, estados HTTP, fallos de conexión y timeouts. Las nueve pruebas de arquitectura comprueban las dependencias del backend completo.

`build` ejecuta también Checkstyle y PMD. Estos mantienen el alcance del código de envío original, sus pruebas y los contratos compartidos; analizan además el cargador demo. La cobertura unitaria y las pruebas de arquitectura abarcan todo el backend. Los reportes de análisis están en `build/reports/checkstyle/` y `build/reports/pmd/`.

La compilación de producción de Angular verifica TypeScript y las plantillas con modo estricto. La UI no utiliza datos de ejemplo como sustituto silencioso de una conexión fallida.

### Cobertura de pruebas unitarias

Desde la raíz del repositorio:

```powershell
.\gradlew.bat unitTestCoverageVerification -PskipFrontend
```

JaCoCo **0.8.16** mide únicamente la tarea `unitTest` del proyecto. Las suites `*IntegrationTest` y `*ArchitectureTest` quedan fuera de esa medición. Las 175 pruebas unitarias usan dobles en memoria; no requieren MongoDB ni un producer HTTP real.

| Métrica del backend | Cubierto | Cobertura |
| --- | --- | --- |
| Líneas | 691 de 691 | **100%** |
| Ramas | 230 de 230 | **100%** |
| Métodos medidos | 158 de 158 | **100%** |
| Clases medidas | 43 de 43 | **100%** |

La medición incluye todas las clases de producción, las configuraciones, el punto de entrada y los mappers generados por MapStruct, sin exclusiones de clases de producción. El cargador demo pertenece a un source set separado del código de producción y no forma parte del JAR. Este reporte corresponde al backend Java; Angular se valida mediante su compilación de producción.

`unitTestCoverageVerification` exige al menos **99% de líneas y 99% de ramas** y forma parte de `check` y `build`: una regresión por debajo de cualquiera de esos límites falla la compilación. No utiliza los archivos de cobertura de la tarea `test`, que también ejecuta integración.

Reportes generados:

- HTML: `build/reports/jacoco/unitTest/html/index.html`.
- XML: `build/reports/jacoco/unitTest/unitTestCoverage.xml`.
- CSV: `build/reports/jacoco/unitTest/unitTestCoverage.csv`.

La suite completa contiene 389 casos: 175 unitarios, 205 de integración y nueve de arquitectura. Se retiraron las pruebas de los adaptadores y arranques duplicados al consolidar los proyectos. `build` ejecuta esa suite y además la tarea unitaria separada para verificar su cobertura.

### Verificación realizada en esta entrega

- `build` completo: instalación reproducible, compilación de Angular y empaquetado del JAR correctos.
- Consulta: 8 pruebas de validación de búsqueda y 17 HTTP contra MongoDB 8.0 real, además de las unitarias de servicio, adaptador MongoDB, controladores y errores. Se utiliza una instancia de prueba en el puerto 27028, separada del MongoDB existente en 27017.
- Envío: 188 pruebas HTTP de integración del POST y nueve de arquitectura sobre el proyecto unificado. La suite completa pasó sus 389 casos sin fallos ni omisiones; Checkstyle y PMD también pasaron en su alcance documentado.
- Cobertura unitaria: 175 casos, 100% de líneas y ramas y umbral obligatorio de 99% en ambas métricas, sin incorporar los resultados de integración.
- JAR empaquetado: Angular, conexión MongoDB, estadísticas y detalle comprobados junto con un POST a un producer simulado. Se conservaron el HTTP 202, los veinte detalles y las cuatro cabeceras, y el POST no escribió directamente en MongoDB.
- Navegador: consulta por aplicación, detalle, payload dinámico, resultados vacíos, paginación, filtro de hoy y descarga JSON comprobados. Sin errores de JavaScript durante estas comprobaciones.
- Dashboard: gráficos por aplicación, prioridad y día comprobados en el navegador, incluidos filtros combinados. La exportación de estadísticas conserva el total y las sumas de las series de los 36 eventos sintéticos.
- Detalle: botón textual y apertura con teclado comprobados, con una sola ventana por activación. Los seis campos del formulario son de solo lectura y las cuatro filas de la tabla coinciden con la lista del documento JSON sintético.
- Indisponibilidad: al detener la instancia de prueba, `/health` y `/events` devolvieron 503 con un error controlado.
- `docker compose config --quiet` correcto. El arranque y la construcción de las imágenes no se ejecutaron porque el daemon Docker local estaba detenido.

## Estructura

```text
build.gradle                       Único proyecto Gradle
settings.gradle                    Solo declara el proyecto raíz
frontend/                          Angular standalone + Material + signals
config/                            Reglas de Checkstyle y PMD
gradle/                            Wrapper, análisis estático y cobertura unitaria
examples/                          JSON original y cliente HTTP del POST
postman/                           Envío, consulta, detalle y estadísticas
src/main/java/.../domain/           Modelos de consulta y envío
src/main/java/.../application/      Puertos, casos de uso y servicios
src/main/java/.../adapter/common/   Contratos compartidos de los adaptadores
src/main/java/.../adapter/in/       Consulta y envío HTTP, DTOs, mappers y errores
src/main/java/.../adapter/out/      MongoDB y cliente HTTP del producer
src/main/java/.../bootstrap/        Configuración de consulta, envío y reloj
src/demo/java/                     Carga sintética separada
src/test/java/                     Pruebas unitarias, integración y arquitectura
```

### Clases del backend

Paquete `pe.com.scotiabank.tapp.audit`, en `src/main/java/pe/com/scotiabank/tapp/audit/`:

```text
AuditApplication.java
adapter/
  common/
    error/ApiError.java
    http/AuditHttpHeaderConstants.java
    mapper/StrictMapperConfig.java
  in/web/
    AuditQueryController.java
    AuditSubmissionController.java
    dto/
      AuditLogRequest.java
      AuditLogResponse.java
    error/
      QueryExceptionHandler.java
      ApiExceptionHandler.java
    mapper/AuditWebMapper.java
  out/http/
    AuditApiClient.java
    config/
      AuditApiProperties.java
      AuditClientConfiguration.java
    dto/
      AuditApiRequest.java
      AuditApiResponse.java
    mapper/AuditApiMapper.java
  out/mongo/MongoAuditQueryAdapter.java
application/
  exception/AuditDeliveryException.java
  model/AuditSubmission.java
  port/in/
    AuditQueryUseCase.java
    SubmitAuditUseCase.java
  port/out/
    AuditQueryPort.java
    SendAuditPort.java
  service/
    AuditQueryService.java
    SubmitAuditService.java
bootstrap/
  ApplicationConfiguration.java
  QueryConfiguration.java
domain/
  AuditAnalytics.java
  AuditCatalogs.java
  AuditEvent.java
  AuditPage.java
  AuditSearch.java
  AuditSummary.java
  model/
    AuditContext.java
    AuditEvent.java
    AuditPayloadCopyUtils.java
    AuditResult.java
```

El inventario contiene 38 archivos Java de producción en el mismo `src`, una herramienta de carga demo y 20 archivos Java de pruebas. `AuditSubmissionController` importa directamente `pe.com.scotiabank.tapp.audit.adapter.common.http.AuditHttpHeaderConstants`, disponible en ese mismo árbol de fuentes.

El controlador de consulta depende de `AuditQueryUseCase`, cuyo servicio utiliza `AuditQueryPort`, implementado por el adaptador MongoDB. El controlador de envío depende de `SubmitAuditUseCase`, cuyo servicio utiliza `SendAuditPort`, implementado por `AuditApiClient`. Estas capas son paquetes dentro del mismo proyecto.

`AuditWebMapperImpl` y `AuditApiMapperImpl` los genera MapStruct en `build/generated/sources/annotationProcessor/java/main/` al ejecutar `compileJava`. Se incluyen junto a las demás clases en `BOOT-INF/classes/` del JAR.

En IntelliJ, abre la carpeta que contiene este `settings.gradle`, selecciona JDK 25 como Gradle JVM y recarga el proyecto Gradle. Ejecuta `AuditApplication` para iniciar consulta, envío y la interfaz compilada. Para generar los mappers:

```powershell
.\gradlew.bat compileJava
```

## Referencias

- [Compatibilidad de Angular](https://angular.dev/reference/versions).
- [Angular Material](https://material.angular.dev/).
- [MongoDB con Spring Boot 4.0](https://docs.spring.io/spring-boot/4.0/reference/data/nosql.html#data.nosql.mongodb).
- [Spring WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html).
- [Adaptar llamadas bloqueantes con Reactor](https://projectreactor.io/docs/core/release/reference/faq.html#faq.wrap-blocking).
- Contrato de referencia en la POC original, externa a este repositorio: `bjf7-lineage-consumer/src/test/resources/mongo/expected-tapp-document.json`.
