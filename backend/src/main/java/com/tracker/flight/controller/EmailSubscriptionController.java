package com.tracker.flight.controller;

import com.tracker.flight.dto.CreateEmailSubscriptionRequest;
import com.tracker.flight.dto.EmailSubscriptionResponse;
import com.tracker.flight.dto.SubscriptionTokenRequest;
import com.tracker.flight.service.EmailSubscriptionService;
import com.tracker.flight.service.FlightFilters;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions/email")
public class EmailSubscriptionController {
    private final EmailSubscriptionService subscriptions;

    public EmailSubscriptionController(EmailSubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EmailSubscriptionResponse create(@Valid @RequestBody CreateEmailSubscriptionRequest request) {
        return subscriptions.create(request);
    }

    @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EmailSubscriptionResponse confirm(@Valid @RequestBody SubscriptionTokenRequest request) {
        return subscriptions.confirm(request.token());
    }

    @PostMapping(value = "/unsubscribe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EmailSubscriptionResponse unsubscribe(@Valid @RequestBody SubscriptionTokenRequest request) {
        return subscriptions.unsubscribe(request.token());
    }

    // Mail scanners can GET links safely. The visitor must submit the POST form explicitly.
    // React may use the JSON endpoints instead; these pages also work before its integration.
    @GetMapping(value = "/{action:confirm|unsubscribe}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> tokenPage(@PathVariable String action, @RequestParam String token) {
        validateToken(token);
        String label = "confirm".equals(action) ? "Подтвердить подписку" : "Отписаться";
        String html = "<!doctype html><html lang=\"ru\"><meta charset=\"utf-8\">"
                + "<meta name=\"referrer\" content=\"no-referrer\"><title>" + label + "</title>"
                + "<h1>" + label + "</h1><form method=\"post\" action=\"/api/v1/subscriptions/email/"
                + action + "\"><input type=\"hidden\" name=\"token\" value=\"" + token
                + "\"><button type=\"submit\">" + label + "</button></form></html>";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").body(html);
    }

    @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public EmailSubscriptionResponse confirmForm(@RequestParam String token) {
        validateToken(token);
        return subscriptions.confirm(token);
    }

    @PostMapping(value = "/unsubscribe", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public EmailSubscriptionResponse unsubscribeForm(@RequestParam String token) {
        validateToken(token);
        return subscriptions.unsubscribe(token);
    }

    private void validateToken(String token) {
        if (!token.matches("[A-Za-z0-9_-]{43}")) throw FlightFilters.badRequest("Недействительный токен");
    }
}
