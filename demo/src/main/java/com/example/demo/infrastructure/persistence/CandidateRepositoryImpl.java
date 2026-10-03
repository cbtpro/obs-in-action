package com.example.demo.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.domain.model.Candidate;
import com.example.demo.domain.model.CandidateQuery;
import com.example.demo.domain.model.PageQuery;
import com.example.demo.domain.model.PageResult;
import com.example.demo.domain.repository.CandidateRepository;
import com.example.demo.infrastructure.persistence.mybatis.CandidateMapper;
import com.example.demo.infrastructure.persistence.mybatis.CandidateRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 候选人仓储的 MyBatis-Plus 实现。
 * 负责 Candidate ↔ CandidateRecord 的双向转换，对应用层暴露领域接口。
 * 常规查询一律附加 deleted_at IS NULL；软删打标与物理删除走专用方法。
 */
@Repository
@RequiredArgsConstructor
public class CandidateRepositoryImpl implements CandidateRepository {

    private final CandidateMapper candidateMapper;

    @Override
    public Candidate save(Candidate candidate) {
        CandidateRecord record = toRecord(candidate);
        if (record.getId() == null) {
            candidateMapper.insert(record);
            return toEntity(record);
        }
        // updateById 忽略 null 字段，不会覆盖已有 deleted_at
        candidateMapper.updateById(record);
        return toEntity(candidateMapper.selectById(record.getId()));
    }

    @Override
    public Optional<Candidate> findById(Long id) {
        LambdaQueryWrapper<CandidateRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CandidateRecord::getId, id)
                .isNull(CandidateRecord::getDeletedAt);
        CandidateRecord record = candidateMapper.selectOne(wrapper);
        return Optional.ofNullable(record).map(this::toEntity);
    }

    @Override
    public PageResult<Candidate> findPage(CandidateQuery query) {
        LambdaQueryWrapper<CandidateRecord> wrapper = new LambdaQueryWrapper<>();
        // 软删数据一律不出现在列表
        wrapper.isNull(CandidateRecord::getDeletedAt);
        // 过滤条件下推到 SQL，避免全表加载到内存过滤
        if (isNotBlank(query.getName())) {
            wrapper.like(CandidateRecord::getName, query.getName().trim());
        }
        if (isNotBlank(query.getIdCard())) {
            wrapper.like(CandidateRecord::getIdCard, query.getIdCard().trim());
        }
        if (isNotBlank(query.getGender())) {
            wrapper.eq(CandidateRecord::getGender, query.getGender());
        }
        // 分页必须保证稳定排序，否则翻页可能出现重复/丢失
        wrapper.orderByDesc(CandidateRecord::getId);

        PageQuery page = query.getPage();
        Page<CandidateRecord> result = candidateMapper.selectPage(
                Page.of(page.page(), page.pageSize()), wrapper);
        List<Candidate> records = result.getRecords().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), records);
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    @Override
    public void softDeleteById(Long id, LocalDateTime deletedAt) {
        // 打标即删除，幂等：已软删的不再刷新时间
        LambdaUpdateWrapper<CandidateRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(CandidateRecord::getDeletedAt, deletedAt)
                .set(CandidateRecord::getUpdatedAt, deletedAt)
                .eq(CandidateRecord::getId, id)
                .isNull(CandidateRecord::getDeletedAt);
        candidateMapper.update(null, wrapper);
    }

    @Override
    public List<Candidate> findDeletedBefore(LocalDateTime threshold) {
        LambdaQueryWrapper<CandidateRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(CandidateRecord::getDeletedAt)
                .lt(CandidateRecord::getDeletedAt, threshold);
        return candidateMapper.selectList(wrapper).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void hardDeleteById(Long id) {
        candidateMapper.deleteById(id);
    }

    @Override
    public Optional<Candidate> findByIdCard(String idCard) {
        // 不过滤软删：id_card 有唯一索引，软删行在物理清理前仍占用该号码
        LambdaQueryWrapper<CandidateRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CandidateRecord::getIdCard, idCard);
        CandidateRecord record = candidateMapper.selectOne(wrapper);
        return Optional.ofNullable(record).map(this::toEntity);
    }

    private CandidateRecord toRecord(Candidate c) {
        CandidateRecord r = new CandidateRecord();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setIdCard(c.getIdCard());
        r.setGender(c.getGender());
        r.setAge(c.getAge());
        r.setPhotoKey(c.getPhotoKey());
        r.setPhotoName(c.getPhotoName());
        r.setResumeKey(c.getResumeKey());
        r.setResumeName(c.getResumeName());
        r.setBio(c.getBio());
        r.setCreatedAt(c.getCreatedAt());
        r.setUpdatedAt(c.getUpdatedAt());
        // 不设置 deletedAt：软删状态只由 softDeleteById 维护
        return r;
    }

    private Candidate toEntity(CandidateRecord r) {
        return Candidate.builder()
                .id(r.getId())
                .name(r.getName())
                .idCard(r.getIdCard())
                .gender(r.getGender())
                .age(r.getAge())
                .photoKey(r.getPhotoKey())
                .photoName(r.getPhotoName())
                .resumeKey(r.getResumeKey())
                .resumeName(r.getResumeName())
                .bio(r.getBio())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .deletedAt(r.getDeletedAt())
                .build();
    }
}
