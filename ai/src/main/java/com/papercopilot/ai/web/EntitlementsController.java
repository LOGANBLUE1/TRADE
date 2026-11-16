package com.papercopilot.ai.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.papercopilot.ai.credits.AiCreditService;
import com.papercopilot.ai.screen.NlScreenService;

/**
 * Tells the UI whether to enable the AI inputs. Lives outside /api/ai so the credit gate never
 * applies, and never fails: any problem just reports AI as off.
 */
@RestController
public class EntitlementsController {

    private static final Logger log = LoggerFactory.getLogger(EntitlementsController.class);

    private final AiCreditService credits;
    private final NlScreenService nlScreen;

    public EntitlementsController(AiCreditService credits, NlScreenService nlScreen) {
        this.credits = credits;
        this.nlScreen = nlScreen;
    }

    public record Entitlements(boolean aiEnabled, boolean aiModelConfigured, int aiCreditsRemaining) {}

    @GetMapping("/api/me/entitlements")
    public Entitlements entitlements(@RequestHeader(value = "X-User-Id", defaultValue = "demo") String userId) {
        try {
            boolean configured = nlScreen.modelConfigured();
            int remaining = credits.balance(userId);
            return new Entitlements(configured && remaining > 0, configured, remaining);
        } catch (RuntimeException e) {
            log.warn("Entitlements lookup failed; reporting AI as off", e);
            return new Entitlements(false, false, 0);
        }
    }
}
