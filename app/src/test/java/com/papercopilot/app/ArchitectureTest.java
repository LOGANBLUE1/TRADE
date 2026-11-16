package com.papercopilot.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.papercopilot.core.ledger.OrderService;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.papercopilot", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Core and the user-facing REST API can never be taken down by the AI layer: they cannot even see it. */
    @ArchTest
    static final ArchRule coreAndRestApiDoNotDependOnAi = noClasses()
            .that().resideInAnyPackage("com.papercopilot.core..", "com.papercopilot.app..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.ai..", "com.papercopilot.ai..", "io.modelcontextprotocol..");

    /** Approval is a human action. Nothing in the AI module may approve or directly place an order. */
    @ArchTest
    static final ArchRule aiCannotApproveOrders = noClasses()
            .that().resideInAPackage("com.papercopilot.ai..")
            .should().callMethod(OrderService.class, "approve", String.class, java.util.UUID.class)
            .orShould().callMethod(OrderService.class, "placeManual", String.class,
                    com.papercopilot.core.ledger.TradeRequest.class, String.class);
}
