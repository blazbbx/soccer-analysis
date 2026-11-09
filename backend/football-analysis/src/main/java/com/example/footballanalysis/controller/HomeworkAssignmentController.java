package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.CommentRequest;
import com.example.footballanalysis.model.requests.ReturnAssignmentRequest;
import com.example.footballanalysis.model.requests.ReviewAssignmentRequest;
import com.example.footballanalysis.model.requests.SubmitAssignmentRequest;
import com.example.footballanalysis.model.responses.AssignmentResponse;
import com.example.footballanalysis.model.responses.HomeworkCommentResponse;
import com.example.footballanalysis.model.responses.HomeworkSummaryCountsResponse;
import com.example.footballanalysis.model.responses.MyHomeworkResponse;
import com.example.footballanalysis.model.responses.PageResponse;
import com.example.footballanalysis.service.HomeworkAnswers;
import com.example.footballanalysis.service.HomeworkAssignmentViews;
import com.example.footballanalysis.service.HomeworkComments;
import com.example.footballanalysis.service.HomeworkReview;
import com.example.footballanalysis.service.UserAccessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Homework assignments", description = "Házi feladat, címzetti oldal és értékelés")
public class HomeworkAssignmentController {

    private final HomeworkAssignmentViews assignmentViews;
    private final HomeworkAnswers homeworkAnswers;
    private final HomeworkReview homeworkReview;
    private final HomeworkComments homeworkComments;
    private final UserAccessService userAccessService;

    @GetMapping("/me/homeworks")
    public PageResponse<MyHomeworkResponse> listMine(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) UUID profileId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal Jwt jwt) {
        return assignmentViews.listMine(tab, profileId, page, size, userAccessService.resolveCurrentUser(jwt));
    }

    @GetMapping("/me/homeworks/summary")
    public HomeworkSummaryCountsResponse summary(
            @RequestParam(required = false) String view,
            @AuthenticationPrincipal Jwt jwt) {
        return assignmentViews.summary(view, userAccessService.resolveCurrentUser(jwt));
    }

    @GetMapping("/homework-assignments/{assignmentId}")
    public AssignmentResponse get(@PathVariable UUID assignmentId, @AuthenticationPrincipal Jwt jwt) {
        return assignmentViews.get(assignmentId, userAccessService.resolveCurrentUser(jwt));
    }

    @PostMapping("/homework-assignments/{assignmentId}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markViewed(@PathVariable UUID assignmentId, @AuthenticationPrincipal Jwt jwt) {
        homeworkAnswers.markViewed(assignmentId, userAccessService.resolveCurrentUser(jwt));
    }

    @PostMapping("/homework-assignments/{assignmentId}/submit")
    public AssignmentResponse submit(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody SubmitAssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkAnswers.submit(assignmentId, request, userAccessService.resolveCurrentUser(jwt));
    }

    @PostMapping("/homework-assignments/{assignmentId}/review")
    public AssignmentResponse review(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody ReviewAssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkReview.review(assignmentId, request, userAccessService.resolveCurrentUser(jwt));
    }

    @PostMapping("/homework-assignments/{assignmentId}/return")
    public AssignmentResponse returnForRevision(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody ReturnAssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkReview.returnForRevision(assignmentId, request, userAccessService.resolveCurrentUser(jwt));
    }

    @GetMapping("/homework-assignments/{assignmentId}/comments")
    public List<HomeworkCommentResponse> listComments(@PathVariable UUID assignmentId, @AuthenticationPrincipal Jwt jwt) {
        return homeworkComments.listComments(assignmentId, userAccessService.resolveCurrentUser(jwt));
    }

    @PostMapping("/homework-assignments/{assignmentId}/comments")
    public HomeworkCommentResponse addComment(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkComments.addComment(assignmentId, request, userAccessService.resolveCurrentUser(jwt));
    }
}
