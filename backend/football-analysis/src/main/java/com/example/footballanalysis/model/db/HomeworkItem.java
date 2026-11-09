package com.example.footballanalysis.model.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "homework_items",
        indexes = {
                @Index(name = "ix_homework_items_homework", columnList = "homework_id, position")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class HomeworkItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "homework_id", nullable = false, foreignKey = @ForeignKey(name = "fk_homework_items_homework"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Homework homework;

    @Column(name = "position", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private HomeworkItemType type;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clip_id", foreignKey = @ForeignKey(name = "fk_homework_items_clip"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Clip clip;

    @Column(name = "focus_ms")
    private Integer focusMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_kind", length = 20)
    private HomeworkQuestionKind questionKind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options")
    private List<String> options;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correct_options")
    private List<Integer> correctOptions;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    /** A TIMESTAMP kérdés klipblokkja ugyanebben a leckében. A sorrend nem elég, mert a blokkok átrendezhetők. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ref_item_id", foreignKey = @ForeignKey(name = "fk_homework_items_ref"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private HomeworkItem refItem;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
