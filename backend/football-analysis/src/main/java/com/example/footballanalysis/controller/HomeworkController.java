package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.requests.AddHomeworkAssigneesRequest;
import com.example.footballanalysis.model.requests.PublishHomeworkRequest;
import com.example.footballanalysis.model.requests.UpsertHomeworkRequest;
import com.example.footballanalysis.model.responses.AssignmentRowResponse;
import com.example.footballanalysis.model.responses.ClipHomeworkUsageResponse;
import com.example.footballanalysis.model.responses.HomeworkDetailResponse;
import com.example.footballanalysis.model.responses.HomeworkRecipientResponse;
import com.example.footballanalysis.model.responses.HomeworkSummaryResponse;
import com.example.footballanalysis.model.responses.PageResponse;
import com.example.footballanalysis.service.HomeworkContent;
import com.example.footballanalysis.service.HomeworkRecipients;
import com.example.footballanalysis.service.HomeworkService;
import com.example.footballanalysis.service.UserAccessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
@Tag(name = "Homework", description = "Házi feladat, edzői oldal")
public class HomeworkController {

    private final HomeworkService homeworkService;
    private final HomeworkRecipients homeworkRecipients;
    private final HomeworkContent homeworkContent;
    private final UserAccessService userAccessService;

    @GetMapping("/teams/{teamId}/homeworks")
    public PageResponse<HomeworkSummaryResponse> list(
            @PathVariable UUID teamId,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkService.list(teamId, tab, page, size, userAccessService.resolveCurrentUser(jwt));
    }

    @PostMapping("/teams/{teamId}/homeworks")
    public HomeworkDetailResponse create(
            @PathVariable UUID teamId,
            @Valid @RequestBody UpsertHomeworkRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkService.create(teamId, request, userAccessService.resolveCurrentUser(jwt));
    }

    @GetMapping("/teams/{teamId}/homeworks/{homeworkId}")
    public HomeworkDetailResponse get(
            @PathVariable UUID teamId,
            @PathVariable UUID homeworkId,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkService.get(teamId, homeworkId, userAccessService.resolveCurrentUser(jwt));
    }

    /** For a link that knows only the homework. The answer carries the team. */
    @GetMapping("/homeworks/{homeworkId}")
    public HomeworkDetailResponse getById(@PathVariable UUID homeworkId, @AuthenticationPrincipal Jwt jwt) {
        return homeworkService.get(homeworkId, userAccessService.resolveCurrentUser(jwt));
    }

    @PutMapping("/teams/{teamId}/homeworks/{homeworkId}")
    public HomeworkDetailResponse update(
            @PathVariable UUID teamId,
            @PathVariable UUID homeworkId,
            @Valid @RequestBody UpsertHomeworkRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkService.update(teamId, homeworkId, request, userAccessService.resolveCurrentUser(jwt));
    }

    @DeleteMapping("/teams/{teamId}/homeworks/{homeworkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID teamId,
            @PathVariable UUID homeworkId,
            @AuthenticationPrincipal Jwt jwt) {
        homeworkService.delete(teamId, homeworkId, userAccessService.resolveCurrentUser(jwt));
    }

    @PostMapping("/teams/{teamId}/homeworks/{homeworkId}/publish")
    public HomeworkDetailResponse publish(
            @PathVariable UUID teamId,
            @PathVariable UUID homeworkId,
            @Valid @RequestBody PublishHomeworkRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkService.publish(teamId, homeworkId, request, userAccessService.resolveCurrentUser(jwt));
    }

    @PostMapping("/teams/{teamId}/homeworks/{homeworkId}/archive")
    public HomeworkDetailResponse archive(
            @PathVariable UUID teamId,
            @PathVariable UUID homeworkId,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkService.archive(teamId, homeworkId, userAccessService.resolveCurrentUser(jwt));
    }

    @PostMapping("/teams/{teamId}/homeworks/{homeworkId}/duplicate")
    public HomeworkDetailResponse duplicate(
            @PathVariable UUID teamId,
            @PathVariable UUID homeworkId,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkService.duplicate(teamId, homeworkId, userAccessService.resolveCurrentUser(jwt));
    }

    @PostMapping("/teams/{teamId}/homeworks/{homeworkId}/assignees")
    public List<AssignmentRowResponse> addAssignees(
            @PathVariable UUID teamId,
            @PathVariable UUID homeworkId,
            @Valid @RequestBody AddHomeworkAssigneesRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkRecipients.addAssignees(teamId, homeworkId, request, userAccessService.resolveCurrentUser(jwt));
    }

    @DeleteMapping("/teams/{teamId}/homeworks/{homeworkId}/assignees/{playerProfileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAssignee(
            @PathVariable UUID teamId,
            @PathVariable UUID homeworkId,
            @PathVariable UUID playerProfileId,
            @AuthenticationPrincipal Jwt jwt) {
        homeworkRecipients.removeAssignee(teamId, homeworkId, playerProfileId, userAccessService.resolveCurrentUser(jwt));
    }

    @GetMapping("/teams/{teamId}/homework-recipients")
    public List<HomeworkRecipientResponse> listRecipients(
            @PathVariable UUID teamId,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkRecipients.listRecipients(teamId, userAccessService.resolveCurrentUser(jwt));
    }

    @GetMapping("/clips/{clipId}/homework-usage")
    public ClipHomeworkUsageResponse clipUsage(@PathVariable UUID clipId, @AuthenticationPrincipal Jwt jwt) {
        return new ClipHomeworkUsageResponse(homeworkContent.clipUsage(clipId, userAccessService.resolveCurrentUser(jwt)));
    }

    @GetMapping("/teams/{teamId}/homeworks/{homeworkId}/assignments")
    public List<AssignmentRowResponse> listAssignments(
            @PathVariable UUID teamId,
            @PathVariable UUID homeworkId,
            @AuthenticationPrincipal Jwt jwt) {
        return homeworkRecipients.listAssignments(teamId, homeworkId, userAccessService.resolveCurrentUser(jwt));
    }
}
