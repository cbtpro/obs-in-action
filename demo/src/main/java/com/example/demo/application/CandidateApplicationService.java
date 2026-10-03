package com.example.demo.application;

import com.example.demo.domain.model.Candidate;
import com.example.demo.domain.model.CandidateQuery;
import com.example.demo.domain.model.PageQuery;
import com.example.demo.domain.model.PageResult;
import com.example.demo.domain.repository.CandidateRepository;
import com.example.demo.interfaces.rest.exception.BizErrorCode;
import com.example.demo.interfaces.rest.exception.BizException;
import com.example.demo.util.IdCardUtil;
import com.example.obs.core.ObsStorage;
import com.example.obs.core.ObsUploadRequest;
import com.example.obs.core.util.FileNameUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 候选人用例编排服务（应用层）。
 * 编排身份证解析、对象存储上传、元数据持久化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateApplicationService {

    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CandidateRepository candidateRepository;
    private final ObsStorage obsStorage;
    private final com.example.demo.domain.FileKeyGenerator fileKeyGenerator;
    private final FileUploadPolicy uploadPolicy;
    private final ObsStorageCleaner obsStorageCleaner;

    /**
     * 新增候选人。
     * 性别、年龄从身份证号自动解析；照片、简历按各自策略校验后上传到对象存储。
     * 若对象存储上传成功但 DB 保存失败，补偿删除已上传对象。
     */
    @Transactional
    public Candidate create(String name, String idCard, String bio,
                            MultipartFile photo, MultipartFile resume) {
        // 身份证校验 + 自动填充性别、年龄
        validateIdCard(idCard);
        String gender = IdCardUtil.parseGender(idCard);
        int age = IdCardUtil.parseAge(idCard);

        // 身份证号唯一性校验
        candidateRepository.findByIdCard(idCard).ifPresent(c -> {
            throw new BizException(BizErrorCode.KEY_INVALID, "身份证号已存在");
        });

        FileRef photoRef = uploadFileIfPresent(photo, UploadProfile.PHOTO);
        FileRef resumeRef = uploadFileIfPresent(resume, UploadProfile.RESUME);

        Candidate candidate = Candidate.builder()
                .name(name)
                .idCard(idCard)
                .gender(gender)
                .age(age)
                .photoKey(photoRef != null ? photoRef.key() : null)
                .photoName(photoRef != null ? photoRef.name() : null)
                .resumeKey(resumeRef != null ? resumeRef.key() : null)
                .resumeName(resumeRef != null ? resumeRef.name() : null)
                .bio(bio)
                .build();
        try {
            return candidateRepository.save(candidate);
        } catch (Exception dbEx) {
            // DB 保存失败：补偿删除本次已上传的对象
            log.error("候选人保存失败，回滚已上传对象: name={}", name, dbEx);
            if (photoRef != null) {
                obsStorageCleaner.deleteQuietly(photoRef.key());
            }
            if (resumeRef != null) {
                obsStorageCleaner.deleteQuietly(resumeRef.key());
            }
            throw new BizException(BizErrorCode.QINIU_ERROR, "候选人保存失败");
        }
    }

    /**
     * 修改候选人。
     * 若身份证号变更，重新解析性别、年龄；照片/简历传入则替换旧文件。
     */
    @Transactional
    public Candidate update(Long id, String name, String idCard, String bio,
                            MultipartFile photo, MultipartFile resume) {
        Candidate existing = candidateRepository.findById(id)
                .orElseThrow(() -> new BizException(BizErrorCode.NOT_FOUND));

        // 身份证号变更时重新解析并校验唯一性
        String gender = existing.getGender();
        int age = existing.getAge();
        if (idCard != null && !idCard.equals(existing.getIdCard())) {
            validateIdCard(idCard);
            Optional<Candidate> dup = candidateRepository.findByIdCard(idCard);
            if (dup.isPresent() && !dup.get().getId().equals(id)) {
                throw new BizException(BizErrorCode.KEY_INVALID, "身份证号已存在");
            }
            gender = IdCardUtil.parseGender(idCard);
            age = IdCardUtil.parseAge(idCard);
        }

        // 照片替换：先上传新文件，保存成功后再清理旧文件（顺序不可颠倒，否则上传失败会丢旧文件）
        String photoKey = existing.getPhotoKey();
        String photoName = existing.getPhotoName();
        FileRef newPhotoRef = null;
        if (photo != null && !photo.isEmpty()) {
            newPhotoRef = uploadFileIfPresent(photo, UploadProfile.PHOTO);
            photoKey = newPhotoRef.key();
            photoName = newPhotoRef.name();
        }
        // 简历替换
        String resumeKey = existing.getResumeKey();
        String resumeName = existing.getResumeName();
        FileRef newResumeRef = null;
        if (resume != null && !resume.isEmpty()) {
            newResumeRef = uploadFileIfPresent(resume, UploadProfile.RESUME);
            resumeKey = newResumeRef.key();
            resumeName = newResumeRef.name();
        }

        Candidate candidate = Candidate.builder()
                .id(id)
                .name(name != null ? name : existing.getName())
                .idCard(idCard != null ? idCard : existing.getIdCard())
                .gender(gender)
                .age(age)
                .photoKey(photoKey)
                .photoName(photoName)
                .resumeKey(resumeKey)
                .resumeName(resumeName)
                .bio(bio != null ? bio : existing.getBio())
                .build();
        try {
            Candidate saved = candidateRepository.save(candidate);
            // 保存成功：提交后清理被替换的旧文件
            if (newPhotoRef != null) {
                obsStorageCleaner.deleteAfterCommit(existing.getPhotoKey());
            }
            if (newResumeRef != null) {
                obsStorageCleaner.deleteAfterCommit(existing.getResumeKey());
            }
            return saved;
        } catch (Exception dbEx) {
            // 保存失败：补偿删除本次新上传的对象，旧文件未受影响
            log.error("候选人更新失败，回滚新上传对象: id={}", id, dbEx);
            if (newPhotoRef != null) {
                obsStorageCleaner.deleteQuietly(newPhotoRef.key());
            }
            if (newResumeRef != null) {
                obsStorageCleaner.deleteQuietly(newResumeRef.key());
            }
            throw new BizException(BizErrorCode.QINIU_ERROR, "候选人更新失败");
        }
    }

    /**
     * 删除候选人（软删除）：打标 deleted_at，照片/简历对象与行的物理清理由定时任务在保留期后执行。
     */
    @Transactional
    public void delete(Long id) {
        // findById 已过滤软删数据，重复删除视为不存在
        candidateRepository.findById(id)
                .orElseThrow(() -> new BizException(BizErrorCode.NOT_FOUND));
        candidateRepository.softDeleteById(id, LocalDateTime.now());
    }

    /**
     * 根据 ID 查询候选人。
     */
    public Candidate getById(Long id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new BizException(BizErrorCode.NOT_FOUND));
    }

    /**
     * 分页查询候选人，支持按姓名、身份证号模糊搜索和性别过滤。
     * 过滤与分页在数据库执行，避免全表加载。
     */
    public PageResult<Candidate> list(String name, String idCard, String gender, int page, int pageSize) {
        CandidateQuery query = CandidateQuery.builder()
                .name(name)
                .idCard(idCard)
                .gender(gender)
                .page(PageQuery.of(page, pageSize))
                .build();
        return candidateRepository.findPage(query);
    }

    /**
     * 校验身份证号格式与校验码。
     */
    private void validateIdCard(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            throw new BizException(BizErrorCode.KEY_INVALID, "身份证号不能为空");
        }
        if (!IdCardUtil.isValid(idCard)) {
            throw new BizException(BizErrorCode.KEY_INVALID, "身份证号格式不合法");
        }
    }

    /**
     * 按上传策略校验后上传文件到对象存储，返回存储 key 与原始文件名；文件为空返回 null。
     */
    private FileRef uploadFileIfPresent(MultipartFile file, UploadProfile profile) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        uploadPolicy.validate(file, profile);
        String originalName = FileNameUtil.basename(file.getOriginalFilename());
        String key = fileKeyGenerator.generate(originalName);
        try {
            obsStorage.upload(ObsUploadRequest.builder()
                    .data(file.getBytes())
                    .key(key)
                    .contentType(file.getContentType())
                    .originalName(originalName)
                    .size(file.getSize())
                    .build());
            return new FileRef(key, originalName);
        } catch (IOException e) {
            throw new BizException(BizErrorCode.QINIU_ERROR, e);
        }
    }

    public static String formatDateTime(java.time.LocalDateTime dt) {
        return dt != null ? dt.format(DT_FORMATTER) : null;
    }

    /**
     * 根据存储 key 生成 OBS 访问 URL。
     * key 为空返回 null。
     */
    public String getAccessUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return obsStorage.getUrl(key);
    }

    /**
     * 文件上传结果：存储 key + 原始文件名。
     * 供 create/update 用例同时获取存储标识与回显所需的原始文件名。
     */
    private record FileRef(String key, String name) {
    }
}
