/**
 * 教师端 - 学生打卡详情（含风险预警与趋势）
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    student: null,
    stats: null,
    risk: {},
    weekTrend: [],
    todayClasses: [],
    todayDorm: null,
    recentClassRecords: [],
    recentDormRecords: [],
    loading: true
  },

  async onLoad(options) {
    const studentId = options.studentId;
    if (studentId) {
      await this.loadStudentDetail(studentId);
      this.computeRisk();
      this.computeTrend();
    }
  },

  async loadStudentDetail(studentId) {
    this.setData({ loading: true });
    try {
      const detail = await api.getStudentCheckinDetail(studentId);
      if (detail) {
        this.setData({
          student: detail.student,
          stats: detail.stats,
          todayClasses: detail.todayClasses || [],
          todayDorm: detail.todayDorm,
          recentClassRecords: detail.recentClassRecords || [],
          recentDormRecords: detail.recentDormRecords || []
        });
      }
    } catch (err) {
      console.warn('[学生详情] 加载失败:', err);
      // mock 数据兜底
      const student = {
        id: parseInt(studentId),
        name: '张同学',
        studentId: '2024' + String(studentId).slice(-4),
        college: '计算机科学与技术学院',
        major: '软件工程',
        grade: '2024级'
      };
      this.setData({
        student,
        stats: { classTotal: 42, dormTotal: 38, streakDays: 5, totalPoints: 268 },
        todayClasses: [
          { courseId: 1, courseName: '软件工程', checkinTime: '08:05', status: 'present' },
          { courseId: 2, courseName: '数据结构', checkinTime: null, status: 'absent' }
        ],
        todayDorm: { checkinTime: '22:30', locationAddr: '5号宿舍楼', status: 'normal' },
        recentClassRecords: [
          { date: '07-28', checkinTime: '2026-07-28 08:03', status: 'present' },
          { date: '07-27', checkinTime: '2026-07-27 08:10', status: 'present' },
          { date: '07-26', checkinTime: '2026-07-26 08:02', status: 'late' },
          { date: '07-25', checkinTime: null, status: 'absent' },
          { date: '07-24', checkinTime: '2026-07-24 08:01', status: 'present' }
        ],
        recentDormRecords: [
          { date: '07-28', checkinTime: '2026-07-28 22:30', status: 'normal' },
          { date: '07-27', checkinTime: '2026-07-27 23:15', status: 'late' },
          { date: '07-26', checkinTime: '2026-07-26 22:45', status: 'normal' },
          { date: '07-25', checkinTime: '2026-07-25 23:30', status: 'late' },
          { date: '07-24', checkinTime: '2026-07-24 22:10', status: 'normal' }
        ]
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  /** 计算学生风险等级 */
  computeRisk() {
    const records = this.data.recentClassRecords || [];
    const dormRecords = this.data.recentDormRecords || [];

    // 连续旷课检测
    let absentStreak = 0;
    for (const r of records) {
      if (r.status === 'absent') {
        absentStreak++;
      } else {
        break;
      }
    }

    // 晚归频次检测
    const lateCount = dormRecords.filter(r => r.status === 'late').length;
    const warning = [];

    if (absentStreak >= 2) {
      warning.push(`连续旷课 ${absentStreak} 天`);
    }
    if (lateCount >= 3) {
      warning.push(`近 ${dormRecords.length} 天晚归 ${lateCount} 次`);
    }

    if (warning.length > 0) {
      this.setData({
        risk: {
          level: absentStreak >= 2 ? 'warn' : 'info',
          title: '⚠ ' + warning[0],
          desc: warning.join('；')
        }
      });
    }
  },

  /** 生成近 7 天出勤趋势 */
  computeTrend() {
    const records = this.data.recentClassRecords || [];
    const days = ['07-24', '07-25', '07-26', '07-27', '07-28', '07-29', '07-30'];
    const statusMap = { present: '正常', late: '迟到', absent: '旷课' };

    const trend = days.map((label, i) => {
      const match = records.find(r => r.date === label || (r.checkinTime && r.checkinTime.startsWith('2026-' + label)));
      const status = match ? match.status : 'none';
      const cls = status === 'present' ? 'success' : (status === 'late' ? 'warning' : (status === 'absent' ? 'danger' : 'default'));
      return {
        label,
        status: cls,
        text: statusMap[status] || '无数据',
        width: status === 'present' ? 100 : (status === 'late' ? 60 : (status === 'absent' ? 30 : 0))
      };
    });

    this.setData({ weekTrend: trend });
  },

  /** 导出学生记录为 CSV */
  exportRecords() {
    const s = this.data.student;
    const header = `学生: ${s ? s.name : '-'}(${s ? s.studentId : '-'})\n`;
    const header2 = '类型,日期,时间,状态\n';

    const classRows = (this.data.recentClassRecords || []).map(r =>
      `上课,${r.date},${r.checkinTime ? r.checkinTime.substring(11, 19) : '-'},${r.status === 'present' ? '正常' : (r.status === 'late' ? '迟到' : '旷课')}`
    );

    const dormRows = (this.data.recentDormRecords || []).map(r =>
      `查寝,${r.date},${r.checkinTime ? r.checkinTime.substring(11, 19) : '-'},${r.status === 'normal' ? '正常' : '晚归'}`
    );

    wx.setClipboardData({
      data: header + header2 + [...classRows, ...dormRows].join('\n'),
      success: () => {
        app.showToast('已复制到剪贴板', 'success');
      }
    });
  }
});
