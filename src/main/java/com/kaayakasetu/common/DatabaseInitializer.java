package com.kaayakasetu.common;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the tables and seeds the demo data on startup. Equivalent of init_db() + seed_sample_data()
 * in database.py (which ran inside FastAPI's lifespan hook).
 * Implementing InitializingBean means it runs while Spring is building the beans, i.e. BEFORE the
 * web server starts accepting requests - so no request can ever see missing tables.
 */
@Component
public class DatabaseInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    /** Password of the two demo accounts (only created when app.seed-demo-users=true). */
    public static final String DEMO_PASSWORD = "Demo@1234";
    public static final String DEMO_WORKER_PHONE = "9845011223";    // Ramesh Gowda (worker id 1)
    public static final String DEMO_EMPLOYER_PHONE = "9900112233";  // Shree Manjunatha Traders (owns job 1)

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedDemoUsers;

    @Autowired
    public DatabaseInitializer(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                               @Value("${app.seed-demo-users:true}") boolean seedDemoUsers) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.seedDemoUsers = seedDemoUsers;
    }

    @Override
    public void afterPropertiesSet() {
        log.info("Initializing KaayakaSetu Database...");
        createTables();
        seedSampleData();
        if (seedDemoUsers) {
            seedDemoUsers();
        }
        log.info("Database and sample datasets initialized successfully!");
    }

    /**
     * Portable DDL: runs on MySQL (production) and on H2 in MySQL mode (tests).
     * Table order matters because of the foreign keys: workers -> users -> jobs.
     */
    public void createTables() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS workers (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                phone VARCHAR(32) NOT NULL UNIQUE,
                trade VARCHAR(100) NOT NULL,
                secondary_skills TEXT,
                experience_years INT NOT NULL DEFAULT 1,
                daily_rate DOUBLE NOT NULL,
                hourly_rate DOUBLE,
                city VARCHAR(100) NOT NULL,
                area VARCHAR(255),
                latitude DOUBLE NOT NULL,
                longitude DOUBLE NOT NULL,
                availability VARCHAR(30) NOT NULL DEFAULT 'available',
                rating DOUBLE DEFAULT 4.5,
                jobs_completed INT DEFAULT 0,
                language_pref VARCHAR(20) NOT NULL DEFAULT 'kannada',
                source VARCHAR(20) NOT NULL DEFAULT 'web',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )""");

        // Login accounts. A WORKER account points to its worker profile (worker_id, set when the profile is created).
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                phone VARCHAR(32) NOT NULL UNIQUE,
                password_hash VARCHAR(100) NOT NULL,
                name VARCHAR(255) NOT NULL,
                user_role VARCHAR(20) NOT NULL,
                worker_id BIGINT UNIQUE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (worker_id) REFERENCES workers(id)
            )""");

        // owner_user_id = the employer account that posted the job (NULL for the old demo jobs)
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS jobs (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                trade VARCHAR(100) NOT NULL,
                description TEXT,
                employer_name VARCHAR(255) NOT NULL,
                employer_phone VARCHAR(32) NOT NULL,
                city VARCHAR(100) NOT NULL,
                area VARCHAR(255),
                latitude DOUBLE NOT NULL,
                longitude DOUBLE NOT NULL,
                duration_days INT NOT NULL DEFAULT 1,
                wage_offered DOUBLE NOT NULL,
                payment_type VARCHAR(30) NOT NULL DEFAULT 'daily',
                urgency VARCHAR(30) NOT NULL DEFAULT 'normal',
                workers_needed INT NOT NULL DEFAULT 1,
                status VARCHAR(30) NOT NULL DEFAULT 'open',
                owner_user_id BIGINT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (owner_user_id) REFERENCES users(id)
            )""");

        // sender/receiver ids: worker -> workers.id, employer -> users.id (0 = demo job without an owner)
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS contacts (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                sender_type VARCHAR(20) NOT NULL,
                sender_id BIGINT NOT NULL,
                receiver_type VARCHAR(20) NOT NULL,
                receiver_id BIGINT NOT NULL,
                job_id BIGINT,
                message TEXT,
                status VARCHAR(30) NOT NULL DEFAULT 'pending',
                phone_shared INT DEFAULT 1,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )""");

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS telecom_sessions (
                session_id VARCHAR(100) PRIMARY KEY,
                phone_number VARCHAR(32) NOT NULL,
                session_type VARCHAR(20) NOT NULL DEFAULT 'ussd',
                current_step VARCHAR(50) NOT NULL DEFAULT 'welcome',
                language VARCHAR(20) NOT NULL DEFAULT 'kannada',
                draft_json TEXT,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )""");
    }

    /** Two ready-made accounts so the dashboard can be tried immediately. Runs only when the users table is empty. */
    public void seedDemoUsers() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        String hash = passwordEncoder.encode(DEMO_PASSWORD);

        List<Long> workerIds = jdbc.queryForList("SELECT id FROM workers WHERE phone = ?", Long.class, DEMO_WORKER_PHONE);
        Long workerId = workerIds.isEmpty() ? null : workerIds.get(0);
        jdbc.update("INSERT INTO users (phone, password_hash, name, user_role, worker_id) VALUES (?, ?, ?, ?, ?)",
                DEMO_WORKER_PHONE, hash, "Ramesh Gowda", "WORKER", workerId);

        jdbc.update("INSERT INTO users (phone, password_hash, name, user_role, worker_id) VALUES (?, ?, ?, ?, NULL)",
                DEMO_EMPLOYER_PHONE, hash, "Shree Manjunatha Traders", "EMPLOYER");
        Long employerId = jdbc.queryForObject("SELECT id FROM users WHERE phone = ?", Long.class, DEMO_EMPLOYER_PHONE);
        jdbc.update("UPDATE jobs SET owner_user_id = ? WHERE employer_phone = ?", employerId, DEMO_EMPLOYER_PHONE);

        log.warn("Demo accounts created: worker {} and employer {} (password '{}'). "
                + "Set SEED_DEMO_USERS=false for production.", DEMO_WORKER_PHONE, DEMO_EMPLOYER_PHONE, DEMO_PASSWORD);
    }

    public void seedSampleData() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM workers", Integer.class);
        if (count != null && count > 0) {
            return; // already seeded
        }

        // name, phone, trade, secondary_skills, experience_years, daily_rate, hourly_rate,
        // city, area, latitude, longitude, availability, rating, jobs_completed, language_pref, source
        List<Object[]> workers = new ArrayList<>();
        workers.add(new Object[]{"Ramesh Gowda", "9845011223", "Electrician", "Wiring, Inverter, MCB", 5, 850.0, 150.0,
                "Bengaluru", "Rajajinagar", 12.9904, 77.5530, "available", 4.8, 42, "kannada", "web"});
        workers.add(new Object[]{"Manjunath Patil", "9845022334", "Plumber", "Pipe fitting, Tank cleaning, Taps", 4, 750.0, 120.0,
                "Bengaluru", "Malleshwaram", 13.0031, 77.5643, "available", 4.6, 28, "kannada", "ussd"});
        workers.add(new Object[]{"Suresh Kumar", "9845033445", "Mason", "Brickwork, Plastering, Tiles", 8, 950.0, 160.0,
                "Bengaluru", "Yeshwanthpur", 13.0238, 77.5458, "available", 4.9, 85, "kannada", "ivr"});
        workers.add(new Object[]{"Basavaraj K", "9845044556", "Carpenter", "Furniture repair, Doors, Modular fitting", 6, 900.0, 150.0,
                "Hubballi", "Vidyanagar", 15.3647, 75.1240, "available", 4.7, 31, "kannada", "ussd"});
        workers.add(new Object[]{"Chandrashekhar", "9845055667", "Painter", "Interior painting, Waterproofing, Putty", 3, 700.0, 100.0,
                "Mysuru", "Kuvempunagar", 12.2890, 76.6346, "available", 4.5, 19, "kannada", "web"});
        workers.add(new Object[]{"Ram Lal Yadav", "9811012345", "Electrician", "House wiring, Fan repair, Meter board", 7, 850.0, 140.0,
                "Delhi", "Laxmi Nagar", 28.6304, 77.2773, "available", 4.8, 55, "hindi", "ivr"});
        workers.add(new Object[]{"Dinesh Verma", "9811023456", "Plumber", "Sanitary fittings, Motor install", 5, 800.0, 130.0,
                "Lucknow", "Alambagh", 26.8150, 80.9020, "available", 4.6, 34, "hindi", "ussd"});
        workers.add(new Object[]{"Santosh Chauhan", "9811034567", "Mason", "Construction, Wall tile laying", 10, 1000.0, 170.0,
                "Patna", "Kankarbagh", 25.5941, 85.1376, "available", 4.9, 110, "hindi", "web"});
        workers.add(new Object[]{"Anand Kumar", "9845099887", "Helper", "Loading, Material shifting, Site cleanup", 2, 550.0, 80.0,
                "Bengaluru", "Peenya", 13.0285, 77.5197, "available", 4.3, 14, "kannada", "ussd"});
        workers.add(new Object[]{"Sunil Yadav", "9811045678", "Welder", "Grill fabrication, Gate repair, Arc welding", 6, 900.0, 150.0,
                "Delhi", "Okhla", 28.5300, 77.2700, "available", 4.7, 40, "hindi", "web"});

        jdbc.batchUpdate("""
            INSERT INTO workers (
                name, phone, trade, secondary_skills, experience_years, daily_rate, hourly_rate,
                city, area, latitude, longitude, availability, rating, jobs_completed, language_pref, source
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", workers);

        // title, trade, description, employer_name, employer_phone, city, area, latitude, longitude,
        // duration_days, wage_offered, payment_type, urgency, workers_needed, status
        List<Object[]> jobs = new ArrayList<>();
        jobs.add(new Object[]{"Commercial Shop 3-Phase Wiring Needed", "Electrician",
                "Urgent requirement for complete 3-phase wiring in newly renovated shop.",
                "Shree Manjunatha Traders", "9900112233", "Bengaluru", "Rajajinagar",
                12.9900, 77.5520, 2, 1800.0, "daily", "urgent", 2, "open"});
        jobs.add(new Object[]{"Bathroom Water Line & Tap Repair", "Plumber",
                "Leaking overhead pipe and fitting of new bathroom sink and fixtures.",
                "Raghavendra Rao", "9900223344", "Bengaluru", "Malleshwaram",
                13.0040, 77.5650, 1, 800.0, "daily", "normal", 1, "open"});
        jobs.add(new Object[]{"Compound Wall Masonry Work", "Mason",
                "Building 40 feet brick compound wall and cement plastering.",
                "Kiran Constructions", "9900334455", "Bengaluru", "Yeshwanthpur",
                13.0250, 77.5460, 4, 3800.0, "daily", "normal", 3, "open"});
        jobs.add(new Object[]{"Apartment Repainting (2BHK)", "Painter",
                "Interior walls painting with primer, putty, and 2 coats of emulsion paint.",
                "Naveen Hegde", "9900445566", "Mysuru", "Kuvempunagar",
                12.2900, 76.6350, 3, 2400.0, "daily", "normal", 2, "open"});
        jobs.add(new Object[]{"Showroom Wooden Display Racks Fitting", "Carpenter",
                "Installation of plywood display racks and fixing laminate edges.",
                "Hubli Fashion Hub", "9900556677", "Hubballi", "Vidyanagar",
                15.3650, 75.1250, 2, 1900.0, "daily", "normal", 1, "open"});
        jobs.add(new Object[]{"Factory Main Line Inverter Setup", "Electrician",
                "Industrial panel check and heavy load inverter connection.",
                "Sharma Enterprises", "9810112233", "Delhi", "Okhla",
                28.5320, 77.2710, 1, 1000.0, "daily", "urgent", 1, "open"});
        jobs.add(new Object[]{"School Building Renovation Brickwork", "Mason",
                "Wall repairs, stair renovation, and cement finishing.",
                "Adarsh Vidyalaya", "9810223344", "Lucknow", "Alambagh",
                26.8160, 80.9030, 5, 5200.0, "daily", "normal", 2, "open"});
        jobs.add(new Object[]{"Warehouse Goods Loading & Unloading", "Helper",
                "Heavy boxes unloading from transport container and stacking.",
                "Karnataka Logistics", "9900778899", "Bengaluru", "Peenya",
                13.0290, 77.5200, 1, 650.0, "daily", "urgent", 4, "open"});

        jdbc.batchUpdate("""
            INSERT INTO jobs (
                title, trade, description, employer_name, employer_phone, city, area,
                latitude, longitude, duration_days, wage_offered, payment_type, urgency,
                workers_needed, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", jobs);
        java.sql.Timestamp seededAt = java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().withNano(0));
        jdbc.update("UPDATE workers SET created_at = ?", seededAt);
        jdbc.update("UPDATE jobs SET created_at = ?", seededAt);
        log.info("Sample database initialized with realistic worker and job profiles!");
    }
}
