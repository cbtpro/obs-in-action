import { useEffect, useState } from 'react';
import { Form, Input, Upload, Button, InputNumber, Image, message, theme } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import type { UploadProps, UploadFile } from 'antd';
import { Candidate, CandidateFormValues } from '../types/candidate';
import { isValidIdCard, parseGender, parseAge } from '../utils/idCard';
import {
  ACCEPT_PHOTO,
  ACCEPT_RESUME,
  MAX_PHOTO_SIZE,
  MAX_RESUME_SIZE,
  checkFileSize,
  isAllowedPhoto,
  isAllowedResume,
} from '../utils/upload';

const { TextArea } = Input;

export type CandidateFormMode = 'add' | 'view' | 'edit';

interface Props {
  /** 表单模式 */
  mode: CandidateFormMode;
  /** 编辑/查看时传入候选人数据 */
  candidate?: Candidate | null;
  /** 提交回调，返回表单数据；view 模式下不会触发 */
  onSubmit?: (values: CandidateFormValues) => Promise<void>;
  /** 取消回调 */
  onCancel: () => void;
  /** 外部提交状态（由 wrapper 控制 loading） */
  submitting?: boolean;
}

/**
 * 候选人表单组件（纯 UI + 校验）。
 * - add:  空白表单，可编辑，提交触发 onSubmit
 * - edit: 预填充，可编辑，提交触发 onSubmit
 * - view: 预填充，全字段只读，无提交按钮
 *
 * 不含 API 调用与 Modal 包装，由 AddCandidate / EditCandidate / ViewCandidate 编排。
 */
