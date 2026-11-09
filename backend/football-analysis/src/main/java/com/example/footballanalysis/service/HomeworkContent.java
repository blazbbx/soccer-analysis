package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Homework;
import com.example.footballanalysis.model.db.HomeworkItem;
import com.example.footballanalysis.model.db.HomeworkItemType;
import com.example.footballanalysis.model.db.HomeworkQuestionKind;
import com.example.footballanalysis.model.db.HomeworkStatus;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.HomeworkItemRequest;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.HomeworkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.example.footballanalysis.service.HomeworkGuard.blankToNull;

/** The blocks of a homework: keeping them in line with the editor, checking their clips, and copying them. */
@Service
@RequiredArgsConstructor
public class HomeworkContent {

    private static final Set<String> CLIP_NOT_READY = Set.of(
            ClipService.RENDER_STATUS_PENDING_UPLOAD,
            ClipService.RENDER_STATUS_FAILED);

    private final HomeworkItemRepository itemRepository;
    private final ClipRepository clipRepository;
    private final UserAccessService userAccessService;

    /** How many homeworks a clip's deletion would touch. Only who may delete the clip asks. */
    @Transactional(readOnly = true)
    public long clipUsage(UUID clipId, User actor) {
        userAccessService.requireClipManageAccess(actor, clipId);
        return itemRepository.countHomeworksUsingClip(clipId);
    }

    public Map<UUID, HomeworkItem> questionsOf(UUID homeworkId) {
        Map<UUID, HomeworkItem> questions = new HashMap<>();
        for (HomeworkItem item : itemRepository.findWithClipByHomeworkId(homeworkId)) {
            if (item.getType() == HomeworkItemType.QUESTION) {
                questions.put(item.getId(), item);
            }
        }
        return questions;
    }

    /**
     * Makes the stored items match the request, which is the whole list in its order. The list comes in two rounds,
     * because a TIMESTAMP question may point to a clip item later in the same request.
     *
     * @return whether anything changed
     */
    public boolean sync(Homework homework, List<HomeworkItemRequest> requests, List<HomeworkItem> stored, boolean locked, User actor) {
        Map<UUID, HomeworkItem> storedById = new HashMap<>();
        stored.forEach(item -> storedById.put(item.getId(), item));
        Map<UUID, HomeworkItem> keptById = new HashMap<>();
        Map<String, HomeworkItem> newByClientKey = new HashMap<>();
        List<HomeworkItem> inOrder = new ArrayList<>();
        boolean changed = false;

        for (int index = 0; index < requests.size(); index++) {
            HomeworkItemRequest request = requests.get(index);
            ItemFields fields = ItemFields.of(request);
            HomeworkItem item;
            if (request.id() != null) {
                item = storedById.get(request.id());
                if (item == null || keptById.put(request.id(), item) != null || item.getType() != request.type()) {
                    throw invalidItem("The item does not belong to this homework, or it changed its type.");
                }
                if (fields.type() == HomeworkItemType.CLIP && request.clipId() == null && item.getClip() != null) {
                    throw invalidItem("A clip item needs its clip.");
                }
            } else {
                if (fields.type() == HomeworkItemType.CLIP && request.clipId() == null) {
                    throw invalidItem("A new clip item needs a clip.");
                }
                item = new HomeworkItem();
                item.setHomework(homework);
                item.setType(fields.type());
                if (request.clientKey() != null && newByClientKey.containsKey(request.clientKey())) {
                    throw invalidItem("Two new items have the same key.");
                }
            }
            boolean isNew = request.id() == null;
            boolean clipChanged = !Objects.equals(clipIdOf(item), request.clipId()) && fields.type() == HomeworkItemType.CLIP;
            if (!isNew && locked && (fields.differsInLockedField(item) || clipChanged)) {
                throw itemsLocked();
            }
            if (clipChanged) {
                item.setClip(requireUsableClip(request.clipId(), homework, actor));
            }
            changed |= isNew || clipChanged || index != item.getPosition() || fields.differs(item);
            fields.applyTo(item);
            item.setPosition(index);
            if (isNew) {
                itemRepository.save(item);
                if (request.clientKey() != null) {
                    newByClientKey.put(request.clientKey(), item);
                }
            }
            inOrder.add(item);
        }

        for (int index = 0; index < requests.size(); index++) {
            HomeworkItem target = resolveRef(requests.get(index), keptById, newByClientKey);
            HomeworkItem item = inOrder.get(index);
            if (!Objects.equals(idOf(item.getRefItem()), idOf(target))) {
                if (locked && requests.get(index).id() != null) {
                    throw itemsLocked();
                }
                item.setRefItem(target);
                changed = true;
            }
        }

        List<HomeworkItem> removed = stored.stream().filter(item -> !keptById.containsKey(item.getId())).toList();
        if (!removed.isEmpty()) {
            if (locked) {
                throw itemsLocked();
            }
            itemRepository.deleteAll(removed);
            changed = true;
        }
        return changed;
    }

