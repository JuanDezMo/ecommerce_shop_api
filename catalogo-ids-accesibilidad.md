# Catálogo de IDs de accesibilidad (iOS y Android)

Actualizado: 2026-10-14

Fuente única de verdad de los identificadores y de los textos de alertas que usan Appium y Maestro. Las dos apps usan **exactamente estos valores**: en iOS con `.accessibilityIdentifier(...)`, en Android con `Modifier.testTag(...)` y `testTagsAsResourceId = true`. Un ID nuevo se añade aquí primero y después en las apps.

Complementa la [guía de integración del backend](guia-integracion-ios-android.md).

## Reglas

| Regla | Ejemplo |
| --- | --- |
| Formato `<pantalla>_<elemento>_<tipo>`, en `snake_case`, solo minúsculas, dígitos y `_` | `login_email_input` |
| Elementos de una lista: se identifican con el **id del backend**, nunca con la posición | `catalog_item_4`, `orders_item_2` |
| Partes de un elemento de lista: el ID del elemento como prefijo | `cart_item_4_quantity_stepper` |
| Errores de un campo: `<pantalla>_<campo>_error_text`. Solo existen mientras el error se ve | `register_email_error_text` |
| Imágenes de producto: `product_image_<imageKey>`, dentro del contenedor de su elemento | `product_image_headphones` |
| Un ID aparece una sola vez por pantalla | — |
| Botones de alertas y diálogos: `<pantalla>_<alerta>_alert_<acción>_button` (o `_dialog_`) | `checkout_place_order_alert_confirm_button` |

Sufijos de tipo: `input`, `button`, `link`, `text`, `image`, `picker`, `dropdown`, `segmented`, `slider`, `switch`, `checkbox`, `stepper`, `list`, `item`, `banner`, `indicator`.

Se usan los ids del backend porque los datos semilla los fijan (productos 1 a 6, órdenes 1 y 2 de `buyer`) y vuelven a ser los mismos tras `POST /api/v1/test/reset`. La posición cambia al ordenar, borrar o añadir elementos.

## Comunes

| ID | Elemento | Nota |
| --- | --- | --- |
| `app_loading_indicator` | Indicador de carga a pantalla completa | Solo existe mientras carga |
| `app_snackbar_text` | Mensaje temporal | Desaparece a los 3 s |
| `tab_catalog` | Pestaña Catálogo | En iOS, comprobar en Appium Inspector que el ID se propaga al botón de la pestaña; si no, se localiza por su etiqueta `Catalog` |
| `tab_cart` | Pestaña Carrito | El número del badge se lee en el valor del botón |
| `tab_account` | Pestaña Cuenta | — |

## Login

| ID | Elemento |
| --- | --- |
| `login_email_input` | Campo email |
| `login_password_input` | Campo contraseña |
| `login_submit_button` | Botón "Log in" |
| `login_register_link` | Enlace a registro |
| `login_session_expired_text` | Aviso "Your session has expired…" |
| `login_error_text` | Mensaje de error del login (credenciales, cuenta bloqueada) |

## Registro

| ID | Elemento | Error asociado |
| --- | --- | --- |
| `register_firstname_input` | Nombre | `register_firstname_error_text` |
| `register_lastname_input` | Apellido | `register_lastname_error_text` |
| `register_email_input` | Email | `register_email_error_text` |
| `register_password_input` | Contraseña | `register_password_error_text` |
| `register_phone_input` | Teléfono (obligatorio: `+` y 13 dígitos) | `register_phone_error_text` |
| `register_birthdate_day_dropdown` | Fecha de nacimiento: día (menú) | `register_birthdate_error_text` (uno solo, bajo los tres) |
| `register_birthdate_month_dropdown` | Fecha de nacimiento: mes (menú, nombres en inglés) | `register_birthdate_error_text` |
| `register_birthdate_year_dropdown` | Fecha de nacimiento: año (menú, del actual a 1900) | `register_birthdate_error_text` |
| `register_country_dropdown` | País | `register_country_error_text` |
| `register_plan_segmented` | Plan (BASIC / PRO) | `register_plan_error_text` |
| `register_experience_slider` | Nivel de experiencia | `register_experience_error_text` |
| `register_newsletter_switch` | Newsletter | — |
| `register_terms_checkbox` | Aceptar términos | `register_terms_error_text` |
| `register_next_button` | Botón "Next" de la primera pantalla del registro | — |
| `register_submit_button` | Botón "Create account" de la segunda pantalla | — |
| `register_error_summary_text` | Resumen de errores sobre el formulario | — |
| `register_login_link` | Volver al login | — |

## Catálogo

