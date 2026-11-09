package com.example.footballanalysis.model.db;

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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
        name = "homework_draft_recipients",
        indexes = {
                @Index(name = "ix_homework_draft_recipients_profile", columnList = "player_profile_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class HomeworkDraftRecipient {

    @EmbeddedId
    private HomeworkDraftRecipientId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("homeworkId")
    @JoinColumn(name = "homework_id", nullable = false, foreignKey = @ForeignKey(name = "fk_homework_draft_recipients_homework"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Homework homework;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("playerProfileId")
    @JoinColumn(name = "player_profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_homework_draft_recipients_profile"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlayerProfile playerProfile;
}
