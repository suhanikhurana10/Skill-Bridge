package com.kaayakasetu.auth;

import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Compared against when the phone is unknown, so "no such user" takes as long as "wrong password". */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public AuthResponse register(RegisterRequest req) {
        Role role = parseRole(req.role());
        String phone = req.phone().strip();
        if (users.findByPhone(phone).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this phone number already exists");
        }
        long id;
        try {
            id = users.insert(phone, passwordEncoder.encode(req.password()), req.name().strip(), role);
        } catch (DuplicateKeyException e) { // two people registering the same phone at the same moment
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this phone number already exists");
        }
        return tokenFor(users.findById(id).orElseThrow());
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByPhone(req.phone().strip()).orElse(null);
        boolean ok = passwordEncoder.matches(req.password(), user == null ? dummyHash : user.passwordHash());
        if (user == null || !ok) {
            // same message for both cases: do not reveal which phone numbers have accounts
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid phone number or password");
        }
        return tokenFor(user);
    }

    private AuthResponse tokenFor(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer", jwtService.expirySeconds(),
                UserProfile.from(user));
    }

    private static Role parseRole(String raw) {
        try {
            return Role.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "role must be 'worker' or 'employer'");
        }
    }
}