| ID | Elemento |
| --- | --- |
| `catalog_list` | Lista de productos |
| `catalog_item_<productId>` | Fila de un producto (contenedor) |
| `catalog_item_<productId>_name_text` | Nombre |
| `catalog_item_<productId>_price_text` | Precio |
| `catalog_item_<productId>_soldout_text` | Etiqueta "Sold out" (solo con `stock` 0) |
| `catalog_item_<productId>_add_button` | Botón "Add to cart" de la fila; abre la hoja "Add to cart". Deshabilitado si está agotado o si el carrito ya tiene todo su stock. Con 20 unidades en el carrito abre la alerta "Cart is full" |
| `product_image_<imageKey>` | Imagen, dentro de la fila |
| `catalog_error_text` | Error al cargar el catálogo: `We couldn't load the products. Pull to refresh.` (aparece tras cancelar la alerta genérica) |

## Detalle de producto

| ID | Elemento |
| --- | --- |
| `product_detail_name_text` | Nombre |
| `product_detail_description_text` | Descripción |
| `product_detail_price_text` | Precio |
| `product_detail_stock_text` | Unidades disponibles: `10 in stock`, `1 in stock` (con stock 0 se muestra `product_detail_soldout_text`) |
| `product_detail_soldout_text` | Etiqueta "Sold out" |
| `product_image_<imageKey>` | Imagen |
| `product_detail_add_button` | Botón "Add to cart"; abre la hoja "Add to cart". Mismas reglas que el botón de la fila del catálogo |

## Hoja "Add to cart"

Se abre como hoja (sheet) desde el detalle o desde una fila del catálogo. Al confirmar se cierra y aparece el snackbar "Added to cart".

| ID | Elemento |
| --- | --- |
| `add_to_cart_sheet` | Contenedor de la hoja |
| `add_to_cart_name_text` | Nombre del producto |
| `add_to_cart_quantity_stepper` | Cantidad: de 1 hasta el stock que queda (descontando lo que ya está en el carrito), sin pasar de 20 unidades en total en el carrito |
| `add_to_cart_confirm_button` | Botón "Add to cart" |

Nota para Android: el selector de cantidad (`add_to_cart_quantity_stepper`, y el de cada línea del carrito) se construye con dos botones llamados `<id>-Decrement` y `<id>-Increment`, igual que los genera iOS; el valor lleva el ID `<id>`. La insignia del carrito no expone su texto a `uiautomator`: se comprueba a la vista. La hoja y las alertas son ventanas aparte: Android repite `testTagsAsResourceId` en ellas.

## Carrito

| ID | Elemento |
| --- | --- |
| `cart_list` | Lista de productos del carrito |
| `cart_item_<productId>` | Línea de un producto (contenedor) |
| `cart_item_<productId>_name_text` | Nombre |
| `cart_item_<productId>_quantity_stepper` | Cantidad: de 1 hasta el stock, sin pasar de 20 unidades en total en el carrito |
| `cart_item_<productId>_subtotal_text` | Subtotal estimado |
| `cart_item_<productId>_delete_button` | Botón "Delete" (aparece tras deslizar) |
| `cart_total_text` | Total estimado |
| `cart_clear_button` | Vaciar carrito; abre la hoja de acciones "Remove all items…" |
| `cart_checkout_button` | Ir al checkout |
| `cart_empty_text` | Estado vacío |

## Checkout

| ID | Elemento | Error asociado |
| --- | --- | --- |
| `checkout_address_input` | Dirección | `checkout_address_error_text` |
| `checkout_city_input` | Ciudad | `checkout_city_error_text` |
| `checkout_postalcode_input` | Código postal | `checkout_postalcode_error_text` |
| `checkout_country_dropdown` | País | `checkout_country_error_text` |
| `checkout_payment_segmented` | Forma de pago (Card / Cash / Transfer) | `checkout_payment_error_text` |
| `checkout_total_text` | Total estimado | — |
| `checkout_confirm_button` | Botón "Place order"; abre la alerta de confirmación | — |
| `checkout_error_summary_text` | Resumen de errores de campo | — |

## Confirmación

| ID | Elemento |
| --- | --- |
| `confirmation_order_id_text` | Número de orden |
| `confirmation_total_text` | Total cobrado (el del backend) |
| `confirmation_item_<productId>` | Línea de producto |
| `confirmation_view_order_button` | Ver el detalle de la orden |
| `confirmation_done_button` | Volver al catálogo |

## Cuenta

| ID | Elemento |
| --- | --- |
| `account_name_text` | Nombre completo |
| `account_email_text` | Email |
| `account_plan_text` | Plan: `Basic` o `Pro` |
| `account_orders_button` | Ir a "My orders" |
| `account_theme_segmented` | Tema (System / Light / Dark) |
| `account_logout_button` | Cerrar sesión; abre la alerta "Log out?" |

## Mis órdenes

| ID | Elemento |
| --- | --- |
| `orders_list` | Lista de órdenes |
| `orders_item_<orderId>` | Fila de una orden (contenedor) |
| `orders_item_<orderId>_date_text` | Fecha |
| `orders_item_<orderId>_total_text` | Total |
| `orders_item_<orderId>_count_text` | Número de unidades (suma de `quantity`): `1 item`, `3 items` |
| `orders_empty_text` | Estado vacío |

