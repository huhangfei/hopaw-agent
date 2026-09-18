/**
 * 全局通知 WebSocket 订阅（/ws/notice）
 * 用法：connectNoticeWebSocket(onNotice)
 * onNotice(data)：收到通知消息时回调，data 结构 { type, subtype, content: {...}, timestamp }
 */
var noticeWs = null;
var noticeHandlers = [];

function connectNoticeWebSocket(onNotice) {
    if (typeof onNotice === 'function') {
        noticeHandlers.push(onNotice);
    }
    // 已有连接时不重复建立
    if (noticeWs && (noticeWs.readyState === WebSocket.OPEN || noticeWs.readyState === WebSocket.CONNECTING)) {
        return;
    }
    var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    try {
        noticeWs = new WebSocket(protocol + '//' + window.location.host + '/ws/notice');
    } catch (e) {
        console.error('创建通知 WebSocket 失败:', e);
        return;
    }
    noticeWs.onmessage = function (event) {
        var data;
        try { data = JSON.parse(event.data); } catch (e) { return; }
        noticeHandlers.forEach(function (handler) {
            try { handler(data); } catch (e) { console.error('通知处理异常:', e); }
        });
    };
    noticeWs.onclose = function () {
        // 断线重连
        setTimeout(function () { noticeWs = null; connectNoticeWebSocket(); }, 5000);
    };
}
