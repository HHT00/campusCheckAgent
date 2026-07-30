/**
 * 校园考勤助手 - 真实定位工具
 * 微信小程序内置定位 + 地址解析，无需第三方API key
 */

/**
 * 获取真实GPS坐标
 * @returns {Promise<{latitude: number, longitude: number}>}
 */
function getRealLocation() {
  return new Promise((resolve, reject) => {
    wx.getLocation({
      type: 'gcj02',
      isHighAccuracy: true,
      success: (res) => {
        resolve({
          latitude: res.latitude,
          longitude: res.longitude
        });
      },
      fail: (err) => {
        reject(new Error('定位失败，请检查手机定位权限：' + (err.errMsg || '')));
      }
    });
  });
}

/**
 * 获取位置 + 地址文字（打开地图让用户确认位置，自动返回地址）
 * @returns {Promise<{latitude: number, longitude: number, address: string}>}
 */
function getLocationWithAddress() {
  return new Promise((resolve, reject) => {
    wx.chooseLocation({
      success: (res) => {
        resolve({
          latitude: res.latitude,
          longitude: res.longitude,
          address: res.address || ''
        });
      },
      fail: (err) => {
        // 用户取消选择，尝试只获取坐标
        if (err.errMsg && err.errMsg.includes('cancel')) {
          getRealLocation()
            .then(coords => resolve({ ...coords, address: '' }))
            .catch(reject);
        } else {
          reject(new Error('获取位置失败：' + (err.errMsg || '')));
        }
      }
    });
  });
}

module.exports = {
  getRealLocation,
  getLocationWithAddress
};
