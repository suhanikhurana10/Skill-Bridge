# KaayakaSetu

A job platform that connects blue-collar workers (electricians, plumbers, masons and so on) with employers.
It ranks jobs and workers against each other, suggests a fair daily wage, and also works from basic phones
through a USSD / IVR menu in Kannada, Hindi and English.

Built with Java 17, Spring Boot 3, Spring Security (JWT), MySQL and plain JDBC. The dashboard is static HTML/JS.
This started as a Java port of a Python/FastAPI project; I then added login, roles, the SMS gateway and the MySQL switch.

## Features

- **Matching**: scores a worker against a job (skill, distance, rate, rating, availability) and ranks the results both ways.
- **Wage predictor**: suggests a daily wage from trade, city and experience.
- **Feeds**: job and worker lists sorted by distance from a chosen city, plus a few marketplace stats.
- **USSD / IVR**: a feature-phone menu so workers without smartphones can register and hear about jobs.
- **Login and roles**: workers and employers register with a phone number and password and get a JWT.
- **SMS notifications**: sent through an interface, so the provider can be swapped without touching business code.

## Security model

- Passwords are stored as BCrypt hashes. Login returns a signed JWT that the client sends as `Authorization: Bearer <token>`.
- The server keeps no session. Each request is checked by a filter that verifies the token and loads the user.
- URL rules (in `SecurityConfig`) decide who may call what: only employers post jobs, only workers create profiles.
- Ownership rules live in the controllers and services: a worker edits only their own profile, an employer only their own jobs,
  and only the two sides of a contact can read it.
- Browsing jobs and workers, the wage calculator and the USSD/IVR webhooks are public.

## SMS gateway

`ContactService` depends on the `SmsGateway` interface only. There are two implementations:
`MockSmsGateway` (default, writes to the log) and `TwilioSmsGateway` (calls Twilio's REST API).
Set `SMS_PROVIDER=twilio` and the Twilio variables below to switch.

The Twilio gateway is tested against a fake HTTP server, not a live account. Sending to Indian numbers in production
would also need DLT registration, which is a regulatory step rather than a code change.

## Running it

You need JDK 17+ and MySQL 8. Maven is included through the wrapper.

```sql
CREATE DATABASE kaayakasetu CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
# Windows PowerShell
$env:DB_PASSWORD="your-mysql-password"; .\mvnw.cmd spring-boot:run

# macOS / Linux
DB_PASSWORD=your-mysql-password ./mvnw spring-boot:run
```

In IntelliJ, set the same variables under Run > Edit Configurations > Environment variables.

Then open http://localhost:8000 (dashboard) or http://localhost:8000/docs (Swagger).
Tables and demo data are created on the first start.

Demo accounts (password `Demo@1234`): worker `9845011223`, employer `9900112233`.

Or with Docker: `docker compose up -d --build` starts the app and MySQL together.

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | local MySQL, database `kaayakasetu` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / empty | |
| `JWT_SECRET` | development value | Change it, 32+ characters |
| `JWT_EXPIRY_MINUTES` | `120` | |
| `SEED_DEMO_USERS` | `true` | Set to `false` outside local use |
| `SMS_PROVIDER` | `mock` | `mock` or `twilio` |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` | empty | Only for `twilio` |
| `PORT` | `8000` | |

## Tests

```bash
./mvnw test
```

40 tests: unit tests for the matching, wage and USSD logic, HTTP tests for the security rules with real tokens,
and tests for the Twilio gateway. They run on in-memory H2 in MySQL mode, so no MySQL server is needed.

## Project layout

```
src/main/java/com/kaayakasetu/
  auth/       register, login, JWT creation and the verifying filter
  config/     security rules, password encoder, web and OpenAPI config
  sms/        SmsGateway interface, mock and Twilio implementations
  worker/ job/ matching/ wage/ feed/ location/ contact/ telecom/
  common/     schema, seed data, error handling
src/main/resources/static/   dashboard
```

## Known limitations

- Phone numbers are not verified (no OTP), so anyone can register any number.
- No login rate limiting, refresh tokens or token revocation; tokens simply expire.
- Worker phone numbers are visible in the public worker list.
- The USSD/IVR webhooks are open; in production they should check the telecom provider's signature.