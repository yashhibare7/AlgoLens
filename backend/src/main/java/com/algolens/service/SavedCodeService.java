package com.algolens.service;

import com.algolens.dto.PageResponse;
import com.algolens.dto.code.SavedCodeRequest;
import com.algolens.dto.code.SavedCodeResponse;
import com.algolens.entity.Language;
import com.algolens.entity.Problem;
import com.algolens.entity.SavedCode;
import com.algolens.entity.User;
import com.algolens.exception.BadRequestException;
import com.algolens.exception.NotFoundException;
import com.algolens.repository.SavedCodeRepository;
import com.algolens.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedCodeService {

    private final SavedCodeRepository savedCode;
    private final UserRepository users;
    private final ProblemService problemService;

    public SavedCodeService(SavedCodeRepository savedCode, UserRepository users,
            ProblemService problemService) {
        this.savedCode = savedCode;
        this.users = users;
        this.problemService = problemService;
    }

    @Transactional(readOnly = true)
    public PageResponse<SavedCodeResponse> list(Long userId, Pageable pageable) {
        return PageResponse.of(savedCode.findByUserIdOrderByUpdatedAtDesc(userId, pageable),
                SavedCodeResponse::summary);
    }

    @Transactional(readOnly = true)
    public SavedCodeResponse get(Long userId, Long id) {
        return SavedCodeResponse.from(require(userId, id));
    }

    @Transactional
    public SavedCodeResponse create(Long userId, SavedCodeRequest request) {
        User user = users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        SavedCode entity = new SavedCode(user, resolveProblem(request.problemId()),
                request.title().trim(), language(request.language()), request.code());
        return SavedCodeResponse.from(savedCode.save(entity));
    }

    @Transactional
    public SavedCodeResponse update(Long userId, Long id, SavedCodeRequest request) {
        SavedCode entity = require(userId, id);
        entity.setTitle(request.title().trim());
        entity.setLanguage(language(request.language()));
        entity.setCode(request.code());
        entity.setProblem(resolveProblem(request.problemId()));
        return SavedCodeResponse.from(savedCode.save(entity));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        savedCode.delete(require(userId, id));
    }

    /**
     * Always scoped by user id, never by id alone. Loading by id and then comparing owners is
     * how insecure-direct-object-reference bugs happen; making ownership part of the query means
     * there is no code path where the check can be forgotten.
     */
    private SavedCode require(Long userId, Long id) {
        return savedCode.findByIdAndUserId(id, userId)
                .orElseThrow(() -> NotFoundException.of("Saved snippet", id));
    }

    private Problem resolveProblem(Long problemId) {
        return problemId == null ? null : problemService.requireById(problemId);
    }

    private static Language language(String raw) {
        return Language.fromString(raw)
                .orElseThrow(() -> new BadRequestException("Unknown language: " + raw));
    }
}
