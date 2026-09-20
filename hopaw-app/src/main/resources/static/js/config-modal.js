// 通用 iframe 弹框：用于在任意页面以弹窗形式打开无模板独立页面（如插件配置 / 工具配置）。
//
// 用法：openConfigModal('/plugins/config/xxx', '插件配置') 打开；closeConfigModal() 关闭。
// 被嵌入的独立页面在保存成功后通过 postMessage({type:'hopaw-config-saved'}) 通知父窗口关闭。
(function () {
    var OVERLAY_ID = 'configModalOverlay';

    function ensureOverlay() {
        var overlay = document.getElementById(OVERLAY_ID);
        if (overlay) return overlay;

        overlay = document.createElement('div');
        overlay.id = OVERLAY_ID;
        overlay.className = 'modal-overlay config-modal-overlay';
        overlay.innerHTML =
            '<div class="modal modal-wide config-modal">' +
                '<div class="modal-header">' +
                    '<h3 id="configModalTitle">配置</h3>' +
                    '<button type="button" class="modal-close" aria-label="关闭">&times;</button>' +
                '</div>' +
                '<div class="modal-body config-modal-body">' +
                    '<iframe id="configModalFrame" class="config-modal-frame" frameborder="0"></iframe>' +
                '</div>' +
            '</div>';
        document.body.appendChild(overlay);

        overlay.querySelector('.modal-close').addEventListener('click', function () {
            closeConfigModal();
        });
        // 点击遮罩关闭（mousedown 判断按下位置，避免拖选误关）
        overlay.addEventListener('mousedown', function (e) {
            if (e.target === overlay) closeConfigModal();
        });

        return overlay;
    }

    window.openConfigModal = function (url, title) {
        var overlay = ensureOverlay();
        document.getElementById('configModalTitle').textContent = title || '配置';
        document.getElementById('configModalFrame').src = url;
        overlay.classList.add('active');
    };

    window.closeConfigModal = function () {
        var overlay = document.getElementById(OVERLAY_ID);
        if (!overlay) return;
        overlay.classList.remove('active');
        // 延迟清空 iframe，避免关闭瞬间仍在加载 / 残留内容
        var frame = document.getElementById('configModalFrame');
        if (frame) frame.src = 'about:blank';
    };

    // 被嵌入的独立页面保存成功后，通知本页关闭弹框并提示
    window.addEventListener('message', function (e) {
        var data = e.data;
        if (data && data.type === 'hopaw-config-saved') {
            closeConfigModal();
            if (typeof showToast === 'function') {
                showToast(data.message || '配置保存成功', 'success');
            }
        }
    });
})();
