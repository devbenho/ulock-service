package com.codgo.ulock;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Guards the hexagonal vertical-slice architecture described in ARCHITECTURE.md. Slices are discovered
 * from the top-level packages under {@code com.codgo.ulock}, so the rules cover every slice as it is
 * migrated.
 */
@AnalyzeClasses(packages = ArchitectureTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String ROOT = "com.codgo.ulock";
    private static final Set<String> NOT_SLICES = Set.of("common", "sharedkernel");
    /** Parts of a slice that no other slice may use; only application.port.in is public. */
    private static final String[] SLICE_INTERNALS = {"domain", "adapter", "application.service", "application.port.out"};

    @ArchTest
    static final ArchRule domainAndSharedKernelArePureJava = noClasses()
            .that().resideInAnyPackage(ROOT + ".*.domain..", ROOT + ".sharedkernel..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.validation..", "org.hibernate..",
                    "com.fasterxml..", "lombok..")
            .because("domain and shared kernel code must be plain Java");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that().resideInAPackage(ROOT + ".*.application..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".*.adapter..")
            .because("dependencies point inwards: adapter -> application -> domain");

    @ArchTest
    static final ArchRule applicationUsesOnlyDomainSharedKernelAndTransactions = classes()
            .that().resideInAPackage(ROOT + ".*.application..")
            .should().onlyDependOnClassesThat(resideInAnyPackage("java..", ROOT + ".sharedkernel..",
                    ROOT + ".*.domain..", ROOT + ".*.application..", "org.springframework.transaction.annotation..")
                    .or(JavaClass.Predicates.equivalentTo(Service.class)))
            .because("application code may only use @Service and @Transactional from the framework; "
                    + "all infrastructure is reached through outbound ports");

    @ArchTest
    static void otherSlicesUseOnlyTheInboundPorts(JavaClasses classes) {
        for (String slice : slices(classes)) {
            String[] internals = new String[SLICE_INTERNALS.length];
            for (int i = 0; i < SLICE_INTERNALS.length; i++) {
                internals[i] = ROOT + "." + slice + "." + SLICE_INTERNALS[i] + "..";
            }
            noClasses()
                    .that().resideOutsideOfPackage(ROOT + "." + slice + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(internals)
                    .because("other slices may only use " + slice + ".application.port.in")
                    .check(classes);
        }
    }

    @ArchTest
    static final ArchRule jpaEntitiesLiveInPersistenceAdapters = classes()
            .that().haveSimpleNameEndingWith("JpaEntity")
            .should().resideInAPackage(ROOT + ".*.adapter.out.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule jpaEntitiesAreOnlyUsedInsideTheirPersistenceAdapter = classes()
            .that().haveSimpleNameEndingWith("JpaEntity")
            .should(beUsedOnlyInsideTheirPersistenceAdapter())
            .allowEmptyShould(true)
            .because("JPA entities never leave the persistence adapter");

    private static SortedSet<String> slices(JavaClasses classes) {
        SortedSet<String> slices = new TreeSet<>();
        for (JavaClass javaClass : classes) {
            String pkg = javaClass.getPackageName();
            if (pkg.startsWith(ROOT + ".")) {
                String slice = pkg.substring(ROOT.length() + 1).split("\\.")[0];
                if (!NOT_SLICES.contains(slice)) {
                    slices.add(slice);
                }
            }
        }
        return slices;
    }

    /** Users of a JPA entity must sit in the same slice's adapter.out.persistence package tree. */
    private static ArchCondition<JavaClass> beUsedOnlyInsideTheirPersistenceAdapter() {
        return new ArchCondition<>("only be used inside their slice's persistence adapter") {
            @Override
            public void check(JavaClass entity, ConditionEvents events) {
                String pkg = entity.getPackageName();
                String marker = ".adapter.out.persistence";
                String adapterRoot = pkg.contains(marker) ? pkg.substring(0, pkg.indexOf(marker) + marker.length()) : pkg;
                for (Dependency dependency : entity.getDirectDependenciesToSelf()) {
                    String origin = dependency.getOriginClass().getPackageName();
                    if (!origin.equals(adapterRoot) && !origin.startsWith(adapterRoot + ".")) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

}
