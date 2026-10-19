package com.kaayakasetu.wage;

import com.kaayakasetu.common.RequestChecks;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wage")
@Tag(name = "Fair Wage Prediction Microservice")
public class WageController {

    private final FairWageService fairWageService;

    public WageController(FairWageService fairWageService) {
        this.fairWageService = fairWageService;
    }

    /** Recommended daily/hourly wage with explanations in English, Kannada and Hindi. */
    @PostMapping("/predict")
    public FairWageResponse predict(@Valid @RequestBody FairWageRequest req) {
        return fairWageService.predictFairWage(req.trade(), req.city(), req.experienceYears(),
                req.hasOwnTools(), req.urgent(), req.hoursPerDay(), null);
    }

    /** Quick check whether an employer's offer meets the fair-wage band. */
    @GetMapping("/benchmark")
    public FairWageResponse benchmark(
            @RequestParam("trade") String trade,
            @RequestParam(name = "city", defaultValue = "Bengaluru") String city,
            @RequestParam(name = "experience_years", defaultValue = "3") int experienceYears,
            @RequestParam(name = "wage_offered", required = false) Double wageOffered) {
        RequestChecks.range("experience_years", experienceYears, 0, 40);
        return fairWageService.predictFairWage(trade, city, experienceYears, true, false, 8, wageOffered);
    }
}