export default function CandidateForm({
  mode,
  candidate,
  onSubmit,
  onCancel,
  submitting = false,
}: Props) {
  const isView = mode === 'view';
  const { token } = theme.useToken();
  const [form] = Form.useForm();
  const [gender, setGender] = useState<string | null>(candidate?.gender ?? null);
  const [age, setAge] = useState<number | null>(candidate?.age ?? null);
  const [idCardError, setIdCardError] = useState<string>('');
  const [photoFile, setPhotoFile] = useState<File | undefined>();
  const [resumeFile, setResumeFile] = useState<File | undefined>();
  const [photoPreview, setPhotoPreview] = useState<string | null>(candidate?.photoUrl ?? null);

  useEffect(() => {
    if (candidate) {
      form.setFieldsValue({
        name: candidate.name,
        idCard: candidate.idCard,
        bio: candidate.bio,
      });
      setPhotoPreview(candidate.photoUrl ?? null);
    }
  }, [candidate, form]);

  // blob: 预览 URL 的生命周期管理：切换或卸载时释放，远程签名 URL 不受影响
  useEffect(() => {
    return () => {
      if (photoPreview?.startsWith('blob:')) {
        URL.revokeObjectURL(photoPreview);
      }
    };
  }, [photoPreview]);

  const handleIdCardChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setIdCardError('');
    if (value.length === 18) {
      if (isValidIdCard(value)) {
        setGender(parseGender(value));
        setAge(parseAge(value));
      } else {
        setGender(null);
        setAge(null);
        setIdCardError('身份证号格式不合法');
      }
    } else {
      setGender(null);
      setAge(null);
    }
  };

  const photoProps: UploadProps = {
    defaultFileList: candidate?.photoUrl
      ? [
          {
            uid: 'photo-existing',
            name: candidate.photoName || '照片',
            status: 'done',
            url: candidate.photoUrl,
          } as UploadFile,
        ]
      : [],
    beforeUpload: (file) => {
      if (isView) return false;
      // 类型白名单（精确 MIME 匹配，拒绝 SVG 等伪装图片）+ 大小预校验
      if (!isAllowedPhoto(file)) {
        message.error('照片仅支持 jpg/png/gif/webp/bmp 格式');
        return Upload.LIST_IGNORE;
      }
      const sizeError = checkFileSize(file, MAX_PHOTO_SIZE, '照片');
      if (sizeError) {
        message.error(sizeError);
        return Upload.LIST_IGNORE;
      }
      setPhotoFile(file);
      setPhotoPreview(URL.createObjectURL(file));
      return false;
    },
    onRemove: () => {
      setPhotoFile(undefined);
      setPhotoPreview(null);
    },
    maxCount: 1,
    accept: ACCEPT_PHOTO,
    disabled: isView,
  };

  const resumeProps: UploadProps = {
    defaultFileList: candidate?.resumeUrl
      ? [
          {
            uid: 'resume-existing',
            name: candidate.resumeName || '简历',
            status: 'done',
            url: candidate.resumeUrl,
          } as UploadFile,
        ]
      : [],
    beforeUpload: (file) => {
      if (isView) return false;
      // 扩展名白名单（Office 文档 MIME 各浏览器不一致，按扩展名判定）+ 大小预校验
      if (!isAllowedResume(file)) {
        message.error('简历仅支持 pdf/word/excel/txt/zip 格式');
        return Upload.LIST_IGNORE;
      }
      const sizeError = checkFileSize(file, MAX_RESUME_SIZE, '简历');
      if (sizeError) {
        message.error(sizeError);
        return Upload.LIST_IGNORE;
      }
      setResumeFile(file);
      return false;
    },
    onRemove: () => setResumeFile(undefined),
    maxCount: 1,
    accept: ACCEPT_RESUME,
    disabled: isView,
  };

  const handleSubmit = async (values: {
    name: string;
    idCard: string;
    bio?: string;
  }) => {
    if (!isValidIdCard(values.idCard)) {
      setIdCardError('身份证号格式不合法');
      return;
    }
    const formData: CandidateFormValues = {
      name: values.name,
      idCard: values.idCard,
      bio: values.bio,
      photo: photoFile,
      resume: resumeFile,
    };
    await onSubmit?.(formData);
  };

  const submitLabel = mode === 'edit' ? '保存修改' : '提交';

  return (
    <Form
      form={form}
      layout="vertical"
      onFinish={handleSubmit}
      style={{ maxWidth: 640, margin: '0 auto' }}
      disabled={isView}
    >
      <Form.Item
        label="姓名"
        name="name"
        rules={[{ required: true, message: '请输入姓名' }]}
      >
        <Input placeholder="请输入姓名" maxLength={50} />
      </Form.Item>

      <Form.Item
        label="身份证号"
        name="idCard"
        rules={[{ required: true, message: '请输入身份证号' }]}
        validateStatus={idCardError ? 'error' : ''}
        help={idCardError || undefined}
      >
        <Input
          placeholder="请输入 18 位身份证号"
          maxLength={18}
          onChange={handleIdCardChange}
        />
      </Form.Item>

      <div style={{ display: 'flex', gap: 16, marginBottom: 24 }}>
        <Form.Item label="性别" style={{ flex: 1, marginBottom: 0 }}>
          <Input value={gender ?? ''} placeholder="自动填充" readOnly />
        </Form.Item>
        <Form.Item label="年龄" style={{ flex: 1, marginBottom: 0 }}>
          <InputNumber
            value={age ?? undefined}
            placeholder="自动填充"
            readOnly
            style={{ width: '100%' }}
          />
        </Form.Item>
      </div>

      <Form.Item label="照片">
        <Upload {...photoProps} listType="picture">
          {!isView && <Button icon={<UploadOutlined />}>上传照片</Button>}
        </Upload>
        {photoPreview && (
          <div style={{ marginTop: 8 }}>
            <Image
              src={photoPreview}
              alt="照片预览"
              width={120}
              height={120}
              style={{ objectFit: 'cover', border: `1px solid ${token.colorBorderSecondary}` }}
            />
          </div>
        )}
      </Form.Item>

      <Form.Item label="个人简介" name="bio">
        <TextArea
          rows={4}
          placeholder="请输入个人简介"
          maxLength={1000}
          showCount
        />
      </Form.Item>

      <Form.Item label="简历附件">
        <Upload {...resumeProps}>
          {!isView && <Button icon={<UploadOutlined />}>上传简历</Button>}
        </Upload>
      </Form.Item>

      {!isView && (
        <Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Button type="primary" htmlType="submit" loading={submitting}>
              {submitLabel}
            </Button>
            <Button onClick={onCancel}>取消</Button>
          </div>
        </Form.Item>
      )}
    </Form>
  );
}
