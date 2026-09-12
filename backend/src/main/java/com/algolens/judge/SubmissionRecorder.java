package com.algolens.judge;

import com.algolens.entity.Language;
import com.algolens.entity.Problem;
import com.algolens.entity.Submission;
import com.algolens.entity.User;
import com.algolens.exception.NotFoundException;
import com.algolens.repository.SubmissionRepository;
import com.algolens.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a judged submission to history. A separate bean from {@link JudgeService} so
 * {@code @Transactional} actually applies -- see {@code ExecutionRecorder} for why.
 */
@Service
public class SubmissionRecorder {

    private static final int MAX_ERROR_MESSAGE = 4000;

    private final SubmissionRepository submissions;
    private final UserRepository users;

    public SubmissionRecorder(SubmissionRepository submissions, UserRepository users) {
        this.submissions = submissions;
        this.users = users;
    }

    @Transactional
    public Long record(Long userId, Problem problem, String code, JudgeResult result) {
        User user = users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        Submission entity = new Submission(user, problem, Language.JAVA, code, result.verdict(),
                result.passedCount(), result.totalCount(), truncate(result.errorMessage()),
                result.durationMs());
        return submissions.save(entity).getId();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_MESSAGE) {
            return value;
        }
        return value.substring(0, MAX_ERROR_MESSAGE - 3) + "...";
    }
}
