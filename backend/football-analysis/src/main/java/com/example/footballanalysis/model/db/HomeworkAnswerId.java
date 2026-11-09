package com.example.footballanalysis.model.db;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class HomeworkAnswerId implements Serializable {

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "item_id")
    private UUID itemId;
}
