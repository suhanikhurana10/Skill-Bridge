# KaayakaSetu (Java edition) - Project Notes

Everything you need to understand this project well enough to run it, explain it, and defend the choices in it.
Read sections 1-4 first. Section 8 is a list of questions you are likely to be asked, with answers.

---

## 1. What the project is (in your own words)

KaayakaSetu is a job platform for blue-collar workers (electricians, plumbers, masons, ...) and the people who hire them.

* Workers register (on the web, or from a plain feature phone via **USSD** `*345#` or an **IVR** voice call).
* Employers post jobs.
* The platform **matches** workers to jobs with an explainable score, shows **nearby jobs/workers**, and tells people whether a wage is **fair** for the trade and city.
* Kannada, Hindi and English are supported on the phone channels, because many of these workers do not use smartphones.

**Original:** Python 3 + FastAPI + SQLite.
**This version:** Java 17 + Spring Boot 3.5 + SQLite. The web dashboard is the same files as before.

### What changed and what did not

| | Original | Java edition |
|---|---|---|
| Language / framework | Python, FastAPI | Java 17, Spring Boot 3.5 |
| Database | SQLite | SQLite (same schema, same seed data) |
| Frontend | HTML/CSS/JS | **identical files** |
| API URLs and JSON field names | `/api/v1/...`, snake_case | **identical** |
| Run command | `python run.py` | `./mvnw spring-boot:run` |
| API docs | FastAPI `/docs`, `/redoc` | Swagger UI at `/docs`, JSON at `/openapi.json` (no `/redoc`) |
| Tests | pytest | JUnit 5 |

---

## 2. Architecture

```
 Browser dashboard / feature phone (USSD) / voice call (IVR)
                         |
                  Spring MVC controllers          <- HTTP, JSON, validation
                         |
                      services                    <- business rules (matching, wage, USSD state machine ...)
                         |
                    repositories                  <- SQL only (JdbcTemplate)
                         |
                  SQLite file (bluecollar.db)
```

Eight modules, one package each: `worker`, `job`, `matching`, `feed`, `location`, `wage`, `contact`, `telecom`.
Inside a package the layering is always **Controller -> Service -> Repository**.

### Python file -> Java class map

| Python | Java |
|---|---|
| `run.py`, `app/main.py` | `KaayakaSetuApplication`, `StartupBanner`, `HealthController`, `WebConfig` |
| `app/config.py` | `application.yml`, `AppConstants` |
| `app/database.py` | `DataSourceConfig`, `DatabaseInitializer` |
| `app/models/schemas.py` (Pydantic) | Java `record` classes with validation annotations, one per request/response |
| `app/routers/*.py` | `*Controller` classes |
| `app/services/*.py` | `*Service` classes (+ `*Repository` for the SQL part) |
| `app/static/*` | `src/main/resources/static/*` (unchanged) |
| `app/tests/test_services.py` | `LogicAndServiceTests`, `ApiIntegrationTests` |
| `Dockerfile`, `docker-compose.yml`, `render.yaml` | same names, rewritten for Java |

---

## 3. Important point about "microservices"

The original README calls this a microservice platform. Looking at the code, it is **one FastAPI process** with eight routers sharing **one SQLite database**. That is a **modular monolith**: it is organised like microservices (separate modules, own URL prefix, own service class) but it is deployed as a single application.

I kept that structure on purpose. Splitting it into eight Spring Boot applications would need a gateway, service-to-service calls and a database per service. That adds a lot of complexity without any benefit at this size, and it would change the behaviour the frontend relies on.

**How to describe it honestly:** "It is a modular, microservice-style architecture: each domain is its own module with its own API, service and repository, deployed as one Spring Boot application. Each module could be extracted into its own service later." That is accurate and still a strong answer.

---

## 4. Key decisions and why

### Technology choices

