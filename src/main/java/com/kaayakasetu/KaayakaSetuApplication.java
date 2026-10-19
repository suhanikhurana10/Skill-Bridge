package com.kaayakasetu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Java equivalent of run.py + app/main.py.
 * Starts one embedded Tomcat server that hosts all the modules (workers, jobs, matching, ...)
 * plus the static web dashboard.
 */
@SpringBootApplication
public class KaayakaSetuApplication {

    public static void main(String[] args) {
        SpringApplication.run(KaayakaSetuApplication.class, args);
    }
}
