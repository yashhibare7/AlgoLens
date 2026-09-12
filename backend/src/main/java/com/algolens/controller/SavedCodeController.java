package com.algolens.controller;

import com.algolens.dto.PageResponse;
import com.algolens.dto.code.SavedCodeRequest;
import com.algolens.dto.code.SavedCodeResponse;
import com.algolens.security.AuthUser;
import com.algolens.service.SavedCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/saved-code")
@Tag(name = "Saved code")
public class SavedCodeController {

    private final SavedCodeService savedCodeService;

    public SavedCodeController(SavedCodeService savedCodeService) {
        this.savedCodeService = savedCodeService;
    }

    @GetMapping
    @Operation(summary = "List my saved snippets (titles only)")
    public PageResponse<SavedCodeResponse> list(@AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return savedCodeService.list(user.id(), Pagination.of(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Load one saved snippet with its code")
    public SavedCodeResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return savedCodeService.get(user.id(), id);
    }

    @PostMapping
    @Operation(summary = "Save a snippet")
    public ResponseEntity<SavedCodeResponse> create(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody SavedCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedCodeService.create(user.id(), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a saved snippet")
    public SavedCodeResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
            @Valid @RequestBody SavedCodeRequest request) {
        return savedCodeService.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a saved snippet")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user,
            @PathVariable Long id) {
        savedCodeService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
