package com.example.demo.domain.repository;

import com.example.demo.domain.model.Candidate;
import com.example.demo.domain.model.CandidateQuery;
import com.example.demo.domain.model.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 候选人领域仓储接口。
 * 定义领域层对持久化的需求，由基础设施层（MyBatis-Plus）实现。
 * 常规查询一律过滤软删数据；软删除打标与物理清理由专用方法承接。
 */
public interface CandidateRepository {

    Candidate save(Candidate candidate);

    /** 仅查未删除的候选人（已软删的视为不存在） */
    Optional<Candidate> findById(Long id);

    /** 按条件分页查询（过滤条件在数据库执行，不含软删数据） */
    PageResult<Candidate> findPage(CandidateQuery query);

    /** 软删除打标（deleted_at） */
    void softDeleteById(Long id, LocalDateTime deletedAt);

    /** 软删除时间早于阈值的记录（供物理清理任务扫描） */
    List<Candidate> findDeletedBefore(LocalDateTime threshold);

    /** 物理删除行（仅清理任务使用，先删对象存储再调用） */
    void hardDeleteById(Long id);

    /**
     * 按身份证号查找（唯一性校验用）。
     * 注意：不过滤软删数据——id_card 有唯一索引，软删行在物理清理前仍占用该号码。
     */
    Optional<Candidate> findByIdCard(String idCard);
}
