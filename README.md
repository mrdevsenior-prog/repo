# Audit Query Monolith

Consulta de los eventos que la POC de auditoría almacena en MongoDB. Angular y Angular Material se empaquetan dentro del JAR de Spring Boot: una aplicación, un puerto y un despliegue. El navegador consume `/api/v1/audit`; las credenciales MongoDB permanecen en el backend.

Versiones estables verificadas en npm el 13 de noviembre de 2026: Angular **22.2.6**, Angular Material/CDK **22.2.7**, CLI/build **22.2.7** y TypeScript **6.0.x**, fijadas mediante `package-lock.json`. Backend: Java **25**, Spring Boot **4.0.9** y Gradle Wrapper **9.1.0**, siguiendo la POC existente.

## Funcionalidad

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

La aplicación es de solo lectura. No contiene POST al producer ni consume Kafka; los eventos siguen entrando por el flujo existente. No crea índices, colecciones ni datos al iniciar. No activa TTL ni cambia la retención del consumer.

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

.\gradlew.bat build
java -jar build/libs/audit-query-monolith-1.0.0.jar
```

Abre **http://localhost:8080**. Gradle ejecuta `npm ci`, compila Angular y copia sus archivos al JAR. En Linux/macOS utiliza `./gradlew` y la sintaxis de variables de tu shell. `npmExecutable` permite seleccionar otro ejecutable npm: `-PnpmExecutable=/ruta/a/npm`.

El JAR ya compilado requiere únicamente Java 25 y acceso a MongoDB; Node y Gradle se utilizan al compilar. `.env.example` documenta las variables, pero Spring Boot no carga archivos `.env` automáticamente.

El repositorio contiene el código fuente del frontend y del backend. Tras compilar con `build`, ejecuta el JAR generado:

```powershell
java -jar build/libs/audit-query-monolith-1.0.0.jar
```

Para MongoDB autenticado, establece `MONGODB_URI` en el entorno de ejecución y utiliza una cuenta con rol `read` sobre la base elegida. `MONGODB_DATABASE` debe indicar explícitamente la base correspondiente. No incluyas credenciales en Angular ni en el repositorio. En producción integra la autenticación corporativa en el backend o en el gateway antes de ampliar el acceso; la POC escucha en loopback por defecto.

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

## API de consulta

| Método y ruta | Resultado |
| --- | --- |
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

La suite crea una base temporal `audit_query_test_<UUID>`, carga documentos sintéticos y prueba filtros, fechas, orden, paginación, resumen, estadísticas, detalle dinámico, catálogos, errores y servicio de Angular; finalmente elimina solo su propia base. Las estadísticas se comprueban sobre el conjunto completo y con filtros combinados, agrupación horaria de Lima, categorías ausentes y fechas inválidas. La conexión de test necesita permiso para crear y borrar esa base temporal. Si `MONGODB_TEST_URI` no está definida, esas pruebas se omiten explícitamente. Reporte: `build/reports/tests/test/index.html`.

La compilación de producción de Angular verifica TypeScript y las plantillas con modo estricto. La UI no utiliza datos de ejemplo como sustituto silencioso de una conexión fallida.

### Verificación realizada en esta entrega

- `build` completo: instalación reproducible, compilación de Angular y empaquetado del JAR correctos.
- 24 ejecuciones de prueba: 7 de dominio y 17 HTTP contra MongoDB 8.0 real, sin fallos ni omisiones. Se utilizó una instancia de prueba en el puerto 27028, separada del MongoDB existente en 27017.
- Navegador: consulta por aplicación, detalle, payload dinámico, resultados vacíos, paginación, filtro de hoy y descarga JSON comprobados. Sin errores de JavaScript durante estas comprobaciones.
- Dashboard: gráficos por aplicación, prioridad y día comprobados en el navegador, incluidos filtros combinados. La exportación de estadísticas conserva el total y las sumas de las series de los 36 eventos sintéticos.
- Detalle: botón textual y apertura con teclado comprobados, con una sola ventana por activación. Los seis campos del formulario son de solo lectura y las cuatro filas de la tabla coinciden con la lista del documento JSON sintético.
- Indisponibilidad: al detener la instancia de prueba, `/health` y `/events` devolvieron 503 con un error controlado.
- `docker compose config --quiet` correcto. El arranque y la construcción de las imágenes no se ejecutaron porque el daemon Docker local estaba detenido.

## Estructura

```text
frontend/                          Angular standalone + Material + signals
src/main/java/.../domain/           Modelos y puerto de consulta, Java puro
src/main/java/.../application/      Caso de uso, Java puro
src/main/java/.../adapter/mongo/    Lectura MongoDB y conversión BSON
src/main/java/.../adapter/web/      Controlador REST y errores
src/main/java/.../config/           Cableado y reloj
src/demo/java/                     Carga sintética separada
src/test/java/                     Pruebas de dominio e integración
```

## Referencias

- [Compatibilidad de Angular](https://angular.dev/reference/versions).
- [Angular Material](https://material.angular.dev/).
- [MongoDB con Spring Boot 4.0](https://docs.spring.io/spring-boot/4.0/reference/data/nosql.html#data.nosql.mongodb).
- Contrato de referencia en la POC original, externa a este repositorio: `bjf7-lineage-consumer/src/test/resources/mongo/expected-tapp-document.json`.