| Decision | Why |
|---|---|
| **Spring Boot** | The standard way to build REST backends in Java. Closest equivalent of FastAPI: controllers = routers, `@Service` = service classes, Bean Validation = Pydantic. |
| **Spring Boot 3.5.x, not 4.x** | 4.x exists (it moved to Jackson 3 and re-organised its starters), but 3.5 has by far the most documentation and the libraries I use are proven with it. Nothing in this project needs 4.x features. |
| **Java 17 baseline** | Needed for `record` and text blocks. Setting 17 (not 21) means it compiles on a laptop with either JDK 17 or 21. No Java-21-only features are used. |
| **Maven + Maven Wrapper** | You do not have to install Maven; `mvnw` downloads the right version. Maven is also what most companies and tutorials use. |
| **SQLite kept** | The original used it, it needs no installation, and the whole database is one file. Keeping it means identical behaviour and zero setup. (For production you would move to PostgreSQL.) |
| **`JdbcTemplate`, not JPA/Hibernate** | The original used plain SQL. JdbcTemplate keeps the SQL visible and identical, so behaviour is easy to compare. Hibernate has no official SQLite support and would add risk and ceremony for 4 tables. |
| **Repository layer added** | The Python services mixed SQL and logic. Splitting them (Controller -> Service -> Repository) is the conventional Java structure and makes logic testable without a database. |
| **Java `record`s for models** | Immutable, no boilerplate (no getters/setters), works naturally with Jackson. One record = one JSON shape. |
| **springdoc-openapi** | Gives the same "interactive API docs" experience FastAPI has, at `/docs`. |
| **Hikari pool of exactly 1 connection** | SQLite allows only one writer at a time. A single connection serialises access and avoids "database is locked" errors. Fine for this scale. |
| **`INSERT ... RETURNING id`** | To get the new row id. The usual JDBC `getGeneratedKeys()` is not supported by every SQLite driver version; I found this while testing, so I used SQLite's own `RETURNING` clause. |

### Behaviour-compatibility decisions (so the unchanged frontend keeps working)

| Decision | Why |
|---|---|
| **`SNAKE_CASE` JSON** (`spring.jackson.property-naming-strategy`) | The frontend reads `daily_rate`, `created_at`, `match_score` ... Java fields are camelCase (`dailyRate`); Jackson converts at the boundary. |
| **Explicit `@JsonProperty("daily_rate")` on request fields** | Belt and braces: some older Jackson versions mishandle records + naming strategies. Explicit names work on every version. |
| **Both `/workers` and `/workers/` mapped** (`@PostMapping({"", "/"})`) | FastAPI accepted both; the frontend calls the trailing-slash form. Spring Boot 3 treats them as different URLs, so without this the dashboard would get 404s. |
| **`/static/**` extra resource mapping** | The HTML links `/static/css/style.css`; Spring Boot normally serves that folder at `/css/...`. The extra mapping keeps the HTML untouched. |
| **Errors as `{"detail": ...}`, validation as HTTP 422** | The frontend shows `err.detail`; FastAPI used 422 for invalid input. `ApiExceptionHandler` reproduces both. |
| **Manual range checks for query params** (`RequestChecks`) | Equivalent of `Query(ge=1, le=100)`; explicit code behaves the same on every Spring version. |
| **`PyMath.round` (round-half-even) and `Fmt`** | Python rounds 2.5 to 2; Java's `Math.round` gives 3. The wage and score numbers must be identical, so I reproduce Python's rounding. `Locale.US` is forced for number formatting so a laptop set to a different locale cannot change the output. |
| **UTF-8 forced everywhere** (`project.build.sourceEncoding`, response charsets) | Kannada/Hindi text lives in the source and in responses. Without this, Windows may corrupt it. |
| **Same env vars `HOST`, `PORT`, `DB_PATH`** | Drop-in replacement for deployment configs. |
| **CORS wide open** | Same as the original (`allow_origins=["*"]`). Fine for a demo; restrict for production. |

### Design of the main features (be ready to explain these)

**Smart matching** (`MatchingService`) - rule-based and explainable (not machine learning):

```
total score = 40% skill + 30% distance + 20% wage + 10% reliability     (scaled to 0-100)
```
* Skill: exact trade = 1.0; trade listed in the worker's secondary skills = 0.85; related trade = 0.70; helper fallback = 0.50; otherwise 0.15.
* Distance (Haversine, great-circle km): <=5 km = 1.0, <=15 = 0.85, <=30 = 0.65, within the search radius = 0.40, beyond = 0.10.
* Wage: offer >= worker's rate = 1.0; within 15% below = 0.80; within 30% below = 0.50; else 0.20.
* Reliability = 60% star rating + 40% availability.
* Every result includes a plain-language `explanation`. Rule-based scoring was chosen because users can see *why* a match was made, and there is no training data.

