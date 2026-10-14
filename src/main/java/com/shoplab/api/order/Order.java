package com.shoplab.api.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orden de compra (tabla orders), siguiendo las reglas de JPA de §11.
 * La dirección de envío se copia aquí (snapshot) en lugar de tener una tabla de direcciones (§6.3).
 * La construye OrderServiceImpl con los setters; sin equals, hashCode ni toString (§11, regla 10).
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Solo el id, no un @ManyToOne User: así órdenes no se acopla a la entidad de usuarios (§11, regla 3)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)   // §11, regla 7
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, precision = 12, scale = 2)   // §11, regla 8
    private BigDecimal total;

    @Column(name = "shipping_address", nullable = false, length = 120)
    private String shippingAddress;

    @Column(name = "shipping_city", nullable = false, length = 60)
    private String shippingCity;

    @Column(name = "shipping_postal_code", nullable = false, length = 10)
    private String shippingPostalCode;

    @Column(name = "shipping_country", nullable = false, length = 2)
    private String shippingCountry;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // cascade ALL: guardar la orden guarda sus ítems. @OrderBy("id") = orden de inserción = orden del request
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)   // §11, regla 4
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // lo exige JPA; OrderServiceImpl (mismo paquete) también lo usa para crear órdenes
    }

    /** Añade un ítem y mantiene los dos lados de la relación sincronizados. */
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getShippingCity() {
        return shippingCity;
    }

    public void setShippingCity(String shippingCity) {
        this.shippingCity = shippingCity;
    }

    public String getShippingPostalCode() {
        return shippingPostalCode;
    }

    public void setShippingPostalCode(String shippingPostalCode) {
        this.shippingPostalCode = shippingPostalCode;
    }

    public String getShippingCountry() {
        return shippingCountry;
    }

    public void setShippingCountry(String shippingCountry) {
        this.shippingCountry = shippingCountry;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
