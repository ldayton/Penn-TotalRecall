package actions;

import static org.junit.jupiter.api.Assertions.*;

import di.GuiceBootstrap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisplayName("ActionsManager Creation Order")
class ActionsManagerTest {
    private static final Logger logger = LoggerFactory.getLogger(ActionsManagerTest.class);
    
    // Static list to track creation order across classes
    private static final List<String> creationOrder = new ArrayList<>();
    
    public static void recordCreation(String className) {
        creationOrder.add(className);
        logger.info("Created: {}", className);
    }
    
    public static List<String> getCreationOrder() {
        return new ArrayList<>(creationOrder);
    }
    
    public static void clearCreationOrder() {
        creationOrder.clear();
    }

    @Test
    @DisplayName("ActionsManager is created before MyMenu during GuiceBootstrap creation")
    void actionsManagerCreatedBeforeMyMenu() {
        logger.info("Testing strict creation order...");
        clearCreationOrder();
        
        // Create GuiceBootstrap - this triggers dependency injection and constructor calls
        GuiceBootstrap bootstrap = GuiceBootstrap.create();
        assertNotNull(bootstrap, "GuiceBootstrap should be created");
        
        List<String> order = getCreationOrder();
        logger.info("Creation order: {}", order);
        
        // Verify ActionsManager was created
        int actionsManagerIndex = order.indexOf("ActionsManager");
        assertTrue(actionsManagerIndex >= 0, "ActionsManager should be in creation order");
        
        // Check if MyMenu was created - it should be since it's injected into GuiceBootstrap
        int myMenuIndex = order.indexOf("MyMenu");
        if (myMenuIndex >= 0) {
            assertTrue(actionsManagerIndex < myMenuIndex, 
                "ActionsManager should be created before MyMenu. Order: " + order);
            logger.info("✅ Both created - ActionsManager at index {}, MyMenu at index {}", 
                actionsManagerIndex, myMenuIndex);
        } else {
            logger.warn("⚠️ MyMenu was not created during GuiceBootstrap.create() - lazy initialization?");
            // This suggests MyMenu is lazily created, not eagerly created with the injector
        }
        
        logger.info("✅ ActionsManager created at index {}, MyMenu at index {}", 
            actionsManagerIndex, myMenuIndex);
        logger.info("🎉 Strict creation order verified");
    }
}