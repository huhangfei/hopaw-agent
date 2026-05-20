// tool-config.js
// 工具配置页面脚本
(function() {
    // 页面初始化
    document.addEventListener('DOMContentLoaded', function() {
        // 表单提交处理
        var form = document.querySelector('.config-form');
        if (form) {
            form.addEventListener('submit', function(e) {
                // 如果需要额外的表单处理逻辑，可以在这里添加
            });
        }
    });
})();
