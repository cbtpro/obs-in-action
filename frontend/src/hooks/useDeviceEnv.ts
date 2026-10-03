/**
 * 设备环境检测：模块级 IIFE 一次性采集 UA/平台，避免重复调用 navigator。
 */
const deviceEnv = (() => {
  const ua = typeof navigator !== 'undefined' ? navigator.userAgent : '';
  return {
    isIOS: /iPhone|iPad|iPod/i.test(ua),
    isSafari: /Safari/i.test(ua) && !/Chrome|CriOS|FxiOS|Android/i.test(ua),
    isMobile: /Android|iPhone|iPad|iPod/i.test(ua),
  };
})();

export interface DeviceEnv {
  isIOS: boolean;
  isSafari: boolean;
  isMobile: boolean;
}

export function useDeviceEnv(): DeviceEnv {
  return deviceEnv;
}
