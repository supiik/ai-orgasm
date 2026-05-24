package com.orgasm.backend.organization;

import com.orgasm.billing.domain.Organization;
import com.orgasm.billing.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/organizations", version = "1")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationRepository organizationRepository;

    @GetMapping
    public ResponseEntity<List<Organization>> findAll() {
        return ResponseEntity.ok(organizationRepository.findAll());
    }
}
