package com.kaayakasetu.contact;

import com.kaayakasetu.auth.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** All endpoints need a login. The sender is always the logged-in user; the rules live in ContactService. */
@RestController
@RequestMapping("/api/v1/contact")
@Tag(name = "Contact & Communication Microservice")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    /** Worker applies for a job, or employer asks a worker. */
    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse initiate(@Valid @RequestBody ContactCreateRequest contact,
                                    @AuthenticationPrincipal AuthUser user) {
        return contactService.initiate(contact, user);
    }

    /** My inquiries / leads (as sender or receiver). */
    @GetMapping("/me")
    public List<ContactResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return contactService.listMine(user);
    }

    @GetMapping("/{contactId}")
    public ContactResponse get(@PathVariable("contactId") long contactId, @AuthenticationPrincipal AuthUser user) {
        return contactService.getForParticipant(contactId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact record not found"));
    }

    /** Set status to "accepted" or "declined" (receiver only). */
    @PatchMapping("/{contactId}")
    public ContactResponse updateStatus(@PathVariable("contactId") long contactId,
                                        @Valid @RequestBody ContactStatusUpdate update,
                                        @AuthenticationPrincipal AuthUser user) {
        return contactService.updateStatusAs(contactId, update, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact record not found"));
    }

    /** Same list as /me, but you may only ask for yourself. */
    @GetMapping("/user/{userType}/{userId}")
    public List<ContactResponse> listForUser(@PathVariable("userType") String userType,
                                             @PathVariable("userId") long userId,
                                             @AuthenticationPrincipal AuthUser user) {
        if (!"worker".equals(userType) && !"employer".equals(userType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "user_type must be 'worker' or 'employer'");
        }
        boolean self = "worker".equals(userType) ? user.isWorkerProfile(userId) : user.isEmployer(userId);
        if (!self) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only list your own contacts");
        }
        return contactService.listContactsForUser(userType, userId);
    }

    /** Sends the SMS / WhatsApp-style notification through the configured gateway (mock by default, Twilio optional). */
    @PostMapping("/{contactId}/dispatch-sms")
    public SmsDispatchResult dispatchSms(@PathVariable("contactId") long contactId,
                                         @AuthenticationPrincipal AuthUser user) {
        return contactService.dispatchSmsAs(contactId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact record not found"));
    }
}
