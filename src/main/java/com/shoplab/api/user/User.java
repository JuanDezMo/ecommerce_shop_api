package com.shoplab.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Usuario registrado (tabla users).
 * Lo construye UserServiceImpl campo a campo con los setters (design D4): el constructor vacío es protected,
 * así que solo el paquete user puede crear usuarios.
 * No sobrescribe equals, hashCode ni toString (§11, regla 10): así el hash nunca aparece en un log por accidente.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(length = 15)
    private String phone;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, length = 2)
    private String country;

    @Enumerated(EnumType.STRING)   // §11, regla 7
    @Column(nullable = false, length = 10)
    private Plan plan;

    // En la BD es smallint (1 a 5); en Java se usa Integer para no mezclar Short en DTOs y tests
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "experience_level", nullable = false)
    private Integer experienceLevel;

    @Column(nullable = false)
    private Boolean newsletter;

    @Column(name = "terms_accepted_at", nullable = false)
    private Instant termsAcceptedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
        // lo exige JPA; UserServiceImpl (mismo paquete) también lo usa para crear usuarios
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public Integer getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(Integer experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public Boolean getNewsletter() {
        return newsletter;
    }

    public void setNewsletter(Boolean newsletter) {
        this.newsletter = newsletter;
    }

    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void setTermsAcceptedAt(Instant termsAcceptedAt) {
        this.termsAcceptedAt = termsAcceptedAt;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