**Fair wage predictor** (`FairWageService`) - heuristic formula:

```
daily = base(trade) x cityTierMultiplier x (1 + 3% x years, max 15 years) x (1.20 if urgent) + toolPremium
recommended = daily rounded to nearest Rs.50          fair band = 85% .. 125% of recommended
```
* City tiers: Tier-1 (Bengaluru, Delhi, Mumbai, Hyderabad) x1.25, Tier-2 x1.00, Tier-3 x0.85.
* Tool premium: Rs.150 for skilled trades, Rs.80 otherwise.
* Gives explanations in English, Kannada and Hindi. If an offer is supplied it labels it `BELOW_FAIR_WAGE`, `FAIR_MARKET_RATE` or `PREMIUM_OFFER`.
* The base rates are hard-coded benchmarks, not live market data.

**USSD** (`TelecomService.handleUssd`) - a **state machine**. USSD is a text menu on a basic phone; every key press is a separate HTTP call, so the server must remember where the caller is. The table `telecom_sessions` stores `current_step` (`welcome` -> `main_menu` -> `reg_trade` -> `reg_exp` -> `reg_wage` -> `reg_city` ...), the chosen language, and a JSON "draft" of the answers so far. Replies start with `CON` (continue) or `END` (finish) - that is the telecom standard.

**IVR** - a voice call. The server answers with **TwiML** (an XML dialect understood by Twilio/Exotel) containing `<Say>` (speak this text) and `<Gather>` (wait for a key press). The same call returns plain text for the on-screen phone simulator.

**Feeds** - open jobs for a worker (urgent first, then nearest) and available workers for an employer (nearest, then best-rated).

**Contact** - an "employer" is not its own table; employer name/phone come from the job a contact refers to. SMS sending is **simulated** (a mock gateway message) - nothing is really sent.

### Configuration and data
* Demo data: 10 workers and 8 jobs across Bengaluru, Mysuru, Hubballi, Delhi, Lucknow, Patna are inserted only when the `workers` table is empty.
* Database file `bluecollar.db` is created next to where you start the app. Delete it to reset the data.

---

## 5. Differences from the Python version (bugs found in the original)

While comparing the two versions I found issues in the original. I fixed three small ones and deliberately kept the rest so behaviour is identical.

**Fixed in the Java version**

| # | Original behaviour | Java behaviour |
|---|---|---|
| 1 | Job feed with no location: the sort used `reverse=True` on the whole key, so **urgent jobs came last**, contradicting the code comment ("urgent first"). | Urgent first, then newest. |
| 2 | SMS text for a contact with no message printed `Message: 'None'`. | Prints `Interested in work` (the intended default). |
| 3 | IVR voice text was inserted into XML without escaping, so a job title like `A & B` would produce broken XML. | XML-escaped. |

**Kept the same on purpose (known quirks of the original)**

* Fuzzy trade matching in the wage service returns the *first* table key that matches, so "Tile Mason" is priced as "Mason" (the plain `mason` entry is checked before `tile_mason`).
* Unknown cities fall back to Bengaluru's coordinates *and* are labelled Tier-2.
* In the USSD registration flow, invalid numbers (e.g. experience `-3`, wage `0`) are swallowed: the caller is told "Registration complete" but nothing is saved.
* The USSD job list shows "/ದಿನ" (Kannada for "per day") even for Hindi and English callers.
* Contact status accepts any text (only "accepted"/"declined" are documented, not enforced).
* No authentication; the "logged-in user" in the dashboard is hard-coded to id 1.

---

## 6. What was verified, and what was not (be accurate about this)

**Verified (I ran it):**
* 55 of the 58 main Java source files compile (Java 17 language level) against the real Spring JDBC/Web, Jackson, Bean Validation and SQLite libraries. The 3 I could not compile offline are pure configuration classes that need libraries unavailable to me (`DataSourceConfig` needs HikariCP, `OpenApiConfig` needs springdoc, `WebConfig` needs Spring MVC config).
* **Differential test against the original Python code:** I ran the same 2,425 recorded scenarios through both the Python services and the Java services on fresh SQLite databases (seed data, ~1,600 wage predictions, 529 city-to-city distances, every worker x job score, rankings, feeds, filters, creates/updates, contacts, every USSD branch in all three languages, IVR). **2,423 matched exactly; the other 2 are the intentional fixes #1 and #2 above.**
* 14 unit tests pass (`LogicAndServiceTests`) on real SQLite.
* Request bodies in the exact JSON the frontend sends deserialize correctly.

