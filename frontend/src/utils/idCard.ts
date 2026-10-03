/**
 * 身份证号解析工具（前端，用于实时自动填充性别、年龄）
 * 18 位身份证：前 6 位地区码 + 8 位出生日期 + 3 位顺序码 + 1 位校验码
 * 第 17 位奇数为男，偶数为女
 */

const ID_CARD_REGEX = /^\d{17}[\dXx]$/;

/** 校验码权重 */
const WEIGHTS = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
const CHECK_CODES = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'];

export function isValidIdCard(idCard: string): boolean {
  if (!ID_CARD_REGEX.test(idCard)) return false;
  // 校验出生日期
  const birth = idCard.substring(6, 14);
  const year = parseInt(birth.substring(0, 4), 10);
  const month = parseInt(birth.substring(4, 6), 10);
  const day = parseInt(birth.substring(6, 8), 10);
  const date = new Date(year, month - 1, day);
  if (
    date.getFullYear() !== year ||
    date.getMonth() !== month - 1 ||
    date.getDate() !== day
  ) {
    return false;
  }
  // 校验校验码
  let sum = 0;
  for (let i = 0; i < 17; i++) {
    sum += parseInt(idCard[i], 10) * WEIGHTS[i];
  }
  return CHECK_CODES[sum % 11] === idCard[17].toUpperCase();
}

/** 从身份证号提取性别 */
export function parseGender(idCard: string): string | null {
  if (!isValidIdCard(idCard)) return null;
  const genderDigit = parseInt(idCard[16], 10);
  return genderDigit % 2 === 1 ? '男' : '女';
}

/** 从身份证号计算年龄 */
export function parseAge(idCard: string): number | null {
  if (!isValidIdCard(idCard)) return null;
  const birth = idCard.substring(6, 14);
  const year = parseInt(birth.substring(0, 4), 10);
  const month = parseInt(birth.substring(4, 6), 10);
  const day = parseInt(birth.substring(6, 8), 10);
  const birthDate = new Date(year, month - 1, day);
  const now = new Date();
  let age = now.getFullYear() - birthDate.getFullYear();
  if (
    now.getMonth() < birthDate.getMonth() ||
    (now.getMonth() === birthDate.getMonth() && now.getDate() < birthDate.getDate())
  ) {
    age--;
  }
  return age;
}
