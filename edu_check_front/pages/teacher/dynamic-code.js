/**
 * 教师端 - 动态码管理
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    courses: [],
    selectedCourseId: null,
    selectedCourseName: '',
    selectedCourseIndex: 0,
    dynamicCode: '',
    sessionId: '',
    duration: 60,
    durationIndex: 1,
    durationOptions: [
      { value: 30, label: '30秒' },
      { value: 60, label: '60秒' },
      { value: 90, label: '90秒' },
      { value: 120, label: '120秒' }
    ],
    countdown: 0,
    timer: null,
    expired: false,
    generating: false,
    activeCode: null
  },

  async onLoad() {
    await this.loadCourses();
  },

  onUnload() {
    if (this.data.timer) {
      clearInterval(this.data.timer);
    }
  },

  async loadCourses() {
    try {
      const schedule = await api.getCourseSchedule();
      this.setData({ courses: schedule || [] });
      // 加载完成后自动选中当前时间段的课程
      this.autoSelectCurrentCourse(schedule || []);
    } catch (err) {
      console.warn('[动态码] 加载课程失败:', err);
      app.showToast('加载课程列表失败');
    }
  },

  /** 自动选中当前时间段（或即将开始）的课程 */
  autoSelectCurrentCourse(courses) {
    if (!courses || courses.length === 0) return;

    const weekMap = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
    const todayWeekDay = weekMap[new Date().getDay()];

    // 筛选今日课程
    const todayCourses = courses.filter(c => c.weekDay === todayWeekDay);
    if (todayCourses.length === 0) return;

    const now = new Date();
    const nowMinutes = now.getHours() * 60 + now.getMinutes();

    let bestIdx = -1;
    let bestCourse = null;

    for (let i = 0; i < todayCourses.length; i++) {
      const c = todayCourses[i];
      const startParts = c.startTime.split(':');
      const endParts = c.endTime.split(':');
      const startMin = parseInt(startParts[0]) * 60 + parseInt(startParts[1]);
      const endMin = parseInt(endParts[0]) * 60 + parseInt(endParts[1]);

      // 正在上课中：优先选中
      if (nowMinutes >= startMin && nowMinutes <= endMin) {
        bestIdx = i;
        bestCourse = c;
        break;
      }

      // 即将开始的课程（未来最近的一节）
      if (startMin > nowMinutes) {
        if (!bestCourse || (startMin < (parseInt(bestCourse.startTime.split(':')[0]) * 60 + parseInt(bestCourse.startTime.split(':')[1])))) {
          bestIdx = i;
          bestCourse = c;
        }
      }
    }

    // 如果找到合适的课程但无进行中或未来的，选最后一节
    if (!bestCourse && todayCourses.length > 0) {
      bestIdx = todayCourses.length - 1;
      bestCourse = todayCourses[bestIdx];
    }

    if (bestCourse) {
      // 在 courses 全量数组中查找该课程的索引
      const globalIdx = courses.findIndex(c => c.id === bestCourse.id);
      if (globalIdx >= 0) {
        this.setData({
          selectedCourseId: bestCourse.id,
          selectedCourseName: bestCourse.name,
          selectedCourseIndex: globalIdx
        });
        this.loadActiveCode(bestCourse.id);
      }
    }
  },

  onCourseChange(e) {
    const idx = parseInt(e.detail.value);
    const course = this.data.courses[idx];
    if (course) {
      this.setData({
        selectedCourseId: course.id,
        selectedCourseName: course.name,
        selectedCourseIndex: idx
      });
      this.loadActiveCode(course.id);
    }
  },

  async loadActiveCode(courseId) {
    try {
      const active = await api.getActiveDynamicCode(courseId);
      if (active && active.dynamicCode) {
        this.setData({
          activeCode: active,
          dynamicCode: active.dynamicCode,
          sessionId: active.sessionId
        });
      } else {
        this.setData({
          activeCode: null,
          dynamicCode: '',
          sessionId: ''
        });
      }
    } catch (err) {
      console.warn('[动态码] 查询失败:', err);
    }
  },

  setDuration(e) {
    const idx = parseInt(e.detail.value);
    const option = this.data.durationOptions[idx];
    if (option) {
      this.setData({ duration: option.value, durationIndex: idx });
    }
  },

  async generateCode() {
    if (!this.data.selectedCourseId) {
      app.showToast('请先选择课程');
      return;
    }

    this.setData({ generating: true });
    try {
      const result = await api.generateDynamicCode({
        courseId: this.data.selectedCourseId,
        duration: this.data.duration
      });

      this.setData({
        dynamicCode: result.dynamicCode,
        sessionId: result.sessionId,
        expired: false,
        countdown: result.duration || this.data.duration,
        activeCode: null
      });

      // 开始倒计时
      this.startCountdown();
    } catch (err) {
      app.showToast(err.message || '生成失败');
    } finally {
      this.setData({ generating: false });
    }
  },

  startCountdown() {
    if (this.data.timer) {
      clearInterval(this.data.timer);
    }

    const timer = setInterval(() => {
      let countdown = this.data.countdown - 1;
      if (countdown <= 0) {
        countdown = 0;
        clearInterval(timer);
        this.setData({
          expired: true,
          countdown: 0,
          timer: null
        });
      } else {
        this.setData({ countdown });
      }
    }, 1000);

    this.setData({ timer });
  },

  refreshCode() {
    this.generateCode();
  }
});
