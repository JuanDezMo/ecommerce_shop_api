package com.shoplab.api;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

import java.util.Optional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

/**
 * Reglas de comunicación entre capas (§3.3 del documento de arquitectura y design D2 del change bootstrap).
 * Si una regla falla, el mensaje indica la clase y la dependencia que la rompen.
 * allowEmptyShould(true): mientras no existan clases que cumplan el patrón, la regla pasa sin comprobar nada.
 */
@AnalyzeClasses(packages = "com.shoplab.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersOnlyTalkToServiceInterfaces =
            noClasses().that().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                    .orShould().dependOnClassesThat().haveSimpleNameEndingWith("ServiceImpl")
                    .because("un controller solo habla con la interfaz del service (§3.3)")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule entitiesNeverLeaveTheService =
            noClasses().that().haveSimpleNameEndingWith("Controller")
                    .or().resideInAPackage("..dto..")
                    .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                    .because("las entidades no salen del service: la API solo expone DTOs (§3.3)")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule repositoriesDoNotKnowServices =
            noClasses().that().haveSimpleNameEndingWith("Repository")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Service")
                    .orShould().dependOnClassesThat().haveSimpleNameEndingWith("ServiceImpl")
                    .because("las dependencias van siempre hacia abajo (§3.3)")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule commonDoesNotDependOnFeatures =
            noClasses().that().resideInAPackage("com.shoplab.api.common..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.shoplab.api.auth..", "com.shoplab.api.user..", "com.shoplab.api.product..",
                            "com.shoplab.api.order..", "com.shoplab.api.security..", "com.shoplab.api.config..",
                            "com.shoplab.api.testsupport..")
                    .because("common es compartido y no puede conocer a ninguna feature")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule constructorInjectionOnly =
            NO_CLASSES_SHOULD_USE_FIELD_INJECTION
                    .because("la inyección es por constructor (§2.1), así las clases se prueban sin Spring");

    @ArchTest
    static final ArchRule transactionsOnlyInServices =
            methods().that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                    .should().notBeAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                    .andShould().notBeAnnotatedWith(jakarta.transaction.Transactional.class)
                    .because("las transacciones empiezan y terminan en el ServiceImpl (design D1)")
                    .allowEmptyShould(true);

    // Reglas que necesitan al menos un ServiceImpl real (change product-catalog, design D6).
    // allowEmptyShould(false): si un cambio de nombre las deja sin clases, fallan en vez de pasar en silencio.

    @ArchTest
    static final ArchRule serviceImplsAreTransactional =
            classes().that().haveSimpleNameEndingWith("ServiceImpl")
                    .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                    .because("cada ServiceImpl declara @Transactional a nivel de clase (§11, regla 2)")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule serviceImplsImplementTheirServiceInterface =
            classes().that().haveSimpleNameEndingWith("ServiceImpl")
                    .should().implement(interfaceNamedService())
                    .because("los controllers dependen de la interfaz del service, no de la implementación (§5)")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule servicesNeverReturnOptionalOrEntities =
            noMethods().that().arePublic()
                    .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("ServiceImpl")
                    .should().haveRawReturnType(Optional.class)
                    .orShould().haveRawReturnType(annotatedWithEntity())
                    .because("el service devuelve DTOs o lanza BusinessException; nunca Optional ni entidades (bootstrap D1)")
                    .allowEmptyShould(false);

    // change auth-jwt-filter, design D6: el controller recibe AuthUser y al service solo le pasa el id (§3.2)
    @ArchTest
    static final ArchRule servicesDoNotKnowTheAuthenticatedPrincipal =
            // Se excluye security: JwtService es justamente quien crea el AuthUser a partir del token
            noClasses().that().haveNameMatching(".*(Service|ServiceImpl)")
                    .and().resideOutsideOfPackage("com.shoplab.api.security..")
                    .should().dependOnClassesThat().haveFullyQualifiedName("com.shoplab.api.security.AuthUser")
                    .because("los services reciben Long userId, no el AuthUser de la seguridad (§3.2)")
                    .allowEmptyShould(false);

    private static DescribedPredicate<JavaClass> interfaceNamedService() {
        return DescribedPredicate.describe("an interface whose name ends with 'Service'",
                type -> type.isInterface() && type.getSimpleName().endsWith("Service"));
    }

    private static DescribedPredicate<JavaClass> annotatedWithEntity() {
        return DescribedPredicate.describe("an @Entity class", type -> type.isAnnotatedWith(Entity.class));
    }
}
