package com.papercopilot.ai.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.papercopilot.ai.screen.NlScreenService;
import com.papercopilot.ai.screen.NlScreenService.NlScreenResponse;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final NlScreenService nlScreen;

    public AiController(NlScreenService nlScreen) {
        this.nlScreen = nlScreen;
    }

    public record ScreenQuery(String query) {}

    @PostMapping("/screen")
    public NlScreenResponse screen(@RequestHeader(value = "X-User-Id", defaultValue = "demo") String userId,
                                   @RequestBody ScreenQuery body) {
        return nlScreen.screen(userId, body.query());
    }
}
