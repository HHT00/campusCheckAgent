/**
 * 校园考勤助手 - 后端 API 接口定义
 * 所有与后端的通信统一入口
 */
import request from '../utils/request';

const api = {
  // ==================== 认证 ====================
  login(studentId, password) {
    return request.post('/auth/login', { studentId, password });
  },

  // ==================== 首页仪表盘 ====================
  getDashboardOverview(userId) {
    return request.get('/dashboard/overview', { userId });
  },

  getBanners() {
    return request.get('/dashboard/banners');
  },

  // ==================== 用户 ====================
  getUserProfile() {
    return request.get('/user/profile');
  },

  updateUserProfile(data) {
    return request.put('/user/profile', data);
  },

  // ==================== 公告 ====================
  getAnnouncements(params) {
    return request.get('/announcements', params);
  },

  getTopAnnouncement() {
    return request.get('/announcements/top');
  },

  getAnnouncementDetail(id) {
    return request.get(`/announcements/${id}`);
  },

  // ==================== 请假 ====================
  getLeaves(params) {
    return request.get('/leaves', params);
  },

  getLeaveStats(userId) {
    return request.get('/leaves/stats', { userId });
  },

  applyLeave(data) {
    return request.post('/leaves', data);
  },

  getLeaveDetail(id) {
    return request.get(`/leaves/${id}`);
  },

  // ==================== 查寝打卡 ====================
  dormCheckin(data) {
    return request.post('/dorm/checkin', data);
  },

  getDormHistory(params) {
    return request.get('/dorm/history', params);
  },

  getDormTodayStatus() {
    return request.get('/dorm/today-status');
  },

  // ==================== 上课打卡 ====================
  getTodayCourse() {
    return request.get('/course/today');
  },

  getCourseSchedule() {
    return request.get('/course/schedule');
  },

  classCheckin(data) {
    return request.post('/course/checkin', data);
  },

  getClassHistory(params) {
    return request.get('/course/history', params);
  },

  // ==================== 实习打卡 ====================
  getMyInternship() {
    return request.get('/intern/my');
  },

  getInternStats() {
    return request.get('/intern/stats');
  },

  internCheckin(data) {
    return request.post('/intern/checkin', data);
  },

  getInternHistory(params) {
    return request.get('/intern/history', params);
  },

  uploadInternPhoto(checkinId, imageUrl) {
    return request.post('/intern/photo/upload', { checkinId, imageUrl });
  },

  deleteInternPhoto(id) {
    return request.del(`/intern/photo/${id}`);
  },

  // ==================== 人脸管理 ====================
  /** 人脸录入（注册/更新覆盖） */
  registerFace(imageBase64) {
    return request.post('/face/register', {
      image: imageBase64,
      format: 'jpg'
    });
  },

  /** 获取人脸录入状态 */
  getFaceStatus() {
    return request.get('/face/status');
  },

  /** 人脸验证（打卡前调用，与已录入人脸比对） */
  verifyFace(imageBase64) {
    return request.post('/face/verify', {
      image: imageBase64,
      format: 'jpg'
    });
  },

  // ==================== 教师端 ====================

  // 请假审批
  getTeacherPendingLeaves(params) {
    return request.get('/leaves/teacher/pending', params);
  },

  getTeacherAllLeaves(params) {
    return request.get('/leaves/teacher/all', params);
  },

  getTeacherLeaveStats() {
    return request.get('/leaves/teacher/stats');
  },

  approveLeave(id, data) {
    return request.post(`/leaves/teacher/approve/${id}`, data);
  },

  // 公告管理
  createAnnouncement(data) {
    return request.post('/announcements', data);
  },

  updateAnnouncement(id, data) {
    return request.put(`/announcements/${id}`, data);
  },

  deleteAnnouncement(id) {
    return request.del(`/announcements/${id}`);
  },

  getTeacherAllAnnouncements(params) {
    return request.get('/announcements/teacher/list-all', params);
  },

  // 动态码管理
  generateDynamicCode(data) {
    return request.post('/teacher/dynamic-code/generate', data);
  },

  getActiveDynamicCode(courseId) {
    return request.get('/teacher/dynamic-code/active', { courseId });
  },

  // 学生管理
  getStudents() {
    return request.get('/teacher/students');
  },

  getStudentsPage(params) {
    return request.get('/teacher/students/page', params);
  },

  getStudentCheckinDetail(studentId) {
    return request.get(`/teacher/students/checkin/${studentId}`);
  },

  getTodayCheckinSummary() {
    return request.get('/teacher/checkin/today-summary');
  },

  // ==================== 意见反馈 ====================
  submitFeedback(data) {
    return request.post('/feedback/submit', data);
  },

  getMyFeedback(params) {
    return request.get('/feedback/list', params);
  },

  getTeacherFeedback(params) {
    return request.get('/feedback/teacher', params);
  },

  // ==================== 数据分析 ====================
  getCourseRanking() {
    return request.get('/analysis/course/ranking');
  },
  getDormRanking() {
    return request.get('/analysis/dorm/building-ranking');
  },
  getLeaveAnomaly() {
    return request.get('/analysis/anomaly/leave-pattern');
  },
  getAlerts(params) {
    return request.get('/analysis/alerts', params);
  },

  // ==================== 智能问答 ====================
  searchKnowledge(data) {
    return request.post('/knowledge/search', data);
  },

  getHotQuestions() {
    return request.get('/knowledge/hot');
  },

  getUnansweredQuestions(params) {
    return request.get('/knowledge/unanswered', params);
  }
};

export default api;
