package com.example.footballanalysis.model.db;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(
        name = "homework_answers",
        indexes = {
                @Index(name = "ix_homework_answers_item", columnList = "item_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class HomeworkAnswer {

    @EmbeddedId
    private HomeworkAnswerId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("assignmentId")
    @JoinColumn(name = "assignment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_homework_answers_assignment"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private HomeworkAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("itemId")
    @JoinColumn(name = "item_id", nullable = false, foreignKey = @ForeignKey(name = "fk_homework_answers_item"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private HomeworkItem item;

    @Column(name = "text_answer", columnDefinition = "TEXT")
    private String textAnswer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_options")
    private List<Integer> selectedOptions;

    @Column(name = "timestamp_ms")
    private Integer timestampMs;
}
