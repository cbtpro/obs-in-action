package com.example.demo.interfaces.rest;

import com.example.demo.application.CandidateApplicationService;
import com.example.demo.domain.model.Candidate;
import com.example.demo.domain.model.PageResult;
import com.example.demo.interfaces.rest.dto.CandidateDTO;
import com.example.demo.interfaces.rest.dto.CandidateListRequest;
import com.example.demo.interfaces.rest.dto.PageDTO;
import com.example.demo.interfaces.rest.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 面试候选人 REST 接口。
 * 提供列表、新增、修改、删除；性别和年龄由后端从身份证号自动解析。
 */
@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateApplicationService candidateApplicationService;

    /**
     * 候选人分页列表。支持按姓名、身份证号模糊搜索，gender 过滤性别。
     * 查询条件含身份证号等敏感信息，通过 POST 请求体传递，避免出现在 URL 中。
     */
    @PostMapping("/list")
    public Result<PageDTO<CandidateDTO>> list(@RequestBody CandidateListRequest request) {
        PageResult<Candidate> result = candidateApplicationService.list(
                request.name(), request.idCard(), request.gender(),
                request.safePage(), request.safePageSize());
        List<CandidateDTO> dtos = result.records().stream().map(this::toDTO).toList();
        return Result.success(new PageDTO<>(result.total(), dtos));
    }

    /**
     * 候选人详情。
     */
    @GetMapping("/{id}")
    public Result<CandidateDTO> getById(@PathVariable Long id) {
        return Result.success(toDTO(candidateApplicationService.getById(id)));
    }

    /**
     * 新增候选人。
     * 照片、简历为可选文件；性别、年龄由身份证号自动填充。
     */
    @PostMapping(consumes = "multipart/form-data")
    public Result<CandidateDTO> create(
            @RequestParam String name,
            @RequestParam String idCard,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) MultipartFile photo,
            @RequestParam(required = false) MultipartFile resume) {
        Candidate candidate = candidateApplicationService.create(name, idCard, bio, photo, resume);
        return Result.success(toDTO(candidate));
    }

    /**
     * 修改候选人。
     * 姓名、身份证号、简介为可选（不传则保留原值）；照片/简历传入则替换。
     */
    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public Result<CandidateDTO> update(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String idCard,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) MultipartFile photo,
            @RequestParam(required = false) MultipartFile resume) {
        Candidate candidate = candidateApplicationService.update(id, name, idCard, bio, photo, resume);
        return Result.success(toDTO(candidate));
    }

    /**
     * 删除候选人。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        candidateApplicationService.delete(id);
        return Result.success();
    }

    private CandidateDTO toDTO(Candidate c) {
        return CandidateDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .idCard(c.getIdCard())
                .gender(c.getGender())
                .age(c.getAge())
                .photoUrl(candidateApplicationService.getAccessUrl(c.getPhotoKey()))
                .photoName(c.getPhotoName())
                .resumeUrl(candidateApplicationService.getAccessUrl(c.getResumeKey()))
                .resumeName(c.getResumeName())
                .bio(c.getBio())
                .createdAt(CandidateApplicationService.formatDateTime(c.getCreatedAt()))
                .updatedAt(CandidateApplicationService.formatDateTime(c.getUpdatedAt()))
                .build();
    }
}
