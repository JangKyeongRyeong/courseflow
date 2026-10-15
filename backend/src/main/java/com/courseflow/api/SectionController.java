package com.courseflow.api;

import com.courseflow.enrollment.EnrollmentService;
import com.courseflow.enrollment.EnrollmentService.EnrollmentResult;
import com.courseflow.enrollment.EnrollmentService.EnrollRequest;
import com.courseflow.catalog.SectionQueryService;
import com.courseflow.catalog.SectionQueryService.SectionView;
import com.courseflow.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "${courseflow.web.origin:http://localhost:5173}")
public class SectionController {
    private final SectionQueryService sectionQueryService;
    private final EnrollmentService enrollmentService;

    public SectionController(SectionQueryService sectionQueryService, EnrollmentService enrollmentService) {
        this.sectionQueryService = sectionQueryService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/terms/{termId}/sections")
    public List<SectionView> sections(@PathVariable String termId,
                                      @RequestParam Optional<String> studentNumber) {
        return sectionQueryService.listSections(termId, studentNumber.orElse(""));
    }

    @PostMapping("/sections/{sectionId}/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResult enroll(@PathVariable UUID sectionId,
                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                   @Valid @RequestBody EnrollBody body) {
        if (idempotencyKey.isBlank()) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REQUIRED", "요청 키가 필요합니다.");
        }
        return enrollmentService.enroll(sectionId, idempotencyKey,
                new EnrollRequest(body.studentNumber()));
    }

    @DeleteMapping("/sections/{sectionId}/enrollments/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void drop(@PathVariable UUID sectionId, @RequestParam @NotBlank String studentNumber) {
        enrollmentService.drop(sectionId, studentNumber);
    }

    public record EnrollBody(@NotBlank String studentNumber) {}
}