    /** A clip still rendering may go out: the recipient sees a placeholder until it is done. */
    public static void requireClipReady(Clip clip) {
        if (clip != null && CLIP_NOT_READY.contains(clip.getRenderStatus())) {
            throw new BadRequestException("error.homework.clip_not_ready", new Object[0], "A clip of this homework is not ready.");
        }
    }

    /** A clip item whose clip is gone, and a timestamp question pointing to one, are left out of a copy. */
    public static boolean survivesCopy(HomeworkItem item, List<HomeworkItem> all) {
        if (item.getType() == HomeworkItemType.CLIP) {
            return item.getClip() != null;
        }
        if (item.getQuestionKind() != HomeworkQuestionKind.TIMESTAMP) {
            return true;
        }
        UUID refId = idOf(item.getRefItem());
        return all.stream().anyMatch(other -> other.getId().equals(refId) && other.getClip() != null);
    }

    public void copy(Homework copy, List<HomeworkItem> kept) {
        Map<UUID, HomeworkItem> copied = new HashMap<>();
        int position = 0;
        for (HomeworkItem source : kept) {
            HomeworkItem item = new HomeworkItem();
            item.setHomework(copy);
            item.setPosition(position++);
            item.setType(source.getType());
            item.setBody(source.getBody());
            item.setClip(source.getClip());
            item.setFocusMs(source.getFocusMs());
            item.setQuestionKind(source.getQuestionKind());
            item.setOptions(source.getOptions() == null ? null : new ArrayList<>(source.getOptions()));
            item.setCorrectOptions(source.getCorrectOptions() == null ? null : new ArrayList<>(source.getCorrectOptions()));
            item.setRequired(source.isRequired());
            itemRepository.save(item);
            copied.put(source.getId(), item);
        }
        for (HomeworkItem source : kept) {
            if (source.getRefItem() != null) {
                copied.get(source.getId()).setRefItem(copied.get(source.getRefItem().getId()));
            }
        }
    }

    private static HomeworkItem resolveRef(
            HomeworkItemRequest request, Map<UUID, HomeworkItem> keptById, Map<String, HomeworkItem> newByClientKey) {
        if (request.questionKind() != HomeworkQuestionKind.TIMESTAMP || request.type() != HomeworkItemType.QUESTION) {
            return null;
        }
        if ((request.refItemId() == null) == (request.refClientKey() == null)) {
            throw timestampRef();
        }
        HomeworkItem target = request.refItemId() != null
                ? keptById.get(request.refItemId())
                : newByClientKey.get(request.refClientKey());
        if (target == null || target.getType() != HomeworkItemType.CLIP) {
            throw timestampRef();
        }
        return target;
    }

    /** A new or changed clip: only one of the team's own matches, so the item never opens a foreign clip to the team. */
    private Clip requireUsableClip(UUID clipId, Homework homework, User actor) {
        Clip clip = clipRepository.findById(clipId).orElseThrow(() -> new NotFoundException(
                "error.homework.clip_not_found",
                new Object[]{clipId},
                "Clip not found."));
        userAccessService.requireClipForTeamHomework(actor, clipId, homework.getTeam().getId());
        if (homework.getStatus() != HomeworkStatus.DRAFT) {
            requireClipReady(clip);
        }
        return clip;
    }

