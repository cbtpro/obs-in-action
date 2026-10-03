package com.example.demo.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 候选人 MyBatis-Plus Mapper。
 */
@Mapper
public interface CandidateMapper extends BaseMapper<CandidateRecord> {
}