**Not verified in my environment (no access to Maven Central, so I could not boot Spring Boot itself):**
* The Spring Boot start-up, the HTTP routing/validation layer, Swagger, and the `ApiIntegrationTests` have **not been executed**. Those classes compile against Spring and use standard APIs, but the first real run is on your laptop. If something fails there, see Troubleshooting in the run guide - and tell me the exact error text.
* The Docker build has not been run.

---

## 7. Possible improvements (good to mention)

* Move SQLite -> PostgreSQL and add a migration tool (Flyway) instead of `CREATE TABLE IF NOT EXISTS`.
* Add authentication (Spring Security + JWT) - today anyone can call any endpoint.
* Connect real SMS/voice providers (Exotel/Twilio) behind the existing mock; add webhook signature checks.
* Use a routing API for real road distance instead of straight-line Haversine.
* Replace the heuristic wage table with data from government minimum-wage notifications or a trained model.
* Pagination metadata, rate limiting, caching of the city table, structured logging, metrics (Actuator).

---

## 8. Questions you may be asked (with answers)

**Why did you choose Spring Boot?**
It is the standard Java framework for REST services, with built-in validation, JSON handling and testing. It maps directly onto the FastAPI concepts the original used.

**Is this really microservices?**
It is modular, microservice-style: separate modules (workers, jobs, matching, wage, telecom...) each with their own controller, service and repository, deployed as one application. They share one SQLite database, so strictly it is a modular monolith - that is the right size for this project, and each module could be split out later.

**How does the matching work? Is it AI?**
It is a transparent weighted scoring model (40% skill, 30% distance, 20% wage fit, 10% reliability) that also returns a human-readable explanation. I chose rules over machine learning because there is no training data and users should be able to see why a match was made.

**How is distance calculated?**
With the Haversine formula: the great-circle distance between two latitude/longitude points on a sphere of radius 6,371 km. It is a straight-line distance, not road distance.

**How does USSD work if HTTP is stateless?**
Each key press arrives as a separate request, so the server stores the session (current step, language, answers so far) in the `telecom_sessions` table, keyed by session id. A state machine reads the step, interprets the input, saves the next step and replies `CON` or `END`.

**What is TwiML?**
Twilio's XML format for voice calls. `<Say>` speaks text, `<Gather>` collects key presses, `<Hangup>` ends the call. Exotel and similar providers accept the same idea.

**How do you prevent SQL injection?**
All user values are passed as bound parameters (`?`) through JdbcTemplate. The only dynamic SQL is the column list of partial updates, and those column names are fixed in code, never taken from the request.

**How do you handle validation?**
Request records carry Bean Validation annotations (`@NotBlank`, `@Min`, `@Positive` ...) and the controller uses `@Valid`. Failures produce HTTP 422 with a `detail` body, which is what the existing frontend expects.

**Why SQLite and a connection pool of one?**
SQLite is file-based and needs no setup, perfect for a demo. It only allows one writer at a time, so one pooled connection avoids locking errors. For production I would use PostgreSQL.

**How do you know the Java version behaves like the Python one?**
I ran identical scenarios through both implementations and compared the outputs (about 2,400 comparisons). Everything matched except three small bugs in the original that I fixed on purpose and documented.

**What was the hardest part of the migration?**
Matching the details that frameworks do silently: snake_case JSON, trailing-slash URLs, FastAPI-style error bodies, Python's round-half-even rounding, and UTF-8 handling for Kannada/Hindi text.

**What would you do next?**
Authentication, PostgreSQL + Flyway, real SMS/voice integration, and real-road distances (see section 7).

---

## 9. A note on presenting the project

Be ready to explain every part of this document in your own words - run the app, click through each tab, read the matching and USSD code once, and try changing a weight or a menu text and seeing the effect. That makes the explanation yours.

If you are asked how it was built, say plainly that the original was Python, that this edition is a Java port prepared with an AI assistant, and that you ran, tested and understood it. Interviewers care far more that you can explain and modify the code than about who typed it - and a claim of sole authorship can come apart on the first follow-up question.
