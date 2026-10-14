package com.shoplab.api.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplab.api.support.ContractAssertions;
import com.shoplab.api.support.IntegrationTest;
import com.shoplab.api.support.SeedData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escenarios de specs/orders de punta a punta, con tokens del login real.
 * Cada test devuelve órdenes y stock a la semilla (design D8): ProductCatalogIT y SeedDataIT dependen de ello.
 */
class OrdersIT extends IntegrationTest {

    static final String URL = "/api/v1/orders";
    static final ObjectMapper MAPPER = new ObjectMapper();
    static final String SHIPPING = """
            {"address":"Calle 10 #20-30","city":"Bogotá","postalCode":"110111","country":"CO"}""";

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void restoreSeed() {
        SeedData.restoreOrdersAndStock(jdbc);
    }

    // ---------- helpers ----------

    ResponseEntity<String> exchange(HttpMethod method, String path, String json, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (json != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(json, headers), String.class);
    }

    String login(String email, String password) throws Exception {
        ResponseEntity<String> login = exchange(HttpMethod.POST, "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json(login).get("accessToken").asText();
    }

    String standardToken() throws Exception {
        return login("standard@shoplab.dev", "Secret_123");
    }

    static String order(String itemsJson) {
        return "{\"items\":" + itemsJson + ",\"paymentMethod\":\"CARD\",\"shipping\":" + SHIPPING + "}";
    }

    ResponseEntity<String> createOrder(String itemsJson, String token) {
        return exchange(HttpMethod.POST, URL, order(itemsJson), token);
    }

    static JsonNode json(ResponseEntity<String> response) throws Exception {
        return MAPPER.readTree(response.getBody());
    }

    Integer stockOf(long productId) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    }

