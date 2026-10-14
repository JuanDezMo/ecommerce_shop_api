package com.shoplab.api.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Producto del catálogo (tabla products).
 * Solo tiene getters: en este change nadie modifica un producto (design D2).
 * No sobrescribe equals, hashCode ni toString (§11, regla 10).
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String sku;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String description;

    // Monto en USD (§11, regla 8)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Column(name = "image_key", nullable = false, length = 50)
    private String imageKey;

    // Se mapea para representar la tabla completa, pero no se expone en la API
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Product() {
        // lo exige JPA
    }

    public Product(String sku, String name, String description, BigDecimal price, Integer stock,
                   String imageKey, Instant createdAt) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.imageKey = imageKey;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public String getImageKey() {
        return imageKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
