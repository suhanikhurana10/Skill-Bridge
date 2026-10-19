package com.kaayakasetu.contact;

import com.kaayakasetu.auth.AuthUser;
import com.kaayakasetu.auth.Role;
import com.kaayakasetu.auth.User;
import com.kaayakasetu.auth.UserRepository;
import com.kaayakasetu.job.Job;
import com.kaayakasetu.job.JobService;
import com.kaayakasetu.sms.SmsGateway;
import com.kaayakasetu.sms.SmsSendResult;
import com.kaayakasetu.worker.Worker;
import com.kaayakasetu.worker.WorkerService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Contacts between workers and employers.
 * Party ids: a "worker" party is a workers.id, an "employer" party is a users.id (the employer's account).
 * Demo jobs that have no owner use employer id 0 and fall back to the name/phone stored on the job.
 * SMS goes through the {@link SmsGateway} interface - this class does not know whether it is the mock or Twilio.
 */
@Service
public class ContactService {

    private final ContactRepository repository;
    private final WorkerService workerService;
    private final JobService jobService;
    private final UserRepository userRepository;
    private final SmsGateway smsGateway;

    public ContactService(ContactRepository repository, WorkerService workerService, JobService jobService,
                          UserRepository userRepository, SmsGateway smsGateway) {
        this.repository = repository;
        this.workerService = workerService;
        this.jobService = jobService;
        this.userRepository = userRepository;
        this.smsGateway = smsGateway;
    }

    // ------------------------------------------------------------------ rules for a logged-in caller

    /** Starts a contact. The sender is always the caller; the request only says who/what it is about. */
    public ContactResponse initiate(ContactCreateRequest req, AuthUser caller) {
        String receiverType = req.receiverType().strip().toLowerCase(Locale.ROOT);

        if (caller.role() == Role.WORKER) {
            if (caller.workerId() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Create your worker profile before contacting employers");
            }
            if (!"employer".equals(receiverType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workers can only contact employers");
            }
            if (req.jobId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "job_id is required when contacting an employer");
            }
            Job job = jobService.getJob(req.jobId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job posting not found"));
            long employerUserId = jobService.getOwnerUserId(job.id()).orElse(0L);
            return createContact("worker", caller.workerId(), "employer", employerUserId, job.id(), req.message());
        }

        // EMPLOYER
        if (!"worker".equals(receiverType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employers can only contact workers");
        }
        if (req.receiverId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receiver_id is required when contacting a worker");
        }
        if (workerService.getWorker(req.receiverId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker profile not found");
        }
        if (req.jobId() != null) {
            if (jobService.getJob(req.jobId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job posting not found");
            }
            boolean mine = jobService.getOwnerUserId(req.jobId()).map(caller::isEmployer).orElse(false);
            if (!mine) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only attach jobs that you posted");
            }
        }
        return createContact("employer", caller.id(), "worker", req.receiverId(), req.jobId(), req.message());
    }

    /** Only the sender or the receiver may read a contact. */
    public Optional<ContactResponse> getForParticipant(long contactId, AuthUser caller) {
        Optional<Contact> found = repository.findById(contactId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Contact c = found.get();
        if (!isParty(c.senderType(), c.senderId(), caller) && !isParty(c.receiverType(), c.receiverId(), caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This contact does not belong to you");
        }
        return Optional.of(enrich(c));
    }

    /** Only the RECEIVER can accept or decline. */
    public Optional<ContactResponse> updateStatusAs(long contactId, ContactStatusUpdate update, AuthUser caller) {
        Optional<Contact> found = repository.findById(contactId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Contact c = found.get();
        if (!isParty(c.receiverType(), c.receiverId(), caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the receiver can accept or decline a contact");
        }
        String status = update.status().strip().toLowerCase(Locale.ROOT);
        if (!status.equals("accepted") && !status.equals("declined")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "status must be 'accepted' or 'declined'");
        }
        return updateStatus(contactId, new ContactStatusUpdate(status));
    }

    /** Only the SENDER can trigger the SMS notification for their contact. */
    public Optional<SmsDispatchResult> dispatchSmsAs(long contactId, AuthUser caller) {
        Optional<Contact> found = repository.findById(contactId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Contact c = found.get();
        if (!isParty(c.senderType(), c.senderId(), caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the sender can send the SMS for a contact");
        }
        return dispatchSms(contactId);
    }

    /** All contacts where the caller is sender or receiver. */
    public List<ContactResponse> listMine(AuthUser caller) {
        if (caller.role() == Role.WORKER) {
            return caller.workerId() == null ? List.of() : listContactsForUser("worker", caller.workerId());
        }
        return listContactsForUser("employer", caller.id());
    }

    private static boolean isParty(String type, long id, AuthUser caller) {
        return switch (type) {
            case "worker" -> caller.isWorkerProfile(id);
            case "employer" -> caller.isEmployer(id);
            default -> false;
        };
    }

    // ------------------------------------------------------------------ plain operations (no caller checks)

    public ContactResponse createContact(String senderType, long senderId, String receiverType, long receiverId,
                                         Long jobId, String message) {
        long id = repository.insert(senderType, senderId, receiverType, receiverId, jobId, message);
        return getContactWithDetails(id).orElseThrow();
    }

    public Optional<ContactResponse> getContactWithDetails(long contactId) {
        return repository.findById(contactId).map(this::enrich);
    }

    public Optional<ContactResponse> updateStatus(long contactId, ContactStatusUpdate update) {
        repository.updateStatus(contactId, update.status());
        return getContactWithDetails(contactId);
    }

    public List<ContactResponse> listContactsForUser(String userType, long userId) {
        List<ContactResponse> out = new ArrayList<>();
        for (Contact c : repository.findForUser(userType, userId)) {
            out.add(enrich(c));
        }
        return out;
    }

    /** Builds the notification text and hands it to whichever SmsGateway is configured (mock or Twilio). */
    public Optional<SmsDispatchResult> dispatchSms(long contactId) {
        return getContactWithDetails(contactId).map(c -> {
            String message = c.message() == null ? "Interested in work" : c.message();
            String text = "KaayakaSetu: You have a new work inquiry from " + c.senderName()
                    + " (Ph: " + c.senderPhone() + "). Message: '" + message + "'. Call back to confirm.";
            SmsSendResult r = smsGateway.send(c.receiverPhone(), text);
            return new SmsDispatchResult(r.status(), r.provider(), LocalDateTime.now().toString(),
                    c.receiverPhone(), c.senderPhone(), text, r.providerMessageId(), r.error());
        });
    }

    // ------------------------------------------------------------------ helpers

    private ContactResponse enrich(Contact c) {
        Party sender = resolveParty(c.senderType(), c.senderId(), c.jobId());
        Party receiver = resolveParty(c.receiverType(), c.receiverId(), c.jobId());
        return new ContactResponse(c.id(), c.senderType(), c.senderId(), c.receiverType(), c.receiverId(),
                c.jobId(), c.message(), c.status(), c.phoneShared(), c.createdAt(),
                sender.name(), sender.phone(), receiver.name(), receiver.phone());
    }

    private record Party(String name, String phone) {}

    private Party resolveParty(String type, long id, Long jobId) {
        if ("worker".equals(type)) {
            Optional<Worker> w = workerService.getWorker(id);
            return new Party(w.map(Worker::name).orElse("Worker"), w.map(Worker::phone).orElse(""));
        }
        // employer: prefer the real account, fall back to the name/phone written on the job (demo jobs)
        Optional<User> account = id > 0 ? userRepository.findById(id) : Optional.empty();
        if (account.isPresent()) {
            return new Party(account.get().name(), account.get().phone());
        }
        Optional<Job> job = jobId == null ? Optional.empty() : jobService.getJob(jobId);
        return new Party(job.map(Job::employerName).orElse("Employer"), job.map(Job::employerPhone).orElse(""));
    }
}