    Integer ordersCount() {
        return jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class);
    }

    static void assertError(ResponseEntity<String> response, HttpStatus status, String code, String message) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(status);
        ContractAssertions.assertMatchesSchema(response.getBody(), "error-response");
        assertThat(json(response).get("code").asText()).isEqualTo(code);
        if (message != null) {
            assertThat(json(response).get("message").asText()).isEqualTo(message);
        }
    }

    // ---------- crear ----------

    @Nested
    class Create {

        @Test
        void orderIsCreatedWithCatalogPricesAndTotals() throws Exception {
            ResponseEntity<String> response = createOrder(
                    "[{\"productId\":1,\"quantity\":2},{\"productId\":4,\"quantity\":1}]", standardToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            ContractAssertions.assertMatchesSchema(response.getBody(), "order-response");
            JsonNode body = json(response);
            assertThat(body.get("status").asText()).isEqualTo("CONFIRMED");
            assertThat(body.has("userId")).isFalse();
            assertThat(body.get("items").get(0).get("productId").asLong()).isEqualTo(1);
            assertThat(body.get("items").get(0).get("sku").asText()).isEqualTo("SL-HEAD-001");
            assertThat(body.get("items").get(0).get("subtotal").decimalValue()).isEqualByComparingTo("119.98");
            assertThat(body.get("items").get(1).get("productId").asLong()).isEqualTo(4);
            assertThat(body.get("items").get(1).get("subtotal").decimalValue()).isEqualByComparingTo("25.00");
            assertThat(body.get("total").decimalValue()).isEqualByComparingTo("144.98");
            assertThat(body.get("shipping")).isEqualTo(MAPPER.readTree(SHIPPING));
        }

        @Test
        void stockIsDecreased() throws Exception {
            createOrder("[{\"productId\":1,\"quantity\":2}]", standardToken());

            assertThat(json(exchange(HttpMethod.GET, "/api/v1/products/1", null, null)).get("stock").asInt()).isEqualTo(8);
        }

        @Test
        void pricesSentByTheClientAreIgnored() throws Exception {
            String json = "{\"items\":[{\"productId\":1,\"quantity\":1,\"unitPrice\":0.01}],\"total\":1,"
                    + "\"paymentMethod\":\"CASH\",\"shipping\":" + SHIPPING + "}";

            JsonNode body = json(exchange(HttpMethod.POST, URL, json, standardToken()));

            assertThat(body.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo(new BigDecimal("59.99"));
            assertThat(body.get("total").decimalValue()).isEqualByComparingTo(new BigDecimal("59.99"));
        }

        @Test
        void soldOutProductIs409() throws Exception {
            assertError(createOrder("[{\"productId\":3,\"quantity\":1}]", standardToken()),
                    HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Insufficient stock for product 3");
        }

        @Test
        void moreUnitsThanStockIs409() throws Exception {
            assertError(createOrder("[{\"productId\":5,\"quantity\":2}]", standardToken()),
                    HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Insufficient stock for product 5");
            assertThat(stockOf(5)).isEqualTo(1);
        }

        @Test
        void failingItemRollsBackPreviousStockDecrease() throws Exception {
            int ordersBefore = ordersCount();

            assertError(createOrder("[{\"productId\":1,\"quantity\":1},{\"productId\":3,\"quantity\":1}]", standardToken()),
                    HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", null);

            assertThat(stockOf(1)).isEqualTo(10);
            assertThat(ordersCount()).isEqualTo(ordersBefore);
        }

        @Test
        void unknownProductIs404() throws Exception {
            assertError(createOrder("[{\"productId\":99,\"quantity\":1}]", standardToken()),
                    HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product 99 not found");
        }

        @Test
        void emptyItemsIsEmptyOrder() throws Exception {
            ResponseEntity<String> response = createOrder("[]", standardToken());

            assertError(response, HttpStatus.BAD_REQUEST, "EMPTY_ORDER", "Order must contain at least one item");
            assertThat(json(response).get("fieldErrors")).isEmpty();
        }

        @Test
        void repeatedProductIsDuplicate() throws Exception {
            assertError(createOrder("[{\"productId\":1,\"quantity\":1},{\"productId\":4,\"quantity\":1},{\"productId\":1,\"quantity\":2}]",
                            standardToken()),
                    HttpStatus.BAD_REQUEST, "DUPLICATE_ORDER_ITEM", "Product 1 appears more than once in the order");
        }

        @Test
        void invalidQuantityIsValidationError() throws Exception {
            ResponseEntity<String> response = createOrder("[{\"productId\":1,\"quantity\":0}]", standardToken());

            assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request has invalid fields");
            assertThat(json(response).get("fieldErrors").get(0).get("field").asText()).isEqualTo("items[0].quantity");
        }
    }

    // ---------- usuario del token borrado (decisión del usuario: 401 en las tres rutas) ----------

    @Nested
    class DeletedUser {

        String tokenOfDeletedUser() throws Exception {
            exchange(HttpMethod.POST, "/api/v1/users", """
                    {"firstName":"Ana","lastName":"Gomez","email":"orders@registration.it","password":"Secret_456",
                     "birthDate":"1990-05-20","country":"CO","plan":"BASIC","experienceLevel":3,"termsAccepted":true}
                    """, null);
            String token = login("orders@registration.it", "Secret_456");
            jdbc.update("DELETE FROM users WHERE email = 'orders@registration.it'");
            return token;
        }

        @Test
        void cannotCreateAndStockIsUntouched() throws Exception {
            assertError(createOrder("[{\"productId\":1,\"quantity\":1}]", tokenOfDeletedUser()),
                    HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "Authentication token is invalid");
            assertThat(stockOf(1)).isEqualTo(10);
        }

        @Test
        void cannotListOrders() throws Exception {
            assertError(exchange(HttpMethod.GET, URL, null, tokenOfDeletedUser()),
                    HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", null);
        }

        @Test
        void cannotReadAnOrder() throws Exception {
            assertError(exchange(HttpMethod.GET, URL + "/1", null, tokenOfDeletedUser()),
                    HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", null);
        }
    }

    // ---------- consultar ----------

    @Nested
    class History {

        @Test
        void buyerSeesSeedOrdersNewestFirst() throws Exception {
            ResponseEntity<String> response = exchange(HttpMethod.GET, URL, null, login("buyer@shoplab.dev", "Secret_123"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ContractAssertions.assertMatchesSchema(response.getBody(), "order-list");
            JsonNode orders = json(response);
            assertThat(orders).hasSize(2);
            assertThat(orders.get(0).get("id").asLong()).isEqualTo(2);
            assertThat(orders.get(0).get("paymentMethod").asText()).isEqualTo("CASH");
            assertThat(orders.get(0).get("total").decimalValue()).isEqualByComparingTo("45.00");
            assertThat(orders.get(1).get("id").asLong()).isEqualTo(1);
            assertThat(orders.get(1).get("total").decimalValue()).isEqualByComparingTo("109.99");
        }

        @Test
        void userWithoutOrdersGetsEmptyList() throws Exception {
            ResponseEntity<String> response = exchange(HttpMethod.GET, URL, null, standardToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(json(response)).isEmpty();
        }

        @Test
        void newOrderComesFirst() throws Exception {
            String buyer = login("buyer@shoplab.dev", "Secret_123");
            long newId = json(createOrder("[{\"productId\":2,\"quantity\":1}]", buyer)).get("id").asLong();

            JsonNode orders = json(exchange(HttpMethod.GET, URL, null, buyer));

            assertThat(orders).hasSize(3);
            assertThat(orders.get(0).get("id").asLong()).isEqualTo(newId);
        }

        @Test
        void createdOrderReadsBackTheSame() throws Exception {
            String token = standardToken();
            ResponseEntity<String> created = createOrder("[{\"productId\":6,\"quantity\":2}]", token);
            long id = json(created).get("id").asLong();

            ResponseEntity<String> read = exchange(HttpMethod.GET, URL + "/" + id, null, token);

            assertThat(json(read)).isEqualTo(json(created));
        }

        @Test
        void buyerSeesSeedOrderDetailWithSubtotals() throws Exception {
            ResponseEntity<String> response = exchange(HttpMethod.GET, URL + "/1", null, login("buyer@shoplab.dev", "Secret_123"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ContractAssertions.assertMatchesSchema(response.getBody(), "order-response");
            JsonNode items = json(response).get("items");
            assertThat(items.get(0).get("productId").asLong()).isEqualTo(1);
            assertThat(items.get(0).get("subtotal").decimalValue()).isEqualByComparingTo("59.99");
            assertThat(items.get(1).get("productId").asLong()).isEqualTo(4);
            assertThat(items.get(1).get("quantity").asInt()).isEqualTo(2);
            assertThat(items.get(1).get("subtotal").decimalValue()).isEqualByComparingTo("50.00");
        }

        @Test
        void otherUsersOrderIs404() throws Exception {
            assertError(exchange(HttpMethod.GET, URL + "/1", null, standardToken()),
                    HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order 1 not found");
        }

        @Test
        void unknownOrderIs404() throws Exception {
            assertError(exchange(HttpMethod.GET, URL + "/999", null, standardToken()),
                    HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order 999 not found");
        }
    }

    // ---------- concurrencia ----------

    @Test
    void twoSimultaneousBuyersOfTheLastUnitCannotBothWin() throws Exception {
        // Producto 5: stock 1. Dos compradores distintos piden 1 unidad a la vez (design D3)
        String standard = standardToken();
        String buyer = login("buyer@shoplab.dev", "Secret_123");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ResponseEntity<String>>> futures = List.of(standard, buyer).stream()
                    .map(token -> pool.submit(() -> {
                        start.await();   // los dos hilos salen a la vez
                        return createOrder("[{\"productId\":5,\"quantity\":1}]", token);
                    }))
                    .toList();
            start.countDown();

            List<HttpStatus> statuses = new java.util.ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                statuses.add((HttpStatus) future.get(30, TimeUnit.SECONDS).getStatusCode());
            }

            // No importa quién gana: exactamente una 201 y una 409, y el stock nunca queda negativo
            assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
            assertThat(stockOf(5)).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- rutas ----------

    @Test
    void withoutTokenIs401() throws Exception {
        assertError(exchange(HttpMethod.POST, URL, order("[{\"productId\":1,\"quantity\":1}]"), null),
                HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_MISSING", null);
        assertError(exchange(HttpMethod.GET, URL, null, null), HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_MISSING", null);
        assertThat(stockOf(1)).isEqualTo(10);
    }

    @Test
    void putWithTokenIs405() throws Exception {
        assertError(exchange(HttpMethod.PUT, URL, "{}", standardToken()),
                HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", null);
    }
}
