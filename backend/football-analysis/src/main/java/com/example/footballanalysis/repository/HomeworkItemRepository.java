package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.HomeworkItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HomeworkItemRepository extends JpaRepository<HomeworkItem, UUID> {

    @Query("""
            select item from HomeworkItem item
            left join fetch item.clip
            where item.homework.id = :homeworkId
            order by item.position asc, item.id asc
            """)
    List<HomeworkItem> findWithClipByHomeworkId(@Param("homeworkId") UUID homeworkId);

    @Query("select count(distinct item.homework.id) from HomeworkItem item where item.clip.id = :clipId")
    long countHomeworksUsingClip(@Param("clipId") UUID clipId);
}
