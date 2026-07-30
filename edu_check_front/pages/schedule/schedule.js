/**
 * 我的课程表 - 独立页面
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    weekSchedule: [],
    weekDays: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
    today: '',
    loading: true
  },

  onLoad() {
    // 获取今天的星期
    const weekMap = ['日', '一', '二', '三', '四', '五', '六'];
    const today = '星期' + weekMap[new Date().getDay()];
    this.setData({ today });
    this.loadSchedule();
  },

  onPullDownRefresh() {
    this.loadSchedule(() => wx.stopPullDownRefresh());
  },

  async loadSchedule(callback) {
    this.setData({ loading: true });
    try {
      const schedule = await api.getCourseSchedule();
      this.setData({ weekSchedule: schedule || [], loading: false });
    } catch (err) {
      console.warn('[课程表] 加载失败:', err);
      app.showToast('加载课程表失败');
      this.setData({ loading: false });
    } finally {
      callback && callback();
    }
  },

  /** 某天是否有课 */
  dayHasCourse(day) {
    return this.data.weekSchedule.some(c => c.weekDay === day);
  }
});