    private static UUID clipIdOf(HomeworkItem item) {
        return item.getClip() != null ? item.getClip().getId() : null;
    }

    private static UUID idOf(HomeworkItem item) {
        return item != null ? item.getId() : null;
    }

    private static ConflictException itemsLocked() {
        return new ConflictException(
                "error.homework.items_locked",
                new Object[0],
                "Work was already handed in, so this item cannot change. Make a copy instead.");
    }

    private static BadRequestException invalidItem(String fallback) {
        return new BadRequestException("error.homework.invalid_item", new Object[0], fallback);
    }

    private static BadRequestException timestampRef() {
        return new BadRequestException(
                "error.homework.timestamp_ref",
                new Object[0],
                "A timestamp question needs exactly one clip item of this homework.");
    }

    /**
     * What a request says about an item, after the block rules. A field the type does not use is dropped, because an
     * editor may leave one behind when its kind is switched.
     */
    private record ItemFields(
            HomeworkItemType type,
            String body,
            Integer focusMs,
            HomeworkQuestionKind questionKind,
            List<String> options,
            List<Integer> correctOptions,
            boolean required) {

        static ItemFields of(HomeworkItemRequest request) {
            return switch (request.type()) {
                case TEXT -> {
                    requireBody(request);
                    yield new ItemFields(HomeworkItemType.TEXT, request.body(), null, null, null, null, true);
                }
                case CLIP -> new ItemFields(
                        HomeworkItemType.CLIP, blankToNull(request.body()), request.focusMs(), null, null, null, true);
                case QUESTION -> question(request);
            };
        }

        private static ItemFields question(HomeworkItemRequest request) {
            requireBody(request);
            HomeworkQuestionKind kind = request.questionKind();
            if (kind == null) {
                throw invalidItem("A question needs a kind.");
            }
            boolean required = request.required() == null || request.required();
            if (kind != HomeworkQuestionKind.SINGLE_CHOICE && kind != HomeworkQuestionKind.MULTI_CHOICE) {
                return new ItemFields(HomeworkItemType.QUESTION, request.body(), null, kind, null, null, required);
            }
            List<String> options = request.options();
            if (options == null || options.size() < 2) {
                throw invalidItem("A choice question needs at least two options.");
            }
            List<Integer> given = request.correctOptions() == null ? List.of() : request.correctOptions();
            if (given.stream().anyMatch(index -> index == null || index < 0 || index >= options.size())
                    || new HashSet<>(given).size() != given.size()) {
                throw new BadRequestException("error.homework.invalid_option", new Object[0], "A correct option is not on the list.");
            }
            List<Integer> correct = given.stream().sorted().toList();
            if (kind == HomeworkQuestionKind.SINGLE_CHOICE ? correct.size() != 1 : correct.isEmpty()) {
                throw invalidItem("The question has the wrong number of correct options.");
            }
            return new ItemFields(HomeworkItemType.QUESTION, request.body(), null, kind, List.copyOf(options), correct, required);
        }

        private static void requireBody(HomeworkItemRequest request) {
            if (request.body() == null || request.body().isBlank()) {
                throw invalidItem("The item needs a text.");
            }
        }

        /** The fields whose change would make an answer already handed in mean something else. */
        boolean differsInLockedField(HomeworkItem item) {
            return switch (type) {
                case TEXT -> false;
                case CLIP -> !Objects.equals(focusMs, item.getFocusMs());
                case QUESTION -> !Objects.equals(body, item.getBody())
                        || questionKind != item.getQuestionKind()
                        || !Objects.equals(options, item.getOptions())
                        || !Objects.equals(correctOptions, item.getCorrectOptions())
                        || required != item.isRequired();
            };
        }

        boolean differs(HomeworkItem item) {
            return !Objects.equals(body, item.getBody()) || differsInLockedField(item);
        }

        void applyTo(HomeworkItem item) {
            item.setBody(body);
            item.setFocusMs(focusMs);
            item.setQuestionKind(questionKind);
            item.setOptions(options);
            item.setCorrectOptions(correctOptions);
            item.setRequired(required);
        }
    }
}
