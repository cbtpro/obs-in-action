package com.example.taskservice.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 候选人 Mapper：批量插入 + 按标志删除/统计模拟数据。
 */
@Mapper
public interface CandidateMapper extends BaseMapper<CandidateRecord> {

    /**
     * 批量插入模拟数据。
     */
    @Insert({
            "<script>",
            "INSERT IGNORE INTO candidate (name, id_card, gender, age, bio, mock_tag, created_at, updated_at) VALUES",
            "<foreach collection='records' item='r' separator=','>",
            "(#{r.name}, #{r.idCard}, #{r.gender}, #{r.age}, #{r.bio}, #{r.mockTag}, #{r.createdAt}, #{r.updatedAt})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("records") List<CandidateRecord> records);

    /**
     * 按数据标志删除模拟数据。
     */
    @Delete("DELETE FROM candidate WHERE mock_tag = #{mockTag}")
    int deleteByMockTag(@Param("mockTag") String mockTag);

    /**
     * 按数据标志统计条数。
     */
    @Select("SELECT COUNT(*) FROM candidate WHERE mock_tag = #{mockTag}")
    long countByMockTag(@Param("mockTag") String mockTag);
}