## Detalle de orden

| ID | Elemento |
| --- | --- |
| `order_detail_id_text` | Número de orden |
| `order_detail_status_text` | Estado: `Confirmed` |
| `order_detail_date_text` | Fecha |
| `order_detail_payment_text` | Forma de pago: `Card`, `Cash` o `Transfer` |
| `order_detail_item_<productId>` | Línea de producto (contenedor) |
| `order_detail_item_<productId>_subtotal_text` | Subtotal |
| `order_detail_total_text` | Total |
| `order_detail_shipping_text` | Dirección de envío |

Nota para Android: la dirección de envío (`order_detail_shipping_text`) es un solo texto con cuatro líneas (dirección, ciudad, código postal y país), separadas por saltos de línea. El país se muestra con su nombre en inglés si la app lo ofrece y, si no, con el código que devuelve el backend.

## Alertas y diálogos

Las alertas nativas de iOS **no garantizan** que el ID de sus botones llegue a Appium (se verifica en la fase 0 de la app iOS). Por eso los textos son parte del contrato: iOS localiza las alertas por **título** y sus botones por **texto**. Android usa los IDs (`testTag` en los botones del diálogo), y iOS también los usará si la verificación sale bien.

En Appium, las suites que prueban alertas usan `autoAcceptAlerts` y `autoDismissAlerts` en `false`.

| Alerta / diálogo | Cuándo aparece | Tipo | Título | Mensaje | Botones (texto → ID) |
| --- | --- | --- | --- | --- | --- |
| Cuenta creada | Registro con 201 | Alerta | `Account created` | `You can now log in with your email.` | `Log in` → `register_success_alert_login_button` |
| Vaciar carrito | `cart_clear_button` | Hoja de acciones | `Remove all items from your cart?` | — | `Clear cart` (destructivo) → `cart_clear_dialog_confirm_button` · `Cancel` → `cart_clear_dialog_cancel_button` |
| Confirmar compra | `checkout_confirm_button` | Alerta | `Place order?` | `Estimated total: $144.98.` (el total estimado del carrito) | `Place order` → `checkout_place_order_alert_confirm_button` · `Cancel` → `checkout_place_order_alert_cancel_button` |
| Compra rechazada | 409 `INSUFFICIENT_STOCK`, 404 `PRODUCT_NOT_FOUND`, 400 `EMPTY_ORDER` o `DUPLICATE_ORDER_ITEM`, o un `VALIDATION_ERROR` cuyos errores no corresponden a ningún campo del checkout | Alerta | `Can't place your order` | El mensaje de negocio del `code` (guía de integración). En el caso de `VALIDATION_ERROR`: `We couldn't process your cart. Please try again.` | `Review cart` → `checkout_order_error_alert_review_cart_button` |
| Orden inexistente | 404 `ORDER_NOT_FOUND` | Alerta | `Order not found` | `We couldn't find this order.` | `OK` → `order_detail_not_found_alert_ok_button` |
| Cerrar sesión | `account_logout_button` | Alerta | `Log out?` | `Your cart will be emptied.` | `Log out` (destructivo) → `account_logout_alert_confirm_button` · `Cancel` → `account_logout_alert_cancel_button` |
| Carrito lleno | "Add to cart" con 20 unidades en el carrito | Alerta | `Cart is full` | `Your cart can hold up to 20 items.` | `OK` → `app_cart_full_alert_ok_button` |
| Error genérico | Sin conexión, 5xx, otros 4xx o `FORCED_ERROR`, en cualquier pantalla | Alerta | `Something went wrong` | El mensaje de negocio correspondiente | `Try again` → `app_error_alert_retry_button` · `Cancel` → `app_error_alert_cancel_button` |

**Mensajes temporales y avisos (con ID en las dos plataformas):**

| Texto | Cuándo | ID |
| --- | --- | --- |
| `Added to cart` | Al confirmar la hoja "Add to cart" | `app_snackbar_text` |
| `Your cart was updated to match available stock.` | Al abrir el carrito, si se ajustó alguna cantidad o se quitó un producto agotado | `app_snackbar_text` |
| `Your session has expired. Please log in again.` | Login tras `AUTH_TOKEN_EXPIRED`, `_INVALID` o `_MALFORMED` | `login_session_expired_text` |

**Cómo provocarlas con el perfil `testing`:**
- **Error genérico:** cabecera `X-Force-Error: 503`.
- **Compra rechazada:** comprar el producto 3, o dos veces la última unidad del producto 5.
- **Orden inexistente:** abrir la orden 999.
- **Carrito lleno:** añadir 20 unidades (por ejemplo, 10 del producto 1 y 10 del producto 2) y pulsar otro "Add to cart".
- **Cuenta creada:** registrar un email nuevo tras `POST /api/v1/test/reset`.
